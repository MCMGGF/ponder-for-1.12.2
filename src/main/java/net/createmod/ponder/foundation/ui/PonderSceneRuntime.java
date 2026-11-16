package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSchematic;
import net.createmod.ponder.foundation.Vec3iAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

final class PonderSceneRuntime {

    static final int SECTION_FADE_TICKS = 15;
    static final int CAMERA_ROTATE_TICKS = 18;
    private static final float SECTION_FADE_DISTANCE = 0.5F;
    private static final Map<PonderScene, PonderScenePreview.PreviewBounds> BOUNDS_CACHE =
        Collections.synchronizedMap(new WeakHashMap<PonderScene, PonderScenePreview.PreviewBounds>());

    private PonderSceneRuntime() {
    }

    static PonderScenePreview.PreviewBounds computeBounds(PonderScene scene) {
        scene.validateSectionOwnershipBudget();
        PonderScenePreview.PreviewBounds cached = BOUNDS_CACHE.get(scene);
        if (cached != null) {
            return cached;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        PonderSchematic schematic = scene.getSchematic();
        Set<BlockPos> renderablePositions = new LinkedHashSet<BlockPos>();
        for (Map.Entry<BlockPos, IBlockState> entry : schematic.getBlocks().entrySet()) {
            if (!isRenderable(entry.getValue())) {
                continue;
            }
            BlockPos pos = entry.getKey();
            renderablePositions.add(pos);
            minX = Math.min(minX, Vec3iAccessor.x(pos));
            minY = Math.min(minY, Vec3iAccessor.y(pos));
            minZ = Math.min(minZ, Vec3iAccessor.z(pos));
            maxX = Math.max(maxX, Vec3iAccessor.x(pos));
            maxY = Math.max(maxY, Vec3iAccessor.y(pos));
            maxZ = Math.max(maxZ, Vec3iAccessor.z(pos));
        }

        // Selection boxes include empty cells. Only geometry that exists at
        // some point in the timeline contributes to the fixed camera bounds.
        for (PonderScene.WorldEvent event : scene.getWorldEvents()) {
            if ((event.getType() == PonderScene.WorldEventType.SET_BLOCK
                || event.getType() == PonderScene.WorldEventType.SET_BLOCKS
                || event.getType() == PonderScene.WorldEventType.REPLACE_BLOCKS)
                && isRenderable(parseState(event.getStateDescription()))) {
                renderablePositions.addAll(event.getPositions());
            }
        }

        Map<BlockPos, List<PonderScene.WorldEvent>> motionsByPosition =
            new LinkedHashMap<BlockPos, List<PonderScene.WorldEvent>>();
        for (PonderScene.WorldEvent event : scene.getWorldEvents()) {
            boolean motion = event.getSectionOwnership() == null && PreviewMotionBounds.isMotion(event);
            for (BlockPos pos : event.getPositions()) {
                if (!renderablePositions.contains(pos)) {
                    continue;
                }
                minX = Math.min(minX, Vec3iAccessor.x(pos));
                minY = Math.min(minY, Vec3iAccessor.y(pos));
                minZ = Math.min(minZ, Vec3iAccessor.z(pos));
                maxX = Math.max(maxX, Vec3iAccessor.x(pos));
                maxY = Math.max(maxY, Vec3iAccessor.y(pos));
                maxZ = Math.max(maxZ, Vec3iAccessor.z(pos));

                if (motion) {
                    List<PonderScene.WorldEvent> motions = motionsByPosition.get(pos);
                    if (motions == null) {
                        motions = new ArrayList<PonderScene.WorldEvent>();
                        motionsByPosition.put(pos, motions);
                    }
                    motions.add(event);
                }
            }
        }

        PreviewMotionBounds.Bounds actors = PreviewActorBounds.compute(scene.getActorEvents());
        if (actors != null) {
            minX = Math.min(minX, minimumCell(actors.minX));
            minY = Math.min(minY, minimumCell(actors.minY));
            minZ = Math.min(minZ, minimumCell(actors.minZ));
            maxX = Math.max(maxX, maximumCell(actors.maxX));
            maxY = Math.max(maxY, maximumCell(actors.maxY));
            maxZ = Math.max(maxZ, maximumCell(actors.maxZ));
        }

        Map<BlockPos, EnumSet<EnumFacing>> fadesByPosition = sectionFadeDirections(scene, renderablePositions);
        Map<MotionGroup, PreviewMotionBounds.Bounds> groups =
            new LinkedHashMap<MotionGroup, PreviewMotionBounds.Bounds>();
        for (BlockPos position : renderablePositions) {
            List<PonderScene.WorldEvent> motions = motionsByPosition.get(position);
            EnumSet<EnumFacing> fades = fadesByPosition.get(position);
            if (motions == null && fades == null) {
                continue;
            }
            MotionGroup group = new MotionGroup(motions, fades);
            PreviewMotionBounds.Bounds geometry = groups.get(group);
            if (geometry == null) {
                groups.put(group, PreviewMotionBounds.Bounds.block(position));
            } else {
                geometry.include(PreviewMotionBounds.Bounds.block(position));
            }
        }
        // A shared event chain transforms the whole group affinely. Sweeping
        // its geometry once avoids repeating the timeline work per block.
        for (Map.Entry<MotionGroup, PreviewMotionBounds.Bounds> entry : groups.entrySet()) {
            MotionGroup group = entry.getKey();
            PreviewMotionBounds.Bounds swept = PreviewMotionBounds.compute(entry.getValue(), group.events);
            for (EnumFacing direction : EnumFacing.values()) {
                if ((group.fadeDirections & (1 << direction.ordinal())) != 0) {
                    swept.includeFade(direction, SECTION_FADE_DISTANCE);
                }
            }
            minX = Math.min(minX, minimumCell(swept.minX));
            minY = Math.min(minY, minimumCell(swept.minY));
            minZ = Math.min(minZ, minimumCell(swept.minZ));
            maxX = Math.max(maxX, maximumCell(swept.maxX));
            maxY = Math.max(maxY, maximumCell(swept.maxY));
            maxZ = Math.max(maxZ, maximumCell(swept.maxZ));
        }
        for (PreviewMotionBounds.Bounds swept : PreviewSectionOwnership.bounds(scene, renderablePositions)) {
            minX = Math.min(minX, minimumCell(swept.minX)); minY = Math.min(minY, minimumCell(swept.minY)); minZ = Math.min(minZ, minimumCell(swept.minZ));
            maxX = Math.max(maxX, maximumCell(swept.maxX)); maxY = Math.max(maxY, maximumCell(swept.maxY)); maxZ = Math.max(maxZ, maximumCell(swept.maxZ));
        }

        if (minX == Integer.MAX_VALUE) {
            minX = scene.getBasePlateOffsetX();
            minY = 0;
            minZ = scene.getBasePlateOffsetZ();
            maxX = minX + Math.max(1, scene.getBasePlateSize()) - 1;
            maxY = 0;
            maxZ = minZ + Math.max(1, scene.getBasePlateSize()) - 1;
        }
        BlockPos authoredBounds = scene.getFramingBounds();
        if (authoredBounds != null) {
            minX = Math.min(minX, 0);
            minY = Math.min(minY, 0);
            minZ = Math.min(minZ, 0);
            maxX = Math.max(maxX, authoredBounds.getX());
            maxY = Math.max(maxY, authoredBounds.getY());
            maxZ = Math.max(maxZ, authoredBounds.getZ());
        }
        // Controller selection bounds are not rendered geometry. An explicitly
        // requested static mesh extends the camera envelope in the screen only.
        PonderScenePreview.PreviewBounds computed =
            new PonderScenePreview.PreviewBounds(minX, maxX, minZ, maxZ, maxY, minY);
        BOUNDS_CACHE.put(scene, computed);
        return computed;
    }

    private static Map<BlockPos, EnumSet<EnumFacing>> sectionFadeDirections(PonderScene scene,
        Set<BlockPos> renderablePositions) {
        Map<BlockPos, EnumSet<EnumFacing>> result = new LinkedHashMap<BlockPos, EnumSet<EnumFacing>>();
        for (PonderScene.WorldEvent event : scene.getWorldEvents()) {
            if (event.getDirection() == null || (event.getType() != PonderScene.WorldEventType.SHOW_SECTION
                && event.getType() != PonderScene.WorldEventType.HIDE_SECTION)) {
                continue;
            }
            EnumFacing direction = event.getType() == PonderScene.WorldEventType.SHOW_SECTION
                ? event.getDirection().getOpposite() : event.getDirection();
            for (BlockPos pos : event.getPositions()) {
                if (!renderablePositions.contains(pos)) {
                    continue;
                }
                EnumSet<EnumFacing> directions = result.get(pos);
                if (directions == null) {
                    directions = EnumSet.noneOf(EnumFacing.class);
                    result.put(pos, directions);
                }
                directions.add(direction);
            }
        }
        return result;
    }

    private static final class MotionGroup {
        private final List<PonderScene.WorldEvent> events;
        private final int fadeDirections;

        private MotionGroup(List<PonderScene.WorldEvent> events, Set<EnumFacing> fades) {
            this.events = events == null ? Collections.<PonderScene.WorldEvent>emptyList() : events;
            int directions = 0;
            if (fades != null) {
                for (EnumFacing direction : fades) {
                    directions |= 1 << direction.ordinal();
                }
            }
            fadeDirections = directions;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof MotionGroup)) {
                return false;
            }
            MotionGroup group = (MotionGroup) other;
            return fadeDirections == group.fadeDirections && events.equals(group.events);
        }

