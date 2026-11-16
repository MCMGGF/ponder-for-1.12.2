package net.createmod.ponder.foundation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class PonderScene {

    public static final String TITLE_KEY = "header";

    public static final class RecordedOperation {
        private final int tick;
        private final String description;

        public RecordedOperation(int tick, String description) {
            this.tick = tick;
            this.description = description;
        }

        public int getTick() {
            return tick;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum WorldEventType {
        SHOW_SECTION,
        HIDE_SECTION,
        RESTORE_BLOCKS,
        SET_BLOCKS,
        SET_BLOCK,
        REPLACE_BLOCKS,
        APPLY_NBT,
        DESTROY_BLOCK,
        BREAK_PROGRESS,
        MOVE_SECTION,
        ROTATE_SECTION,
        SECTION_OWNERSHIP
    }

    public static final class WorldEvent {
        private final int tick;
        private final WorldEventType type;
        private final List<BlockPos> positions;
        private final String stateDescription;
        private final EnumFacing direction;
        private final int duration;
        private final Vec3d offset;
        private final Vec3d rotation;
        private final Vec3d pivot;
        private final SectionOwnershipEvent sectionOwnership;

        public WorldEvent(int tick, WorldEventType type, List<BlockPos> positions, String stateDescription,
            EnumFacing direction) {
            this(tick, type, positions, stateDescription, direction, 0, null, null, null);
        }

        public WorldEvent(int tick, WorldEventType type, List<BlockPos> positions, String stateDescription,
            EnumFacing direction, int duration, Vec3d offset, Vec3d rotation, Vec3d pivot) {
            this(tick, type, positions, stateDescription, direction, duration, offset, rotation, pivot, null);
        }

        public WorldEvent(int tick, WorldEventType type, List<BlockPos> positions, String stateDescription,
            EnumFacing direction, int duration, Vec3d offset, Vec3d rotation, Vec3d pivot,
            SectionOwnershipEvent sectionOwnership) {
            this.tick = tick;
            this.type = type;
            List<BlockPos> copied = new ArrayList<BlockPos>();
            for (BlockPos pos : positions) copied.add(pos.toImmutable());
            this.positions = Collections.unmodifiableList(copied);
            this.stateDescription = stateDescription;
            this.direction = direction;
            this.duration = Math.max(0, duration);
            this.offset = offset;
            this.rotation = rotation;
            this.pivot = pivot;
            this.sectionOwnership = sectionOwnership;
        }

        public int getTick() {
            return tick;
        }

        public WorldEventType getType() {
            return type;
        }

        public List<BlockPos> getPositions() {
            return positions;
        }

        public String getStateDescription() {
            return stateDescription;
        }

        public EnumFacing getDirection() {
            return direction;
        }

        public int getDuration() {
            return duration;
        }

        public Vec3d getOffset() {
            return offset;
        }

        public Vec3d getRotation() {
            return rotation;
        }

        public Vec3d getPivot() {
            return pivot;
        }

        public SectionOwnershipEvent getSectionOwnership() { return sectionOwnership; }
    }

    public static final class CameraEvent {
        private final int tick;
        private final float pitchDegrees;
        private final float yawDegrees;
        private final int duration;
        private final boolean targetBased;
        private final boolean affectsPitch;

        public CameraEvent(int tick, float yawDegrees) {
            this(tick, 0F, yawDegrees, 18, false, false);
        }

        public CameraEvent(int tick, float pitchDegrees, float yawDegrees, int duration) {
            this(tick, pitchDegrees, yawDegrees, duration, true, true);
        }

        private CameraEvent(int tick, float pitchDegrees, float yawDegrees, int duration, boolean targetBased,
            boolean affectsPitch) {
            if (tick < 0 || !Float.isFinite(pitchDegrees) || !Float.isFinite(yawDegrees)
                || Math.abs(pitchDegrees) > 1000000F || Math.abs(yawDegrees) > 1000000F
                || duration < 0 || duration > 18) throw new IllegalArgumentException("Invalid camera rotation event");
            this.tick = tick;
            this.pitchDegrees = pitchDegrees;
            this.yawDegrees = yawDegrees;
            this.duration = duration;
            this.targetBased = targetBased;
            this.affectsPitch = affectsPitch;
        }

        public int getTick() {
            return tick;
        }

        public float getYawDegrees() {
            return yawDegrees;
        }

        public float getPitchDegrees() { return pitchDegrees; }
        public int getDuration() { return duration; }
        public boolean isTargetBased() { return targetBased; }
        public boolean affectsPitch() { return affectsPitch; }
    }

    /** An immutable authored view target, evaluated from history rather than render callbacks. */
    public static final class CameraViewEvent {
        private final int tick;
        private final int duration;
        private final Vec3d center;
        private final float multiplier;
        private final int overlayCutoff;

        public CameraViewEvent(int tick, int duration, Vec3d center, float multiplier, int overlayCutoff) {
            if (tick < 0 || duration < 0 || duration > 72000 || !Float.isFinite(multiplier)
                || multiplier <= 0F || multiplier > 64F || overlayCutoff < 0)
                throw new IllegalArgumentException("Invalid camera view event");
            this.tick = tick;
            this.duration = duration;
            this.center = center;
            this.multiplier = multiplier;
            this.overlayCutoff = overlayCutoff;
        }

        public int getTick() { return tick; }
        public int getDuration() { return duration; }
        public Vec3d getCenter() { return center; }
        public float getMultiplier() { return multiplier; }
        public int getOverlayCutoff() { return overlayCutoff; }
    }

    public static final class PoiEvent {
        private final int tick;
        private final Vec3d location;

        public PoiEvent(int tick, Vec3d location) {
            this.tick = tick;
            this.location = location;
        }

        public int getTick() {
            return tick;
        }

        public Vec3d getLocation() {
            return location;
        }
    }

    public static final class ParticleEffectEvent {
        private final int tick;
        private final Vec3d location;
        private final float amountPerCycle;
        private final int cycles;
        private final String particleName;
        private final Vec3d motion;
        private final boolean withinBlockSpace;
        private final String distribution;
        private final Vec3d spread;

        public ParticleEffectEvent(int tick, Vec3d location, float amountPerCycle, int cycles, String particleName,
            Vec3d motion, boolean withinBlockSpace) {
            this(tick, location, amountPerCycle, cycles, particleName, motion, withinBlockSpace, null, Vec3d.ZERO);
        }

        public ParticleEffectEvent(int tick, Vec3d location, float amountPerCycle, int cycles, String particleName,
            Vec3d motion, boolean withinBlockSpace, String distribution, Vec3d spread) {
            this.tick = tick;
            this.location = location;
            this.amountPerCycle = amountPerCycle;
            this.cycles = cycles;
            this.particleName = particleName;
            this.motion = motion;
            this.withinBlockSpace = withinBlockSpace;
            this.distribution = distribution;
            this.spread = spread == null ? Vec3d.ZERO : spread;
        }

        public int getTick() {
            return tick;
        }

        public Vec3d getLocation() {
            return location;
        }

        public float getAmountPerCycle() {
            return amountPerCycle;
        }

        public int getCycles() {
            return cycles;
        }

        public String getParticleName() {
            return particleName;
        }

        public Vec3d getMotion() {
            return motion;
        }

        public boolean isWithinBlockSpace() {
            return withinBlockSpace;
        }

        public String getDistribution() { return distribution; }
        public Vec3d getSpread() { return spread; }
    }

    public enum ActorKind {
        BIRB,
        CART,
        ITEM,
        PLAYER
    }

    public enum ActorEventType {
        SPAWN,
        MOVE,
        ROTATE,
        HIDE,
        POSE,
        LOOK_AT,
        HELD_ITEM,
        SWING,
        SNEAK,
        NBT_PATCH
    }

    public static final class ActorEvent {
        private final int tick;
        private final int duration;
        private final int actorId;
        private final ActorKind actorKind;
        private final ActorEventType type;
        private final Vec3d location;
        private final Vec3d offset;
        private final Vec3d rotation;
        private final float angle;
        private final String poseName;
        private final String displayName;
        private final ItemStack itemStack;
        private final NBTTagCompound actorNbt;
        private final boolean walkAnimation;
        private final boolean entranceMotion;

        public ActorEvent(int tick, int duration, int actorId, ActorKind actorKind, ActorEventType type,
            Vec3d location, Vec3d offset, Vec3d rotation, float angle, String poseName, String displayName,
            ItemStack itemStack) {
            this(tick, duration, actorId, actorKind, type, location, offset, rotation, angle, poseName,
                displayName, itemStack, null, true);
        }

        public ActorEvent(int tick, int duration, int actorId, ActorKind actorKind, ActorEventType type,
            Vec3d location, Vec3d offset, Vec3d rotation, float angle, String poseName, String displayName,
            ItemStack itemStack, NBTTagCompound actorNbt, boolean walkAnimation) {
            this(tick, duration, actorId, actorKind, type, location, offset, rotation, angle, poseName,
                displayName, itemStack, actorNbt, walkAnimation, false);
        }

        public ActorEvent(int tick, int duration, int actorId, ActorKind actorKind, ActorEventType type,
            Vec3d location, Vec3d offset, Vec3d rotation, float angle, String poseName, String displayName,
            ItemStack itemStack, NBTTagCompound actorNbt, boolean walkAnimation, boolean entranceMotion) {
            if (entranceMotion && (type != ActorEventType.MOVE || walkAnimation || duration <= 0))
                throw new IllegalArgumentException("Entrance motion requires a positive-duration, non-walking MOVE");
            this.tick = tick;
            this.duration = duration;
            this.actorId = actorId;
            this.actorKind = actorKind;
            this.type = type;
            this.location = location;
            this.offset = offset;
            this.rotation = rotation;
            this.angle = angle;
            this.poseName = poseName;
            this.displayName = displayName;
            this.itemStack = itemStack == null ? ItemStack.EMPTY : itemStack.copy();
            this.actorNbt = actorNbt == null ? new NBTTagCompound() : actorNbt.copy();
            this.walkAnimation = walkAnimation;
            this.entranceMotion = entranceMotion;
        }

        public int getTick() {
            return tick;
        }

        public int getDuration() {
            return duration;
        }

        public int getActorId() {
            return actorId;
        }

        public ActorKind getActorKind() {
            return actorKind;
        }

        public ActorEventType getType() {
            return type;
        }

        public Vec3d getLocation() {
            return location;
        }

        public Vec3d getOffset() {
            return offset;
        }

        public Vec3d getRotation() {
            return rotation;
        }

        public float getAngle() {
            return angle;
        }

        public String getPoseName() {
            return poseName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public ItemStack getItemStack() {
            return itemStack == null ? ItemStack.EMPTY : itemStack.copy();
        }

        public NBTTagCompound getActorNbt() { return actorNbt.copy(); }

        public boolean hasWalkAnimation() { return walkAnimation; }
        public boolean isEntranceMotion() { return entranceMotion; }
    }

    /** A local playback cue, never a world/entity sound callback. */
    public static final class SoundEvent {
        private final int tick;
        private final ResourceLocation soundId;
        private final SoundCategory category;
        private final float volume, pitch;
        public SoundEvent(int tick, ResourceLocation soundId, SoundCategory category, float volume, float pitch) {
            if (tick < 0 || soundId == null || category == null || !Float.isFinite(volume) || volume < 0 || volume > 4
                || !Float.isFinite(pitch) || pitch < 0.01F || pitch > 4)
                throw new IllegalArgumentException("Invalid bounded preview sound cue");
            this.tick = tick; this.soundId = soundId; this.category = category; this.volume = volume; this.pitch = pitch;
        }
        public int getTick() { return tick; }
        public ResourceLocation getSoundId() { return soundId; }
        public SoundCategory getCategory() { return category; }
        public float getVolume() { return volume; }
        public float getPitch() { return pitch; }
    }

    public enum OverlayEventType {
        TEXT,
        SCENE_OUTLINE,
        SCENE_LINE,
        VALUE_BOX,
        CONTROLS,
        GUI_TEXTURE,
        GUI_HIGHLIGHT
    }

    public static final class OverlayEvent {
        private final int tick;
        private int recordedOrder = -1;
        private int duration;
        OverlayEventType type = OverlayEventType.TEXT;
        String text = "";
        PonderPalette palette = PonderPalette.WHITE;
        Vec3d pointAt;
        private boolean scenePoint;
        int independentY = -1;
        boolean placeNearTarget;
        boolean connectorVisible = true;
        String overlayId;
        String parentOverlayId;
        ResourceLocation textureLocation;
        ResourceLocation guiSnapshotId;
        private PonderGuiSnapshotRegistry.SnapshotProvider guiSnapshotProvider;
        int textureU;
        int textureV;
        int regionWidth;
        int regionHeight;
        int textureWidth = 256;
        int textureHeight = 256;
        int displayWidth;
        int displayHeight;
        int offsetX;
        int offsetY;
        boolean framed = true;
        boolean scaleToParent;
        boolean stretchTexture;
        int stretchBorder = 4;
        int captionX = Integer.MIN_VALUE;
        int captionY = Integer.MIN_VALUE;
        int captionOffsetX;
        int captionOffsetY;
        int guiX;
        int guiY;
        int guiWidth;
        int guiHeight;
        AxisAlignedBB sceneBounds;
        private List<BlockPos> sourcePositions = Collections.emptyList();
        private List<BlockPos> targetPositions = Collections.emptyList();
        private boolean outlineChasing;
        Pointing controlDirection;
        ItemStack controlItem = ItemStack.EMPTY;
        boolean controlLeftClick;
        boolean controlRightClick;
        boolean controlScroll;
        boolean controlSneaking;
        boolean controlCtrl;
        Vec3d lineStart;
        Vec3d lineEnd;
        boolean lineWide;
        Vec3d valueBoxCenter;
        Vec3d valueBoxExpand;

        OverlayEvent(int tick, int duration) {
            this.tick = tick;
            this.duration = duration;
        }

        public int getTick() {
            return tick;
        }

        public int getDuration() {
            return duration;
        }

        /** Authoring GUI timelines are finalized during compilation, before any viewer reads them. */
        public void setGuiDuration(int duration) {
            if (type != OverlayEventType.GUI_TEXTURE || duration < 0 || duration > 24000)
                throw new IllegalArgumentException("GUI duration must be 0..24000 ticks");
            this.duration = Math.max(1, duration);
        }

        public OverlayEventType getType() {
            return type;
        }

        public String getText() {
            return text;
        }

        public int getColor() {
            return palette.getColor();
        }

        public PonderPalette getPalette() {
            return palette;
        }

        public Vec3d getPointAt() {
            return pointAt;
        }

        public boolean isScenePoint() { return scenePoint; }

        public int getIndependentY() {
            return independentY;
        }

        public boolean isPlaceNearTarget() {
            return placeNearTarget;
        }

        public boolean isConnectorVisible() {
            return connectorVisible;
        }

        public String getOverlayId() {
            return overlayId;
        }

        public String getParentOverlayId() {
            return parentOverlayId;
        }

        public ResourceLocation getTextureLocation() {
            return textureLocation;
        }

        public ResourceLocation getGuiSnapshotId() {
            return guiSnapshotId;
        }

        /** A compiled GUI may own a lazy provider instead of depending on a bounded global ID cache. */
        public void setGuiSnapshotProvider(PonderGuiSnapshotRegistry.SnapshotProvider provider) {
            if (type != OverlayEventType.GUI_TEXTURE || provider == null)
                throw new IllegalArgumentException("A GUI texture event and provider are required");
            guiSnapshotProvider = provider;
        }

        public PonderGuiSnapshotRegistry.SnapshotProvider getGuiSnapshotProvider() {
            return guiSnapshotProvider;
        }

        public int getTextureU() {
            return textureU;
        }

        public int getTextureV() {
            return textureV;
        }

        public int getRegionWidth() {
            return regionWidth;
        }

        public int getRegionHeight() {
            return regionHeight;
        }

        public int getTextureWidth() {
            return textureWidth;
        }

        public int getTextureHeight() {
            return textureHeight;
        }

        public int getDisplayWidth() {
            return displayWidth;
        }

        public int getDisplayHeight() {
            return displayHeight;
        }

        public int getOffsetX() {
            return offsetX;
        }

        public int getOffsetY() {
            return offsetY;
        }

        public boolean isFramed() {
            return framed;
        }

        public boolean isScaleToParent() {
            return scaleToParent;
        }

        public boolean isStretchTexture() {
            return stretchTexture;
        }

        public int getStretchBorder() {
            return stretchBorder;
        }

        public int getCaptionX() {
            return captionX;
        }

        public int getCaptionY() {
            return captionY;
        }

        public int getCaptionOffsetX() {
            return captionOffsetX;
        }

        public int getCaptionOffsetY() {
            return captionOffsetY;
        }

        public int getGuiX() {
            return guiX;
        }

        public int getGuiY() {
            return guiY;
        }

        public int getGuiWidth() {
            return guiWidth;
        }

        public int getGuiHeight() {
            return guiHeight;
        }

        public AxisAlignedBB getSceneBounds() {
            return sceneBounds;
        }

        public List<BlockPos> getSourcePositions() {
            return sourcePositions;
        }

        public List<BlockPos> getTargetPositions() {
            return targetPositions;
        }

        public boolean isOutlineChasing() {
            return outlineChasing;
        }

        public Pointing getControlDirection() {
            return controlDirection;
        }

        public ItemStack getControlItem() {
            return controlItem == null ? ItemStack.EMPTY : controlItem;
        }

        public boolean isControlLeftClick() {
            return controlLeftClick;
        }

        public boolean isControlRightClick() {
            return controlRightClick;
        }

        public boolean isControlScroll() {
            return controlScroll;
        }

        public boolean isControlSneaking() {
            return controlSneaking;
        }

        public boolean isControlCtrl() {
            return controlCtrl;
        }

        public Vec3d getLineStart() {
            return lineStart;
        }

        public Vec3d getLineEnd() {
            return lineEnd;
        }

        public boolean isLineWide() {
            return lineWide;
        }

        public Vec3d getValueBoxCenter() {
            return valueBoxCenter;
        }

        public Vec3d getValueBoxExpand() {
            return valueBoxExpand;
        }

        public OverlayEvent text(String text) {
            this.text = text == null ? "" : text;
            return this;
        }

        public OverlayEvent palette(PonderPalette palette) {
            this.palette = palette == null ? PonderPalette.WHITE : palette;
            return this;
        }

        public OverlayEvent pointAt(Vec3d pointAt) {
            this.pointAt = pointAt;
            this.scenePoint = false;
            this.independentY = -1;
            targetPositions(pointAt == null ? Collections.<BlockPos>emptyList()
                : Collections.singletonList(new BlockPos(pointAt)));
            return this;
        }

        public OverlayEvent pointAtScene(Vec3d pointAt) {
            if (pointAt != null && (!Double.isFinite(pointAt.x) || !Double.isFinite(pointAt.y) || !Double.isFinite(pointAt.z)
                || Math.abs(pointAt.x) > 30000000 || Math.abs(pointAt.y) > 30000000 || Math.abs(pointAt.z) > 30000000))
                throw new IllegalArgumentException("A bounded finite scene point is required");
            this.pointAt = pointAt;
            this.scenePoint = pointAt != null;
            this.independentY = -1;
            this.targetPositions = Collections.emptyList();
            return this;
        }

        public OverlayEvent sourcePositions(Collection<BlockPos> positions) {
            this.sourcePositions = copyPositions(positions);
            return this;
        }

        public OverlayEvent targetPositions(Collection<BlockPos> positions) {
            this.targetPositions = copyPositions(positions);
            if (!this.targetPositions.isEmpty()) this.scenePoint = false;
            return this;
        }

        public OverlayEvent outlineChasing(boolean outlineChasing) {
            this.outlineChasing = outlineChasing;
            return this;
        }

        private static List<BlockPos> copyPositions(Collection<BlockPos> positions) {
            if (positions == null || positions.isEmpty()) {
                return Collections.emptyList();
            }
            LinkedHashSet<BlockPos> copy = new LinkedHashSet<BlockPos>();
            for (BlockPos position : positions) {
                if (position != null) {
                    copy.add(new BlockPos(position));
                }
            }
            return Collections.unmodifiableList(new ArrayList<BlockPos>(copy));
        }

        public OverlayEvent independent(int independentY) {
            this.independentY = independentY;
            this.pointAt = null;
            this.scenePoint = false;
            this.targetPositions = Collections.emptyList();
            this.placeNearTarget = false;
            return this;
        }

        public OverlayEvent placeNearTarget(boolean placeNearTarget) {
            this.placeNearTarget = placeNearTarget;
            return this;
        }

        public OverlayEvent connectorVisible(boolean connectorVisible) {
            this.connectorVisible = connectorVisible;
            return this;
        }

        public OverlayEvent overlayId(String overlayId) {
            this.overlayId = overlayId;
            return this;
        }

        public OverlayEvent parentOverlayId(String parentOverlayId) {
            this.parentOverlayId = parentOverlayId;
            return this;
        }

        public OverlayEvent guiOrigin(int guiX, int guiY) {
            this.guiX = guiX;
            this.guiY = guiY;
            return this;
        }

        public OverlayEvent captionPosition(int captionX, int captionY) {
            this.captionX = captionX;
            this.captionY = captionY;
            return this;
        }

        public OverlayEvent captionOffset(int captionOffsetX, int captionOffsetY) {
            this.captionOffsetX = captionOffsetX;
            this.captionOffsetY = captionOffsetY;
            return this;
        }

        public OverlayEvent guiTexture(ResourceLocation textureLocation, int textureU, int textureV, int regionWidth,
            int regionHeight, int textureWidth, int textureHeight, int displayWidth, int displayHeight, int offsetX,
            int offsetY, boolean framed) {
            this.type = OverlayEventType.GUI_TEXTURE;
            this.textureLocation = textureLocation;
            this.textureU = textureU;
            this.textureV = textureV;
            this.regionWidth = Math.max(1, regionWidth);
            this.regionHeight = Math.max(1, regionHeight);
            this.textureWidth = Math.max(1, textureWidth);
            this.textureHeight = Math.max(1, textureHeight);
            this.displayWidth = Math.max(1, displayWidth);
            this.displayHeight = Math.max(1, displayHeight);
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.framed = framed;
            return this;
        }

        public OverlayEvent guiSnapshot(ResourceLocation snapshotId) {
            this.type = OverlayEventType.GUI_TEXTURE;
            this.guiSnapshotId = snapshotId;
            return this;
        }

        public OverlayEvent applySnapshot(PonderGuiSnapshotRegistry.Snapshot snapshot, int offsetX, int offsetY) {
            if (snapshot == null) {
                return this;
            }
            this.textureLocation = snapshot.texture;
            this.textureU = snapshot.u;
            this.textureV = snapshot.v;
            this.regionWidth = Math.max(1, snapshot.regionWidth);
            this.regionHeight = Math.max(1, snapshot.regionHeight);
            this.textureWidth = Math.max(1, snapshot.textureWidth);
            this.textureHeight = Math.max(1, snapshot.textureHeight);
            this.displayWidth = Math.max(1, snapshot.displayWidth);
            this.displayHeight = Math.max(1, snapshot.displayHeight);
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.framed = snapshot.framed;
            return this;
        }

        public OverlayEvent scaleToParent(boolean scaleToParent) {
            this.scaleToParent = scaleToParent;
            return this;
        }

        public OverlayEvent stretchTexture(boolean stretchTexture, int stretchBorder) {
            this.stretchTexture = stretchTexture;
            this.stretchBorder = Math.max(1, stretchBorder);
            return this;
        }

        public OverlayEvent guiHighlight(String parentOverlayId, int guiX, int guiY, int guiWidth, int guiHeight) {
            this.type = OverlayEventType.GUI_HIGHLIGHT;
            this.parentOverlayId = parentOverlayId;
            this.guiX = guiX;
            this.guiY = guiY;
            this.guiWidth = Math.max(1, guiWidth);
            this.guiHeight = Math.max(1, guiHeight);
            return this;
        }

        public OverlayEvent offset(int offsetX, int offsetY) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            return this;
        }

        public OverlayEvent sceneOutline(AxisAlignedBB sceneBounds) {
            this.type = OverlayEventType.SCENE_OUTLINE;
            this.sceneBounds = sceneBounds;
            return this;
        }

        public OverlayEvent sceneOutline(AxisAlignedBB sceneBounds, Collection<BlockPos> positions) {
            sceneOutline(sceneBounds).sourcePositions(positions);
            if (sourcePositions.isEmpty()) {
                this.sceneBounds = null;
            }
            return this;
        }

        public OverlayEvent controls(Vec3d pointAt, Pointing direction) {
            this.type = OverlayEventType.CONTROLS;
            pointAt(pointAt);
            this.controlDirection = direction;
            return this;
        }

        public OverlayEvent sceneLine(Vec3d start, Vec3d end, boolean wide) {
            this.type = OverlayEventType.SCENE_LINE;
            this.lineStart = start;
            this.lineEnd = end;
            this.lineWide = wide;
            return this;
        }

        public OverlayEvent valueBox(Vec3d center, Vec3d expand) {
            this.type = OverlayEventType.VALUE_BOX;
            this.valueBoxCenter = center;
            this.valueBoxExpand = expand;
            pointAt(center);
            return this;
        }

        public OverlayEvent controlItem(ItemStack stack) {
            this.controlItem = stack == null ? ItemStack.EMPTY : stack.copy();
            return this;
        }

        public OverlayEvent controlLeftClick() {
            this.controlLeftClick = true;
            return this;
        }

        public OverlayEvent controlRightClick() {
            this.controlRightClick = true;
            return this;
        }

        public OverlayEvent controlScroll() {
            this.controlScroll = true;
            return this;
        }

        public OverlayEvent controlSneaking() {
            this.controlSneaking = true;
            return this;
        }

        public OverlayEvent controlCtrl() {
            this.controlCtrl = true;
            return this;
        }
    }

    private final StoryBoardEntry entry;
    private final List<ResourceLocation> tags;
    private final List<StoryBoardEntry.SceneOrderingEntry> orderingEntries;
    private final List<RecordedOperation> recordedOperations = new ArrayList<RecordedOperation>();
    private final List<WorldEvent> worldEvents = new ArrayList<WorldEvent>();
    private long sectionOwnershipPositions;
    private int ownershipBudgetValidatedEvents = -1;
    private final List<CameraEvent> cameraEvents = new ArrayList<CameraEvent>();
    private final List<CameraViewEvent> cameraViewEvents = new ArrayList<CameraViewEvent>();
    private BlockPos framingBounds;
    private final List<PoiEvent> poiEvents = new ArrayList<PoiEvent>();
    private final List<ParticleEffectEvent> particleEvents = new ArrayList<ParticleEffectEvent>();
    private final List<ActorEvent> actorEvents = new ArrayList<ActorEvent>();
    private final List<SoundEvent> soundEvents = new ArrayList<SoundEvent>();
    private final List<OverlayEvent> overlayEvents = new ArrayList<OverlayEvent>();
    private final PonderSceneBuildingUtil sceneBuildingUtil;
    private final PonderLocalization localization;
    private PonderSchematic schematic = PonderSchematic.empty();
    private ResourceLocation sceneId;
    private String title = "";
    private int basePlateOffsetX;
    private int basePlateOffsetZ;
    private int basePlateSize = 5;
    private float scaleFactor = 1.0F;
    // The legacy preview renderer has no textured shadow pass. Keep the unsafe
    // fallback disabled until a real transparent shadow implementation exists.
    private boolean hidePlatformShadow = true;
    private float sceneOffsetY;
    private boolean nextUpEnabled = true;
    private boolean finished;
    private int totalIdleTicks;
    private int keyframeCount;
    private final java.util.SortedSet<Integer> keyframeTicks = new java.util.TreeSet<Integer>();
    private int lazyKeyframeCount;
    private float accumulatedCameraYaw;
    private double recordedCameraYaw;
    private double recordedCameraPitch;

    public PonderScene(StoryBoardEntry entry, PonderLocalization localization) {
        this.entry = entry;
        this.localization = localization;
        this.tags = Collections.unmodifiableList(new ArrayList<ResourceLocation>(entry.getTags()));
        this.orderingEntries = Collections.unmodifiableList(
            new ArrayList<StoryBoardEntry.SceneOrderingEntry>(entry.getOrderingEntries()));
        this.sceneId = entry.getSchematicLocation();
        this.sceneBuildingUtil = new PonderSceneBuildingUtil(this);
    }

    public StoryBoardEntry getEntry() {
        return entry;
    }

    public String getNamespace() {
        return entry.getNamespace();
    }

    public ResourceLocation getComponent() {
        return entry.getComponent();
    }

    public ResourceLocation getSchematicLocation() {
        return entry.getSchematicLocation();
    }

    public List<ResourceLocation> getTags() {
        return tags;
    }

    public List<StoryBoardEntry.SceneOrderingEntry> getOrderingEntries() {
        return orderingEntries;
    }

    public ResourceLocation getSceneId() {
        return sceneId;
    }

    public void setSceneId(ResourceLocation sceneId) {
        this.sceneId = sceneId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getBasePlateOffsetX() {
        return basePlateOffsetX;
    }

    public void setBasePlateOffsetX(int basePlateOffsetX) {
        this.basePlateOffsetX = basePlateOffsetX;
    }

    public int getBasePlateOffsetZ() {
        return basePlateOffsetZ;
    }

    public void setBasePlateOffsetZ(int basePlateOffsetZ) {
        this.basePlateOffsetZ = basePlateOffsetZ;
    }

    public int getBasePlateSize() {
        return basePlateSize;
    }

    public void setBasePlateSize(int basePlateSize) {
        this.basePlateSize = basePlateSize;
    }

    public float getScaleFactor() {
        return scaleFactor;
    }

    public void setScaleFactor(float scaleFactor) {
        this.scaleFactor = scaleFactor;
    }

    public boolean isHidePlatformShadow() {
        return hidePlatformShadow;
    }

    public void setHidePlatformShadow(boolean hidePlatformShadow) {
        this.hidePlatformShadow = hidePlatformShadow;
    }

    public float getSceneOffsetY() {
        return sceneOffsetY;
    }

    public void setSceneOffsetY(float sceneOffsetY) {
        this.sceneOffsetY = sceneOffsetY;
    }

    public boolean isNextUpEnabled() {
        return nextUpEnabled;
    }

    public void setNextUpEnabled(boolean nextUpEnabled) {
        this.nextUpEnabled = nextUpEnabled;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public int getTotalIdleTicks() {
        return totalIdleTicks;
    }

    public void addIdleTicks(int ticks) {
        totalIdleTicks += Math.max(0, ticks);
    }

    public int getKeyframeCount() {
        return keyframeCount;
    }

    public void incrementKeyframeCount() {
        keyframeCount++;
        keyframeTicks.add(Integer.valueOf(totalIdleTicks));
    }

    /** Explicit keyframes only; lazy keyframes retain their existing bookkeeping semantics. */
    public List<Integer> getKeyframeTicks() {
        return Collections.unmodifiableList(new ArrayList<Integer>(keyframeTicks));
    }

    public int getLazyKeyframeCount() {
        return lazyKeyframeCount;
    }

    public void incrementLazyKeyframeCount() {
        lazyKeyframeCount++;
    }

    public float getAccumulatedCameraYaw() {
        return accumulatedCameraYaw;
    }

    public void addCameraYaw(float degrees) {
        if (!Float.isFinite(degrees) || Math.abs((double) accumulatedCameraYaw + degrees) > 1000000D)
            throw new IllegalArgumentException("Camera yaw exceeds the cumulative angle budget");
        accumulatedCameraYaw += degrees;
    }

    public void recordCameraYaw(float degrees) {
        if (degrees == 0.0F) {
            return;
        }
        appendCameraRotation(0F, degrees, 18, false, false);
    }

    public void recordCameraRotation(float pitch, float yaw, boolean instant) {
        recordCameraRotation(Float.valueOf(pitch), yaw, instant);
    }

    public void recordCameraRotation(Float pitch, float yaw, boolean instant) {
        if (!Float.isFinite(yaw) || Math.abs((double) accumulatedCameraYaw + yaw) > 1000000D)
            throw new IllegalArgumentException("Camera yaw exceeds the cumulative angle budget");
        appendCameraRotation(pitch == null ? 0F : pitch.floatValue(), yaw, instant ? 0 : 18, true, pitch != null);
        accumulatedCameraYaw += yaw;
    }

    private void appendCameraRotation(float pitch, float yaw, int duration, boolean targetBased, boolean affectsPitch) {
        double nextPitch = recordedCameraPitch + pitch;
        double nextYaw = recordedCameraYaw + yaw;
        if (!Float.isFinite(pitch) || !Float.isFinite(yaw) || Math.abs(nextPitch) > 1000000D
            || Math.abs(nextYaw) > 1000000D) throw new IllegalArgumentException("Camera rotation exceeds the cumulative angle budget");
        CameraEvent event = new CameraEvent(totalIdleTicks, pitch, yaw, duration, targetBased, affectsPitch);
        recordedCameraPitch = nextPitch;
        recordedCameraYaw = nextYaw;
        if (pitch != 0F || yaw != 0F || targetBased) cameraEvents.add(event);
    }

    public void recordCameraView(Vec3d center, Float multiplier, int duration) {
        float scale = multiplier == null ? 1F : multiplier.floatValue();
        if (!Float.isFinite(scale) || scale <= 0F || scale > 64F || duration < 0 || duration > 72000)
            throw new IllegalArgumentException("Invalid authored camera scale or duration");
        if (center != null && (!Double.isFinite(center.x) || !Double.isFinite(center.y)
            || !Double.isFinite(center.z) || Math.abs(center.x) > 30000000D
            || Math.abs(center.y) > 30000000D || Math.abs(center.z) > 30000000D))
            throw new IllegalArgumentException("Invalid authored camera center");
        Vec3d aligned = center == null ? null : new Vec3d(alignCameraCenter(center.x), center.y,
            alignCameraCenter(center.z));
        cameraViewEvents.add(new CameraViewEvent(totalIdleTicks, duration, aligned, scale, overlayEvents.size()));
    }

    private static double alignCameraCenter(double value) {
        return Math.abs(value - Math.rint(value)) < 1E-6D ? value + 0.5D : value;
    }

    /** Bounds are inclusive block coordinates, not dimensions or renderable air cells. */
    public void encapsulateBounds(BlockPos endpoint) {
        if (endpoint == null || endpoint.getX() < 1 || endpoint.getY() < 1 || endpoint.getZ() < 1
            || endpoint.getX() > 256 || endpoint.getY() > 256 || endpoint.getZ() > 256)
            throw new IllegalArgumentException("Camera bounds must be three integer endpoints in [1, 256]");
        framingBounds = framingBounds == null ? new BlockPos(endpoint) : new BlockPos(
            Math.max(framingBounds.getX(), endpoint.getX()), Math.max(framingBounds.getY(), endpoint.getY()),
            Math.max(framingBounds.getZ(), endpoint.getZ()));
    }

    public BlockPos getFramingBounds() { return framingBounds; }

    public List<CameraViewEvent> getCameraViewEvents() {
        return Collections.unmodifiableList(cameraViewEvents);
    }

    public boolean isOverlayCleared(OverlayEvent overlay, float tick) {
        if (overlay == null || overlay.recordedOrder < 0) return false;
        OverlayEventType type = overlay.getType();
        if (type != OverlayEventType.TEXT && type != OverlayEventType.SCENE_OUTLINE
            && type != OverlayEventType.SCENE_LINE && type != OverlayEventType.VALUE_BOX) return false;
        int low = 0, high = cameraViewEvents.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (cameraViewEvents.get(middle).tick <= tick) low = middle + 1;
            else high = middle;
        }
        return low > 0 && overlay.recordedOrder < cameraViewEvents.get(low - 1).overlayCutoff;
    }

    public void recordPointOfInterest(Vec3d location) {
        if (location == null) {
            return;
        }
        poiEvents.add(new PoiEvent(totalIdleTicks, location));
    }

    public void recordParticleEffect(Vec3d location, float amountPerCycle, int cycles, String particleName,
        Vec3d motion, boolean withinBlockSpace) {
        if (location == null || cycles <= 0) {
            return;
        }
        particleEvents.add(new ParticleEffectEvent(totalIdleTicks, location, amountPerCycle, cycles,
            particleName == null ? "custom" : particleName, motion == null ? Vec3d.ZERO : motion,
            withinBlockSpace));
    }

    public void recordParticleDistribution(Vec3d location, int amountPerCycle, int cycles, String particleName,
        Vec3d motion, String distribution, Vec3d spread) {
        if (!"point".equals(distribution) && !"box".equals(distribution) && !"sphere".equals(distribution))
            throw new IllegalArgumentException("Unknown particle distribution: " + distribution);
        if (amountPerCycle <= 0 || cycles <= 0) return;
        particleEvents.add(new ParticleEffectEvent(totalIdleTicks, location, amountPerCycle, cycles,
            particleName, motion == null ? Vec3d.ZERO : motion, false, distribution, spread));
    }

    public void recordActorSpawn(int actorId, ActorKind kind, Vec3d location, int duration, float angle, String poseName) {
        actorEvents.add(new ActorEvent(totalIdleTicks, Math.max(1, duration), actorId, kind, ActorEventType.SPAWN,
            location, null, null, angle, poseName, null, ItemStack.EMPTY));
    }

    public void recordItemActorSpawn(int actorId, Vec3d location, int duration, String displayName, ItemStack stack) {
        actorEvents.add(new ActorEvent(totalIdleTicks, Math.max(1, duration), actorId, ActorKind.ITEM,
            ActorEventType.SPAWN, location, null, null, 0.0F, null, displayName, stack));
    }

    public void recordActorMove(int actorId, ActorKind kind, Vec3d offset, int duration) {
        recordActorMove(actorId, kind, offset, duration, true);
    }

    public void recordActorMove(int actorId, ActorKind kind, Vec3d offset, int duration, boolean walkAnimation) {
        actorEvents.add(new ActorEvent(totalIdleTicks, Math.max(1, duration), actorId, kind, ActorEventType.MOVE, null,
            offset, null, 0.0F, null, null, ItemStack.EMPTY, null, walkAnimation));
    }

    public void recordActorEntranceMove(int actorId, ActorKind kind, Vec3d offset, int duration) {
        if (duration <= 0 || duration > 72000 || offset == null || !Double.isFinite(offset.x)
            || !Double.isFinite(offset.y) || !Double.isFinite(offset.z))
            throw new IllegalArgumentException("Invalid preview entrance motion");
        actorEvents.add(new ActorEvent(totalIdleTicks, duration, actorId, kind, ActorEventType.MOVE, null,
            offset, null, 0.0F, null, null, ItemStack.EMPTY, null, false, true));
    }

    public void recordActorNbtPatch(int actorId, ActorKind kind, NBTTagCompound patch) {
        recordActorNbtPatch(actorId, kind, patch, ItemStack.EMPTY);
    }

    public void recordActorNbtPatch(int actorId, ActorKind kind, NBTTagCompound patch, ItemStack item) {
        actorEvents.add(new ActorEvent(totalIdleTicks, 1, actorId, kind, ActorEventType.NBT_PATCH,
            null, null, null, 0.0F, null, null, item, patch, false));
    }

    public void recordSound(ResourceLocation id, SoundCategory category, float volume, float pitch) {
        if (soundEvents.size() >= 256) throw new IllegalArgumentException("Preview sound cues exceed the 256 scene budget");
        int simultaneous = 0;
        for (SoundEvent event : soundEvents) if (event.getTick() == totalIdleTicks) simultaneous++;
        if (simultaneous >= 16) throw new IllegalArgumentException("Preview sound cues exceed the 16 per-tick budget");
        soundEvents.add(new SoundEvent(totalIdleTicks, id, category, volume, pitch));
    }

    public void recordActorRotation(int actorId, ActorKind kind, Vec3d rotation, int duration) {
        actorEvents.add(new ActorEvent(totalIdleTicks, Math.max(1, duration), actorId, kind, ActorEventType.ROTATE,
            null, null, rotation, 0.0F, null, null, ItemStack.EMPTY));
    }

    public void recordActorCartRotation(int actorId, float angle, int duration) {
        actorEvents.add(new ActorEvent(totalIdleTicks, Math.max(1, duration), actorId, ActorKind.CART,
            ActorEventType.ROTATE, null, null, null, angle, null, null, ItemStack.EMPTY));
    }

    public void recordActorHide(int actorId, ActorKind kind, int duration) {
        actorEvents.add(new ActorEvent(totalIdleTicks, Math.max(1, duration), actorId, kind, ActorEventType.HIDE,
            null, null, null, 0.0F, null, null, ItemStack.EMPTY));
    }

    public void recordActorPose(int actorId, String poseName) {
        actorEvents.add(new ActorEvent(totalIdleTicks, 1, actorId, ActorKind.BIRB, ActorEventType.POSE, null, null,
            null, 0.0F, poseName, null, ItemStack.EMPTY));
    }

    public void recordPlayerLook(int actorId, Vec3d target, int duration) {
        actorEvents.add(new ActorEvent(totalIdleTicks, Math.max(1, duration), actorId, ActorKind.PLAYER,
            ActorEventType.LOOK_AT, target, null, null, 0.0F, null, null, ItemStack.EMPTY));
    }

    public void recordPlayerItem(int actorId, ItemStack stack) {
        actorEvents.add(new ActorEvent(totalIdleTicks, 1, actorId, ActorKind.PLAYER,
            ActorEventType.HELD_ITEM, null, null, null, 0.0F, null, null, stack));
    }

    public void recordPlayerSwing(int actorId, int duration) {
        actorEvents.add(new ActorEvent(totalIdleTicks, Math.max(1, duration), actorId, ActorKind.PLAYER,
            ActorEventType.SWING, null, null, null, 0.0F, null, null, ItemStack.EMPTY));
    }

    public void recordPlayerSneak(int actorId, boolean sneaking) {
        actorEvents.add(new ActorEvent(totalIdleTicks, 1, actorId, ActorKind.PLAYER,
            ActorEventType.SNEAK, null, null, null, sneaking ? 1.0F : 0.0F, null, null, ItemStack.EMPTY));
    }

    public OverlayEvent createOverlayEvent(int duration) {
        OverlayEvent event = new OverlayEvent(totalIdleTicks, Math.max(1, duration));
        event.recordedOrder = overlayEvents.size();
        overlayEvents.add(event);
        return event;
    }

    public PonderSceneBuildingUtil getSceneBuildingUtil() {
        return sceneBuildingUtil;
    }

    public PonderLocalization getLocalization() {
        return localization;
    }

    public PonderSchematic getSchematic() {
        return schematic;
    }

    public void setSchematic(PonderSchematic schematic) {
        this.schematic = schematic == null ? PonderSchematic.empty() : schematic;
    }

    public void recordOperation(String operation) {
        recordedOperations.add(new RecordedOperation(totalIdleTicks, operation));
    }

    public List<RecordedOperation> getRecordedOperations() {
        return Collections.unmodifiableList(recordedOperations);
    }

    public void recordWorldEvent(WorldEventType type, Iterable<BlockPos> positions, String stateDescription) {
        recordWorldEvent(type, positions, stateDescription, null);
    }

    public void recordWorldEvent(WorldEventType type, Iterable<BlockPos> positions, String stateDescription,
        EnumFacing direction) {
        recordWorldEvent(type, positions, stateDescription, direction, 0, null, null, null);
    }

    public void recordWorldEvent(WorldEventType type, Iterable<BlockPos> positions, String stateDescription,
        EnumFacing direction, int duration, Vec3d offset, Vec3d rotation, Vec3d pivot) {
        List<BlockPos> copiedPositions = new ArrayList<BlockPos>();
        for (BlockPos pos : positions) {
            copiedPositions.add(new BlockPos(pos));
        }

        if (copiedPositions.isEmpty()) {
            return;
        }

        worldEvents.add(new WorldEvent(totalIdleTicks, type, copiedPositions, stateDescription, direction, duration,
            offset, rotation, pivot));
    }

    public void recordWorldEvent(WorldEventType type, BlockPos pos, String stateDescription) {
        recordWorldEvent(type, pos, stateDescription, null);
    }

    public void recordWorldEvent(WorldEventType type, BlockPos pos, String stateDescription, EnumFacing direction) {
        List<BlockPos> copiedPositions = new ArrayList<BlockPos>(1);
        copiedPositions.add(new BlockPos(pos));
        worldEvents.add(new WorldEvent(totalIdleTicks, type, copiedPositions, stateDescription, direction));
    }

    public void recordSectionMove(Iterable<BlockPos> positions, Vec3d offset, int duration) {
        recordWorldEvent(WorldEventType.MOVE_SECTION, positions, null, null, duration, offset, null, null);
    }

    public void recordSectionOwnership(SectionOwnershipEvent ownership, Iterable<BlockPos> positions,
        EnumFacing direction, int duration, Vec3d offset, Vec3d rotation, Vec3d pivot) {
        if (ownership == null || duration < 0 || duration > 72000)
            throw new IllegalArgumentException("Bounded section ownership event required");
        List<BlockPos> copied = new ArrayList<BlockPos>();
        for (BlockPos pos : positions) {
            if (copied.size() >= 262144 || Math.abs((long) pos.getX()) > 30000000 || Math.abs((long) pos.getY()) > 30000000 || Math.abs((long) pos.getZ()) > 30000000)
                throw new IllegalArgumentException("Section positions exceed their bounded preview domain");
            copied.add(pos.toImmutable());
        }
        if (worldEvents.size() >= 65536 || sectionOwnershipPositions + copied.size() > 1048576)
            throw new IllegalArgumentException("Expanded section ownership exceeds event/position budgets");
        sectionOwnershipPositions += copied.size();
        WorldEventType type = ownership.getAction() == SectionOwnershipEvent.Action.MOVE ? WorldEventType.MOVE_SECTION
            : ownership.getAction() == SectionOwnershipEvent.Action.ROTATE ? WorldEventType.ROTATE_SECTION
            : ownership.getAction() == SectionOwnershipEvent.Action.SHOW ? WorldEventType.SHOW_SECTION
            : ownership.getAction() == SectionOwnershipEvent.Action.HIDE ? WorldEventType.HIDE_SECTION : WorldEventType.SECTION_OWNERSHIP;
        worldEvents.add(new WorldEvent(totalIdleTicks, type, copied, null, direction, duration, offset, rotation, pivot, ownership));
    }

    public boolean hasSectionOwnership() {
        for (WorldEvent event : worldEvents) if (event.getSectionOwnership() != null) return true;
        return false;
    }

    public void validateSectionOwnershipBudget() {
        if (ownershipBudgetValidatedEvents == worldEvents.size()) return;
        if (hasSectionOwnership()) {
            SectionOwnershipBudget budget = new SectionOwnershipBudget();
            for (WorldEvent event : worldEvents) {
                if (event.getSectionOwnership() != null) budget.add(event.getSectionOwnership(), event.getPositions().size());
                else if (event.getType() == WorldEventType.MOVE_SECTION || event.getType() == WorldEventType.ROTATE_SECTION)
                    budget.legacyMotion(event.getPositions().size());
            }
        }
        ownershipBudgetValidatedEvents = worldEvents.size();
    }

    public void recordSectionRotation(Iterable<BlockPos> positions, Vec3d pivot, Vec3d rotation, int duration) {
        recordWorldEvent(WorldEventType.ROTATE_SECTION, positions, null, null, duration, null, rotation, pivot);
    }

    public List<WorldEvent> getWorldEvents() {
        return Collections.unmodifiableList(worldEvents);
    }

    public List<CameraEvent> getCameraEvents() {
        return Collections.unmodifiableList(cameraEvents);
    }

    public List<PoiEvent> getPointOfInterestEvents() {
        return Collections.unmodifiableList(poiEvents);
    }

    public List<ParticleEffectEvent> getParticleEvents() {
        return Collections.unmodifiableList(particleEvents);
    }

    public List<ActorEvent> getActorEvents() {
        return Collections.unmodifiableList(actorEvents);
    }

    public List<SoundEvent> getSoundEvents() { return Collections.unmodifiableList(soundEvents); }

    public List<OverlayEvent> getOverlayEvents() {
        return Collections.unmodifiableList(overlayEvents);
    }

    public List<String> getOperationLog() {
        List<String> operationLog = new ArrayList<String>(recordedOperations.size());
        for (RecordedOperation recordedOperation : recordedOperations) {
            operationLog.add(recordedOperation.getDescription());
        }
        return Collections.unmodifiableList(operationLog);
    }

    public void program(SceneBuilder scene, SceneBuildingUtil util) {
        entry.getBoard().program(scene, util);
        validateSectionOwnershipBudget();
    }
}
