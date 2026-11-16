package net.createmod.ponder.compat.ftbquests;

import java.util.ArrayList;
import java.util.List;

import net.createmod.ponder.foundation.PonderScene;

/** Resolves actual compiled scene IDs; registration/schematic order is not a scene identity. */
final class QuestSceneResolver {

    enum Failure {
        COMPONENT_MISSING("component_missing"),
        LEGACY_ID_REQUIRED("legacy_id_required"),
        SCENE_MISSING("scene_missing"),
        AMBIGUOUS_ID("ambiguous_id"),
        COMPILE_FAILED("compile_failed"),
        INDEX_CHANGED("index_changed");

        final String translationKey;

        Failure(String key) {
            translationKey = "ponder.ftbquests." + key;
        }
    }

    static Failure metadataFailure(QuestSceneLinks.Target target, Integer sceneCount) {
        if (sceneCount == null || sceneCount.intValue() <= 0) {
            return Failure.COMPONENT_MISSING;
        }
        if (!target.isAutomatic() && target.stableSceneId() == null) {
            return Failure.LEGACY_ID_REQUIRED;
        }
        return null;
    }

    static Result resolve(QuestSceneLinks.Target target, List<String> compiledSceneIds) {
        String wanted = target.stableSceneId();
        if (wanted == null && !target.isAutomatic()) {
            return Result.failed(Failure.LEGACY_ID_REQUIRED);
        }
        if (compiledSceneIds.isEmpty()) {
            return Result.failed(Failure.SCENE_MISSING);
        }
        if (target.isAutomatic()) {
            wanted = compiledSceneIds.get(0);
        }
        if (wanted == null) {
            return Result.failed(Failure.SCENE_MISSING);
        }
        int selected = -1;
        for (int index = 0; index < compiledSceneIds.size(); index++) {
            if (wanted.equals(compiledSceneIds.get(index))) {
                if (selected >= 0) {
                    return Result.failed(Failure.AMBIGUOUS_ID);
                }
                selected = index;
            }
        }
        return selected < 0 ? Result.failed(Failure.SCENE_MISSING) : new Result(selected, wanted, null);
    }

    static Result resolveCompiled(QuestSceneLinks.Target target, List<PonderScene> scenes) {
        List<String> ids = new ArrayList<>();
        for (PonderScene scene : scenes) {
            ids.add(scene.getSceneId() == null ? null : scene.getSceneId().toString());
        }
        return resolve(target, ids);
    }

    static final class Result {
        final int index;
        final String sceneId;
        final Failure failure;

        private Result(int index, String sceneId, Failure failure) {
            this.index = index;
            this.sceneId = sceneId;
            this.failure = failure;
        }

        static Result failed(Failure failure) {
            return new Result(-1, null, failure);
        }

        boolean isAvailable() {
            return failure == null;
        }
    }
}