        @Override
        public int hashCode() {
            return 31 * events.hashCode() + fadeDirections;
        }
    }

    private static int minimumCell(double edge) {
        return checkedCell(Math.floor(edge));
    }

    private static int maximumCell(double edge) {
        return checkedCell(Math.ceil(edge) - 1.0D);
    }

    private static int checkedCell(double cell) {
        if (!Double.isFinite(cell) || cell < Integer.MIN_VALUE || cell > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Ponder section motion exceeds integer preview bounds");
        }
        return (int) cell;
    }

    static RuntimeState buildState(PonderScene scene, int tick) {
        return buildState(scene, (float) tick);
    }

    static RuntimeState buildState(PonderScene scene, float tick) {
        scene.validateSectionOwnershipBudget();
        Map<BlockPos, RuntimeBlockState> blocksByPosition = new LinkedHashMap<BlockPos, RuntimeBlockState>();
        PonderSchematic schematic = scene.getSchematic();

        for (Map.Entry<BlockPos, IBlockState> entry : schematic.getBlocks().entrySet()) {
            RuntimeBlockState state = new RuntimeBlockState(entry.getKey());
            state.originalState = entry.getValue();
            state.currentState = entry.getValue();
            state.stateDescription = describeState(entry.getValue());
            net.minecraft.nbt.NBTTagCompound tileData = schematic.getBlockEntityNbt(entry.getKey());
            state.originalTileNbt = tileData == null ? null : tileData.toString();
            state.tileNbt = state.originalTileNbt;
            blocksByPosition.put(state.pos, state);
        }

        PreviewSectionOwnership ownership = new PreviewSectionOwnership(blocksByPosition);
        for (PonderScene.WorldEvent event : scene.getWorldEvents()) {
            if (event.getTick() > tick) {
                break;
            }

            ownership.before(event.getTick());
            if (event.getSectionOwnership() != null) { ownership.apply(event); continue; }

            float elapsed = tick - event.getTick();

            for (BlockPos pos : event.getPositions()) {
                BlockPos key = new BlockPos(pos);
                RuntimeBlockState state = blocksByPosition.get(key);
                if (state == null) {
                    state = new RuntimeBlockState(key);
                    blocksByPosition.put(key, state);
                }

                switch (event.getType()) {
                    case SHOW_SECTION:
                        if (state.destroyed) {
                            break;
                        }
                        animateShowSection(state, event.getDirection(), elapsed, event.getDuration());
                        state.breakingProgress = 0;
                        break;
                    case RESTORE_BLOCKS:
                        state.destroyed = false;
                        restoreOriginal(state);
                        state.breakingProgress = 0;
                        resetFadeAnimation(state);
                        break;
                    case HIDE_SECTION:
                        animateHideSection(state, event.getDirection(), elapsed, event.getDuration());
                        state.breakingProgress = 0;
                        break;
                    case DESTROY_BLOCK:
                        state.destroyed = true;
                        state.tileNbt = null;
                        state.visible = false;
                        state.fade = 0.0F;
                        resetFadeOffset(state);
                        state.breakingProgress = 0;
                        state.stateDescription = "destroyed";
                        break;
                    case SET_BLOCKS:
                    case SET_BLOCK:
                    case REPLACE_BLOCKS:
                        IBlockState parsedState = parseState(event.getStateDescription());
                        if (event.getType() != PonderScene.WorldEventType.REPLACE_BLOCKS
                            || state.currentState == null || parsedState == null
                            || state.currentState.getBlock() != parsedState.getBlock() || !isRenderable(parsedState)) {
                            state.tileNbt = null;
                        }
                        state.destroyed = false;
                        state.currentState = parsedState;
                        state.visible = isRenderable(parsedState);
                        state.fade = state.visible ? 1.0F : 0.0F;
                        resetFadeOffset(state);
                        state.breakingProgress = 0;
                        state.stateDescription = describeState(parsedState);
                        break;
                    case APPLY_NBT:
                        state.tileNbt = event.getStateDescription();
                        break;
                    case BREAK_PROGRESS:
                        if (state.currentState == null) {
                            state.currentState = state.originalState;
                        }
                        state.visible = state.visible || isRenderable(state.currentState);
                        state.breakingProgress = Math.min(10, state.breakingProgress + 1);
                        if (state.stateDescription == null) {
                            state.stateDescription = describeState(state.currentState);
                        }
                        if (state.visible && state.fade == 0.0F) {
                            state.fade = 1.0F;
                        }
                        break;
                    case MOVE_SECTION:
                        applySectionMove(state, event, elapsed);
                        break;
                    case ROTATE_SECTION:
                        applySectionRotation(state, event, elapsed);
                        break;
                    default:
                        break;
                }
            }
            ownership.observeLegacyMotion(event);
        }

        ownership.finish(tick);
        Map<Long, PonderScenePreview.PreviewCellState> cellsByColumn =
            new LinkedHashMap<Long, PonderScenePreview.PreviewCellState>();
        int visibleBlocks = 0;
        for (RuntimeBlockState state : blocksByPosition.values()) {
            updateDisplayMetrics(state);
            if (!state.visible || !isRenderable(state.currentState)) {
                continue;
            }

            visibleBlocks++;
            int renderX = MathHelper.floor(state.renderCenterX);
            int renderY = MathHelper.floor(state.renderCenterY);
            int renderZ = MathHelper.floor(state.renderCenterZ);
            long key = PonderScenePreview.columnKey(renderX, renderZ);
            PonderScenePreview.PreviewCellState cell = cellsByColumn.get(key);
            if (cell == null) {
                cell = new PonderScenePreview.PreviewCellState();
                cell.x = renderX;
                cell.z = renderZ;
                cellsByColumn.put(key, cell);
            }

            cell.visibleCount++;
            cell.breakingProgress = Math.max(cell.breakingProgress, state.breakingProgress);
            if (renderY >= cell.topY) {
                cell.topY = renderY;
                cell.stateDescription = state.stateDescription;
            }
        }

        return new RuntimeState(blocksByPosition, cellsByColumn, visibleBlocks, cellsByColumn.size());
    }

    static float getCameraYaw(PonderScene scene, float tick) {
        return getCameraRotation(scene, tick, false);
    }

    static float getCameraPitch(PonderScene scene, float tick) {
        return getCameraRotation(scene, tick, true);
    }

    private static float getCameraRotation(PonderScene scene, float tick, boolean pitch) {
        if (!Float.isFinite(tick)) return 0F;
        List<PonderScene.CameraEvent> events = scene.getCameraEvents();
        double target = 0D, start = 0D, activeTarget = 0D;
        PonderScene.CameraEvent active = null;
        int additiveStart = 0, end = 0;
        for (; end < events.size(); end++) {
            PonderScene.CameraEvent event = events.get(end);
            if (event.getTick() > tick) break;
            if (pitch && !event.affectsPitch()) continue;
            target += pitch ? event.getPitchDegrees() : event.getYawDegrees();
            if (event.isTargetBased()) {
                start = cameraAngle(start, activeTarget, active, event.getTick())
                    + additiveCameraAngle(events, additiveStart, end, event.getTick(), pitch);
                active = event;
                activeTarget = target;
                additiveStart = end + 1;
            }
        }
        return (float) (cameraAngle(start, activeTarget, active, tick)
            + additiveCameraAngle(events, additiveStart, end, tick, pitch));
    }

    private static double cameraAngle(double start, double target, PonderScene.CameraEvent active, float tick) {
        if (active == null) return 0D;
        float progress = cameraProgress(active, tick);
        return progress == 1F ? target : start + (target - start) * progress;
    }

    private static double additiveCameraAngle(List<PonderScene.CameraEvent> events, int start, int end,
        float tick, boolean pitch) {
        double angle = 0D;
        for (int i = start; i < end; i++) {
            PonderScene.CameraEvent event = events.get(i);
            if (pitch && !event.affectsPitch()) continue;
            angle += (pitch ? event.getPitchDegrees() : event.getYawDegrees()) * cameraProgress(event, tick);
        }
        return angle;
    }

    private static float cameraProgress(PonderScene.CameraEvent event, float tick) {
        if (event.getDuration() == 0) return 1F;
        float elapsed = tick - event.getTick();
        // New authored targets begin at the current view, not one animation tick ahead.
        float progress = event.isTargetBased() ? MathHelper.clamp(elapsed / event.getDuration(), 0F, 1F)
            : animationProgress(elapsed, event.getDuration());
        return easeOutQuad(progress);
    }

    static Vec3d transformPointAtTick(PonderScene scene, Vec3d point, float tick) {
        if (scene == null || point == null) {
            return point;
        }
        if (scene.hasSectionOwnership()) {
            RuntimeState state = buildState(scene, tick);
            BlockPos floor = new BlockPos(point);
            RuntimeBlockState owner = state.blocksByPosition.get(floor);
            if (owner == null || !isRenderable(owner.currentState) || owner.destroyed) {
                BlockPos[] candidates = {floor.down(), floor.west(), floor.north()};
                double[] coordinates = {point.y, point.x, point.z};
                owner = null;
                for (int i = 0; i < candidates.length; i++) {
                    RuntimeBlockState candidate = state.blocksByPosition.get(candidates[i]);
                    if (Math.abs(coordinates[i] - Math.rint(coordinates[i])) < 1E-7D && candidate != null
                        && !candidate.destroyed && isRenderable(candidate.currentState)) { owner = candidate; break; }
                }
            }
            return owner == null ? point : transformPoint(owner, point);
        }

        Vec3d transformed = point;
        for (PonderScene.WorldEvent event : scene.getWorldEvents()) {
            if (event.getTick() > tick
                || (event.getType() != PonderScene.WorldEventType.MOVE_SECTION
                    && event.getType() != PonderScene.WorldEventType.ROTATE_SECTION)
                || !eventAffectsPoint(event, point)) {
                continue;
            }

            float progress = transformProgress(tick - event.getTick(), event.getDuration());
            if (event.getType() == PonderScene.WorldEventType.MOVE_SECTION && event.getOffset() != null) {
                transformed = transformed.add(event.getOffset().scale(progress));
            } else if (event.getType() == PonderScene.WorldEventType.ROTATE_SECTION
                && event.getRotation() != null && event.getPivot() != null) {
                transformed = rotateAround(transformed, event.getPivot(), event.getRotation().scale(progress));
            }
        }
        return transformed;
    }

    static Vec3d overlayPointAtTick(PonderScene scene, PonderScene.OverlayEvent event, float tick) {
        return event == null ? null : event.isScenePoint() ? event.getPointAt() : transformPointAtTick(scene,event.getPointAt(),tick);
    }

    private static boolean eventAffectsPoint(PonderScene.WorldEvent event, Vec3d point) {
        if (point == null || event.getPositions().isEmpty()) {
            return false;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (BlockPos pos : event.getPositions()) {
            minX = Math.min(minX, Vec3iAccessor.x(pos));
            minY = Math.min(minY, Vec3iAccessor.y(pos));
            minZ = Math.min(minZ, Vec3iAccessor.z(pos));
            maxX = Math.max(maxX, Vec3iAccessor.x(pos) + 1.0D);
            maxY = Math.max(maxY, Vec3iAccessor.y(pos) + 1.0D);
            maxZ = Math.max(maxZ, Vec3iAccessor.z(pos) + 1.0D);
        }
        // Include only a small edge tolerance. A full-block expansion can make
        // adjacent moving sections claim the same overlay anchor.
        final double edgeTolerance = 0.05D;
        return point.x >= minX - edgeTolerance && point.x <= maxX + edgeTolerance
            && point.y >= minY - edgeTolerance && point.y <= maxY + edgeTolerance
            && point.z >= minZ - edgeTolerance && point.z <= maxZ + edgeTolerance;
    }

    private static void revealOriginal(RuntimeBlockState state) {
        if (!isRenderable(state.originalState)) {
            return;
        }
        state.currentState = state.originalState;
        state.visible = true;
        state.stateDescription = describeState(state.currentState);
    }

    private static void restoreOriginal(RuntimeBlockState state) {
        state.tileNbt = isRenderable(state.originalState) ? state.originalTileNbt : null;
        if (!isRenderable(state.originalState)) {
            state.currentState = null;
            state.visible = false;
            state.fade = 0.0F;
            resetFadeOffset(state);
            state.stateDescription = "restored";
            return;
        }
        state.currentState = state.originalState;
        state.visible = true;
        state.stateDescription = describeState(state.currentState);
    }

    static void animateShowSection(RuntimeBlockState state, EnumFacing direction, float elapsed, int duration) {
        if (!isRenderable(state.currentState)) {
            state.visible = false;
            state.fade = 0.0F;
            resetFadeOffset(state);
            return;
        }

        state.stateDescription = describeState(state.currentState);

        if (direction == null) {
            state.visible = true;
            state.fade = 1.0F;
            resetFadeOffset(state);
            return;
        }

        float progress = animationProgress(elapsed, duration > 0 ? duration : SECTION_FADE_TICKS);
        float fade = easeOutQuad(progress);
        state.visible = fade > 0.0F;
        state.fade = fade;
        applyDirectionalFadeOffset(state, direction, -1.0F, 1.0F - fade);
    }

    static void animateHideSection(RuntimeBlockState state, EnumFacing direction, float elapsed, int duration) {
        if (!isRenderable(state.currentState)) {
            state.visible = false;
            state.fade = 0.0F;
            resetFadeOffset(state);
            return;
        }

        float progress = animationProgress(elapsed, duration > 0 ? duration : SECTION_FADE_TICKS);
        float fade = 1.0F - progress * progress;
        state.visible = fade > 0.0F;
        state.fade = fade;
        applyDirectionalFadeOffset(state, direction, 1.0F, 1.0F - fade);

        if (!state.visible) {
            state.breakingProgress = 0;
        }
    }

    static void applySectionMove(RuntimeBlockState state, PonderScene.WorldEvent event, float elapsed) {
        Vec3d offset = event.getOffset();
        if (offset == null) {
            return;
        }

        float progress = transformProgress(elapsed, event.getDuration());
        if (progress <= 0.0F) {
            return;
        }

        Vec3d applied = offset.scale(progress);
        if (isNearZero(applied)) {
            return;
        }
        state.transforms.add(TransformStep.move(applied));
    }

    static void applySectionRotation(RuntimeBlockState state, PonderScene.WorldEvent event, float elapsed) {
        Vec3d rotation = event.getRotation();
        Vec3d pivot = event.getPivot();
        if (rotation == null || pivot == null) {
            return;
        }

        float progress = transformProgress(elapsed, event.getDuration());
        if (progress <= 0.0F) {
            return;
        }

        Vec3d applied = rotation.scale(progress);
        if (isNearZero(applied)) {
            return;
        }
        state.transforms.add(TransformStep.rotate(pivot, applied));
    }

    private static float animationProgress(float elapsed, int totalTicks) {
        if (totalTicks <= 0) {
            return 1.0F;
        }
        return MathHelper.clamp((elapsed + 1.0F) / totalTicks, 0.0F, 1.0F);
    }

    private static float easeOutQuad(float progress) {
        float inverse = 1.0F - progress;
        return 1.0F - inverse * inverse;
    }

    static float transformProgress(float elapsed, int duration) {
        if (duration <= 0) {
            return 1.0F;
        }
        return easeOutQuad(animationProgress(elapsed, duration));
    }

    private static void applyDirectionalFadeOffset(RuntimeBlockState state, EnumFacing direction, float directionScale,
        float amount) {
        if (direction == null || amount <= 0.0F) {
            resetFadeOffset(state);
            return;
        }

        float offset = SECTION_FADE_DISTANCE * amount;
        state.fadeOffsetX = direction.getXOffset() * offset * directionScale;
        state.fadeOffsetY = direction.getYOffset() * offset * directionScale;
        state.fadeOffsetZ = direction.getZOffset() * offset * directionScale;
    }

    private static void resetFadeAnimation(RuntimeBlockState state) {
        state.fade = state.visible ? 1.0F : 0.0F;
        resetFadeOffset(state);
    }

    static void resetFadeOffset(RuntimeBlockState state) {
        state.fadeOffsetX = 0.0F;
        state.fadeOffsetY = 0.0F;
        state.fadeOffsetZ = 0.0F;
    }

    private static boolean isRenderable(IBlockState state) {
        return state != null && state.getBlock() != Blocks.AIR;
    }

    private static IBlockState parseState(String stateDescription) {
        if (stateDescription == null || stateDescription.isEmpty() || "null".equals(stateDescription)) {
            return null;
        }

        try {
            String raw = stateDescription.trim();
            int meta = -1;
            int metaSeparator = raw.indexOf('#');
            if (metaSeparator >= 0) {
                String metaRaw = raw.substring(metaSeparator + 1);
                raw = raw.substring(0, metaSeparator);
                try {
                    meta = Integer.parseInt(metaRaw);
                } catch (NumberFormatException ignored) {
                    meta = -1;
                }
            }

            Block block = Block.REGISTRY.getObject(new ResourceLocation(raw));
            if (block == null) {
                return null;
            }

            if (meta < 0) {
                return block.getDefaultState();
            }

            try {
                return block.getStateFromMeta(meta);
            } catch (RuntimeException ignored) {
                return block.getDefaultState();
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static String describeState(IBlockState state) {
        if (state == null) {
            return "visible";
        }
        ResourceLocation blockId = Block.REGISTRY.getNameForObject(state.getBlock());
        if (blockId == null) {
            return state.toString();
        }

        int meta;
        try {
            meta = state.getBlock().getMetaFromState(state);
        } catch (RuntimeException ignored) {
            meta = -1;
        }
        return meta >= 0 ? blockId + "#" + meta : blockId.toString();
    }

    private static void updateDisplayMetrics(RuntimeBlockState state) {
        Vec3d transformed = applyTransformsToPoint(state, new Vec3d(state.pos).add(0.5D, 0.5D, 0.5D));
        state.renderCenterX = transformed.x;
        state.renderCenterY = transformed.y;
        state.renderCenterZ = transformed.z;
    }

    static Vec3d transformPoint(RuntimeBlockState state, Vec3d point) {
        if (state == null || point == null) {
            return point;
        }
        Vec3d transformed = point;
        for (TransformStep transform : state.transforms) {
            transformed = applyTransform(transformed, transform);
        }
        return transformed.add(state.fadeOffsetX, state.fadeOffsetY, state.fadeOffsetZ);
    }

    private static Vec3d applyTransformsToPoint(RuntimeBlockState state, Vec3d point) {
        return transformPoint(state, point);
    }

    private static Vec3d applyTransform(Vec3d point, TransformStep transform) {
        Vec3d transformed = point;
        if (transform.offset != null) {
            transformed = transformed.add(transform.offset);
        }
        if (transform.rotation != null && transform.pivot != null) {
            transformed = rotateAround(transformed, transform.pivot, transform.rotation);
        }
        return transformed;
    }

    private static Vec3d rotateAround(Vec3d point, Vec3d pivot, Vec3d rotation) {
        Vec3d translated = point.subtract(pivot);
        translated = rotateX(translated, rotation.x);
        translated = rotateY(translated, rotation.y);
        translated = rotateZ(translated, rotation.z);
        return translated.add(pivot);
    }

    private static Vec3d rotateX(Vec3d vec, double degrees) {
        double radians = Math.toRadians(degrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        double y = vec.y * cosine - vec.z * sine;
        double z = vec.y * sine + vec.z * cosine;
        return new Vec3d(vec.x, y, z);
    }

    private static Vec3d rotateY(Vec3d vec, double degrees) {
        double radians = Math.toRadians(degrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        double x = vec.x * cosine + vec.z * sine;
        double z = vec.z * cosine - vec.x * sine;
        return new Vec3d(x, vec.y, z);
    }

    private static Vec3d rotateZ(Vec3d vec, double degrees) {
        double radians = Math.toRadians(degrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        double x = vec.x * cosine - vec.y * sine;
        double y = vec.x * sine + vec.y * cosine;
        return new Vec3d(x, y, vec.z);
    }

    private static boolean isNearZero(Vec3d vec) {
        return vec == null
            || (Math.abs(vec.x) < 1.0E-4D && Math.abs(vec.y) < 1.0E-4D && Math.abs(vec.z) < 1.0E-4D);
    }

    static void applyRenderTransforms(RuntimeBlockState state) {
        if (state == null) {
            return;
        }

        if (state.fadeOffsetX != 0.0F || state.fadeOffsetY != 0.0F || state.fadeOffsetZ != 0.0F) {
            net.minecraft.client.renderer.GlStateManager.translate(state.fadeOffsetX, state.fadeOffsetY,
                state.fadeOffsetZ);
        }

        // OpenGL post-multiplies transforms, so emit recorded steps in reverse
        // order to match applyTransformsToPoint()'s instruction order.
        for (int index = state.transforms.size() - 1; index >= 0; index--) {
            TransformStep transform = state.transforms.get(index);
            if (transform.offset != null && !isNearZero(transform.offset)) {
                net.minecraft.client.renderer.GlStateManager.translate(transform.offset.x, transform.offset.y,
                    transform.offset.z);
            }
            if (transform.rotation != null && transform.pivot != null && !isNearZero(transform.rotation)) {
                net.minecraft.client.renderer.GlStateManager.translate(transform.pivot.x, transform.pivot.y,
                    transform.pivot.z);
                // The CPU projection applies X, then Y, then Z. OpenGL calls
                // must reverse the per-axis order as well as the event order.
                if (Math.abs(transform.rotation.z) >= 1.0E-4D) {
                    net.minecraft.client.renderer.GlStateManager.rotate((float) transform.rotation.z, 0.0F, 0.0F, 1.0F);
                }
                if (Math.abs(transform.rotation.y) >= 1.0E-4D) {
                    net.minecraft.client.renderer.GlStateManager.rotate((float) transform.rotation.y, 0.0F, 1.0F, 0.0F);
                }
                if (Math.abs(transform.rotation.x) >= 1.0E-4D) {
                    net.minecraft.client.renderer.GlStateManager.rotate((float) transform.rotation.x, 1.0F, 0.0F, 0.0F);
                }
                net.minecraft.client.renderer.GlStateManager.translate(-transform.pivot.x, -transform.pivot.y,
                    -transform.pivot.z);
            }
        }
    }

    static final class TransformStep {
        final Vec3d offset;
        final Vec3d pivot;
        final Vec3d rotation;

        private TransformStep(Vec3d offset, Vec3d pivot, Vec3d rotation) {
            this.offset = offset;
            this.pivot = pivot;
            this.rotation = rotation;
        }

        static TransformStep move(Vec3d offset) {
            return new TransformStep(offset, null, null);
        }

        static TransformStep rotate(Vec3d pivot, Vec3d rotation) {
            return new TransformStep(null, pivot, rotation);
        }
    }

    static final class RuntimeBlockState {
        final BlockPos pos;
        IBlockState originalState;
        IBlockState currentState;
        boolean visible;
        boolean destroyed;
        float fade;
        float fadeOffsetX;
        float fadeOffsetY;
        float fadeOffsetZ;
        int breakingProgress;
        String stateDescription = "visible";
        String originalTileNbt;
        String tileNbt;
        String sectionId = "";
        final List<TransformStep> transforms = new ArrayList<TransformStep>();
        double renderCenterX;
        double renderCenterY;
        double renderCenterZ;

        RuntimeBlockState(BlockPos pos) {
            this.pos = pos;
            this.renderCenterX = Vec3iAccessor.x(pos) + 0.5D;
            this.renderCenterY = Vec3iAccessor.y(pos) + 0.5D;
            this.renderCenterZ = Vec3iAccessor.z(pos) + 0.5D;
        }
    }

    static final class RuntimeState {
        final Map<BlockPos, RuntimeBlockState> blocksByPosition;
        final Map<Long, PonderScenePreview.PreviewCellState> cellsByColumn;
        final int visibleBlocks;
        final int columnsWithBlocks;

        RuntimeState(Map<BlockPos, RuntimeBlockState> blocksByPosition,
            Map<Long, PonderScenePreview.PreviewCellState> cellsByColumn, int visibleBlocks, int columnsWithBlocks) {
            this.blocksByPosition = Collections.unmodifiableMap(blocksByPosition);
            this.cellsByColumn = Collections.unmodifiableMap(cellsByColumn);
            this.visibleBlocks = visibleBlocks;
            this.columnsWithBlocks = columnsWithBlocks;
        }
    }
}
