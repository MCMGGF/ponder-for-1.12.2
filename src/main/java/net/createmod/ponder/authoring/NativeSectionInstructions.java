package net.createmod.ponder.authoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.SectionOwnershipEvent;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Lossless declarative transport for native ownership records, not an executable callback. */
public final class NativeSectionInstructions {
    private NativeSectionInstructions() { }
    public static void register(AuthoringCompiler compiler) {
        compiler.registerHandler("native_section", new AuthoringCompiler.StepHandler() {
            @Override public void validate(AuthoringDocument document, JsonObject step, String path, AuthoringValidation.Builder issues) {
                try { data(step); positions(step); timing(step); vectors(step); }
                catch (RuntimeException invalid) { issues.error("native.section", path, invalid.getMessage()); }
            }
            @Override public void apply(SceneBuilder scene, SceneBuildingUtil util, JsonObject step, AuthoringCompiler.StepContext context) {
                SectionOwnershipEvent data = data(step);
                validateOrder(data, context.getState());
                scene.getScene().recordSectionOwnership(data, positions(step), direction(step), timing(step),
                    optionalVector(step, "offset"), optionalVector(step, "rotation"), optionalVector(step, "pivot"));
            }
        });
    }
    private static SectionOwnershipEvent data(JsonObject step) {
        if (AuthoringCompiler.integer(step, "nativeVersion", -1, 1, 1) != 1) throw new IllegalArgumentException("Native section version 1 required");
        for (String key : step.keySet())
            if (!java.util.Arrays.asList("type","nativeVersion","action","sectionId","targetId","revealId","smartDisplay","visible",
                "positions","direction","duration","offset","rotation","pivot","sourceTick","sourceOrdinal").contains(key))
                throw new IllegalArgumentException("Unknown native section field: " + key);
        SectionOwnershipEvent.Action action = SectionOwnershipEvent.Action.valueOf(AuthoringCompiler.requiredString(step,"action"));
        return new SectionOwnershipEvent(action, AuthoringDocument.string(step,"sectionId",""), AuthoringDocument.string(step,"targetId",""),
            AuthoringDocument.string(step,"revealId",""), AuthoringCompiler.bool(step,"smartDisplay",false), AuthoringCompiler.bool(step,"visible",false));
    }
    static List<BlockPos> positions(JsonObject step) {
        if (!step.has("positions") || !step.get("positions").isJsonArray()) throw new IllegalArgumentException("Native section positions must be an explicit array");
        if (step.getAsJsonArray("positions").size() == 0) return Collections.emptyList();
        return AuthoringCompiler.positions(step);
    }
    private static int timing(JsonObject step) { return AuthoringCompiler.integer(step,"duration",0,0,72000); }
    private static EnumFacing direction(JsonObject step) {
        if (!step.has("direction") || step.get("direction").isJsonNull()) return null;
        return EnumFacing.valueOf(AuthoringCompiler.requiredString(step,"direction").toUpperCase(Locale.ROOT));
    }
    private static Vec3d optionalVector(JsonObject step, String key) {
        return step.has(key) ? AuthoringCompiler.vector(step,key,Vec3d.ZERO) : null;
    }
    private static void vectors(JsonObject step) {
        direction(step);
        SectionOwnershipEvent data=data(step);
        if (data.getAction() == SectionOwnershipEvent.Action.MOVE && !step.has("offset")) throw new IllegalArgumentException("Owned move requires an offset");
        if (data.getAction() == SectionOwnershipEvent.Action.ROTATE && (!step.has("rotation") || !step.has("pivot"))) throw new IllegalArgumentException("Owned rotation requires rotation and pivot");
        boolean move = data.getAction() == SectionOwnershipEvent.Action.MOVE, rotate = data.getAction() == SectionOwnershipEvent.Action.ROTATE;
        boolean fading = data.getAction() == SectionOwnershipEvent.Action.SHOW || data.getAction() == SectionOwnershipEvent.Action.HIDE;
        if (step.has("offset") && !move || (step.has("rotation") || step.has("pivot")) && !rotate
            || !fading && step.has("direction") || data.getAction() == SectionOwnershipEvent.Action.PREPARE && timing(step) != 0
            || data.getAction() == SectionOwnershipEvent.Action.STAGE && timing(step) != 0 || data.getAction() == SectionOwnershipEvent.Action.PLACE && timing(step) != 0)
            throw new IllegalArgumentException("Native section field does not apply to this action");
        if (fading && timing(step) == 0 && direction(step) != null)
            throw new IllegalArgumentException("An instant native section cannot specify a fading direction");
        for(String key:new String[]{"offset","rotation","pivot"}) if(step.has(key)) {
            Vec3d value = optionalVector(step,key);
            if ("rotation".equals(key) && (Math.abs(value.x) > 1000000 || Math.abs(value.y) > 1000000 || Math.abs(value.z) > 1000000))
                throw new IllegalArgumentException("Section rotation exceeds numeric budget");
        }
    }
    static void validateReferenceOrder(JsonObject step, java.util.Map<String,Object> state) {
        SectionOwnershipEvent event = data(step);
        validateOrder(event, state);
        net.createmod.ponder.foundation.SectionOwnershipBudget budget = (net.createmod.ponder.foundation.SectionOwnershipBudget)
            state.computeIfAbsent("native-section-budget", key -> new net.createmod.ponder.foundation.SectionOwnershipBudget());
        budget.add(event, positions(step).size());
    }
    @SuppressWarnings("unchecked")
    private static void validateOrder(SectionOwnershipEvent data, java.util.Map<String,Object> state) {
        Set<String> sections=(Set<String>)state.computeIfAbsent("native-section-identities",key -> new LinkedHashSet<String>());
        Set<String> reveals=(Set<String>)state.computeIfAbsent("native-section-reveals",key -> new LinkedHashSet<String>());
        switch(data.getAction()) {
            case PREPARE:
                if(!reveals.add(data.getRevealId())) throw new IllegalArgumentException("Duplicate native reveal identity"); break;
            case STAGE:
                if(!reveals.contains(data.getRevealId())) throw new IllegalArgumentException("Unknown native reveal identity"); break;
            case SHOW: case HIDE:
                if(!data.getTargetId().isEmpty() && !sections.contains(data.getTargetId())) throw new IllegalArgumentException("Native merge target must already exist");
                if(!data.getRevealId().isEmpty() && !reveals.contains(data.getRevealId())) throw new IllegalArgumentException("Unknown native show reveal");
                if(!sections.add(data.getSectionId())) throw new IllegalArgumentException("Native section identity cannot be reused"); break;
            case MOVE: case ROTATE:
                if(!sections.contains(data.getSectionId())) throw new IllegalArgumentException("Unknown native motion section"); break;
            default: break;
        }
    }
    static JsonObject export(PonderScene.WorldEvent event) {
        SectionOwnershipEvent data=event.getSectionOwnership();
        JsonObject step=new JsonObject(); step.addProperty("type","native_section"); step.addProperty("nativeVersion",1);
        step.addProperty("action",data.getAction().name()); step.addProperty("sectionId",data.getSectionId());
        step.addProperty("targetId",data.getTargetId()); step.addProperty("revealId",data.getRevealId());
        step.addProperty("smartDisplay",data.isSmartDisplay()); step.addProperty("visible",data.isVisible());
        step.addProperty("duration",event.getDuration());
        JsonArray positions=new JsonArray(); for(BlockPos pos:event.getPositions()) positions.add(vec(new Vec3d(pos))); step.add("positions",positions);
        if(event.getDirection()!=null) step.addProperty("direction",event.getDirection().name());
        if(event.getOffset()!=null) step.add("offset",vec(event.getOffset()));
        if(event.getRotation()!=null) step.add("rotation",vec(event.getRotation()));
        if(event.getPivot()!=null) step.add("pivot",vec(event.getPivot()));
        return step;
    }
    private static JsonArray vec(Vec3d value) { JsonArray result=new JsonArray(); result.add(value.x); result.add(value.y); result.add(value.z); return result; }
}
