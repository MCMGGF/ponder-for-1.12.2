package net.createmod.ponder.compat.ftbquests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;

import net.createmod.ponder.compat.ftbquests.QuestSceneResolver.Failure;
import net.createmod.ponder.compat.ftbquests.QuestSceneResolver.Result;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSceneBuilder;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class QuestSceneResolverTest {

    private static final String MACHINE = "ponder_legacy:mmce/modularmachinery/test_machine";

    @BeforeAll
    static void registerBlocks() {
        Bootstrap.register();
    }

    @Test
    void matchesActualProgrammedSceneIdentityInsteadOfSharedSchematicOrOrdinal() {
        PonderScene first = scene("first");
        PonderScene second = scene("selected");
        assertEquals(first.getSchematicLocation(), second.getSchematicLocation());
        QuestSceneLinks.Target target = new QuestSceneLinks.Target("test:machine", "test:selected");
        Result before = QuestSceneResolver.resolveCompiled(target, Arrays.asList(first, second));
        Result after = QuestSceneResolver.resolveCompiled(target, Arrays.asList(second, scene("inserted"), first));
        assertTrue(before.isAvailable());
        assertEquals(1, before.index);
        assertTrue(after.isAvailable());
        assertEquals(0, after.index);
        assertEquals("test:selected", after.sceneId);
    }

    @Test
    void deletedSceneDoesNotOpenAnotherSceneEvenWhenOldOrdinalRemainsInRange() {
        QuestSceneLinks.Target target = QuestSceneLinks.parse(new StringReader(
            "{\"quests\":{\"1\":[{\"component\":\"test:machine\",\"scene\":1,\"sceneId\":\"test:removed\"}]}}"))
            .forQuest("1").get(0);
        assertFailure(Failure.SCENE_MISSING,
            QuestSceneResolver.resolve(target, Arrays.asList("test:kept", "test:replacement", "test:other")));
    }

    @Test
    void duplicateSceneIdsFailClosedInsteadOfSelectingTheFirstOccurrence() {
        assertFailure(Failure.AMBIGUOUS_ID, QuestSceneResolver.resolve(
            new QuestSceneLinks.Target("test:machine", "test:duplicate"),
            Arrays.asList("test:duplicate", "test:other", "test:duplicate")));
    }

    @Test
    void mmceLegacyOrdinalsResolveToVerifiedVariantIdsAfterReordering() {
        Result maximum = QuestSceneResolver.resolve(new QuestSceneLinks.Target(MACHINE, 1),
            Arrays.asList(MACHINE + "/maximum", MACHINE + "/minimum"));
        assertTrue(maximum.isAvailable());
        assertEquals(0, maximum.index);
        assertEquals(MACHINE + "/maximum", maximum.sceneId);
    }

    @Test
    void removedLegacyMmceVariantCannotFallBackToTheOtherVariant() {
        assertFailure(Failure.SCENE_MISSING, QuestSceneResolver.resolve(new QuestSceneLinks.Target(MACHINE, 1),
            Arrays.asList(MACHINE + "/minimum", "test:replacement")));
    }

    @Test
    void mmceBindingAliasRequiresItsRealSceneIdAndDoesNotGuessFromTheSuffix() {
        String binding = MACHINE + "/binding_1";
        QuestSceneLinks.Target legacy = new QuestSceneLinks.Target(binding, 0);
        assertEquals(Failure.LEGACY_ID_REQUIRED, QuestSceneResolver.metadataFailure(legacy, 1));
        assertFailure(Failure.LEGACY_ID_REQUIRED,
            QuestSceneResolver.resolve(legacy, Collections.singletonList(MACHINE + "/minimum")));
        assertFailure(Failure.LEGACY_ID_REQUIRED,
            QuestSceneResolver.resolve(legacy, Collections.singletonList(binding + "/minimum")));
        Result alias = QuestSceneResolver.resolve(new QuestSceneLinks.Target(binding, MACHINE + "/minimum"),
            Collections.singletonList(MACHINE + "/minimum"));
        assertTrue(alias.isAvailable());
        Result literalMachinePath = QuestSceneResolver.resolve(
            new QuestSceneLinks.Target(binding, binding + "/minimum"),
            Collections.singletonList(binding + "/minimum"));
        assertTrue(literalMachinePath.isAvailable());
    }

    @Test
    void unknownLegacyOrdinalsRequireMigrationEvenIfOnlyOneSceneExists() {
        QuestSceneLinks.Target target = new QuestSceneLinks.Target("test:machine", 0);
        assertEquals(Failure.LEGACY_ID_REQUIRED, QuestSceneResolver.metadataFailure(target, 1));
        assertFailure(Failure.LEGACY_ID_REQUIRED,
            QuestSceneResolver.resolve(target, Collections.singletonList("test:only")));
    }

    @Test
    void missingComponentIsReportedWithoutAssumingAnItemAlias() {
        QuestSceneLinks.Target target = new QuestSceneLinks.Target("test:machine", "test:scene");
        assertEquals(Failure.COMPONENT_MISSING, QuestSceneResolver.metadataFailure(target, null));
        assertEquals(Failure.COMPONENT_MISSING, QuestSceneResolver.metadataFailure(target, 0));
        assertNull(QuestSceneResolver.metadataFailure(target, 1));
    }

    @Test
    void automaticMatchingSelectsTheCurrentFirstSceneOnlyForAnUnmappedComponent() {
        QuestSceneLinks.Target automatic = QuestSceneLinks.Target.automatic("test:machine");
        assertNull(QuestSceneResolver.metadataFailure(automatic, 2));
        Result result = QuestSceneResolver.resolve(automatic, Arrays.asList("test:first", "test:second"));
        assertEquals("test:first", result.sceneId);
        assertEquals(0, result.index);
        assertFailure(Failure.AMBIGUOUS_ID,
            QuestSceneResolver.resolve(automatic, Arrays.asList("test:first", "test:first")));
    }

    @Test
    void emptyOrUnnamedScenesCannotBeOpened() {
        QuestSceneLinks.Target target = new QuestSceneLinks.Target("test:machine", "test:scene");
        assertFailure(Failure.SCENE_MISSING, QuestSceneResolver.resolve(target, Collections.emptyList()));
        assertFailure(Failure.SCENE_MISSING,
            QuestSceneResolver.resolve(QuestSceneLinks.Target.automatic("test:machine"),
                Collections.singletonList(null)));
    }

    private static void assertFailure(Failure failure, Result result) {
        assertFalse(result.isAvailable());
        assertEquals(failure, result.failure);
        assertEquals(-1, result.index);
        assertNull(result.sceneId);
    }

    private static PonderScene scene(String id) {
        PonderStoryBoardEntry entry = new PonderStoryBoardEntry((scene, util) -> { }, "test",
            new ResourceLocation("test", "shared"), new ResourceLocation("test", "machine"));
        PonderScene scene = new PonderScene(entry, new PonderLocalization());
        new PonderSceneBuilder(scene).title(id, id);
        return scene;
    }
}
