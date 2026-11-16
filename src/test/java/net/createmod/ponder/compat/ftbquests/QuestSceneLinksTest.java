package net.createmod.ponder.compat.ftbquests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

class QuestSceneLinksTest {

    @Test
    void normalizesUnsignedFtb112IdsWithoutLosingHighBit() {
        assertEquals("ffffffff", QuestSceneLinks.idFromInt(-1));
        assertEquals("80000000", QuestSceneLinks.idFromInt(Integer.MIN_VALUE));
        assertEquals("00abcdef", QuestSceneLinks.normalizeId("ABCDEF"));
        assertEquals("00000001", QuestSceneLinks.normalizeId("1"));
        assertThrows(IllegalArgumentException.class, () -> QuestSceneLinks.normalizeId("123456789"));
        assertThrows(IllegalArgumentException.class, () -> QuestSceneLinks.normalizeId("../a"));
    }

    @Test
    void parsesQuestAndTaskMappingsAndPreservesSceneSelection() {
        QuestSceneLinks links = parse("{\"version\":1,\"quests\":{\"ABCDEF\":["
            + "{\"component\":\"ponder_legacy:mmce/machine/compressor\",\"scene\":2}]},"
            + "\"tasks\":{\"f67e7463\":[{\"component\":\"modularmachinery:blockinputbus\"}]}}");
        assertEquals(2, links.forQuest("00abcdef").get(0).scene);
        assertEquals("ponder_legacy:mmce/machine/compressor", links.forQuest("abcdef").get(0).component);
        assertEquals("modularmachinery:blockinputbus", links.forTask("F67E7463").get(0).component);
        assertTrue(links.forQuest("222b4665").isEmpty());
    }

    @Test
    void rejectsBadIdsDuplicateNormalizedIdsAndWrongTypes() {
        for (String json : Arrays.asList(
            "{\"version\":2}",
            "{\"enabled\":\"false\"}",
            "{\"quests\":{\"xyz\":[]}}",
            "{\"quests\":{\"1\":[],\"00000001\":[]}}",
            "{\"quests\":{\"1\":{}}}",
            "{\"quests\":{\"1\":[{\"component\":\"bad id\"}]}}",
            "{\"tasks\":{\"1\":[{\"component\":\"minecraft:stone\",\"scene\":-1}]}}",
            "{\"tasks\":{\"1\":[{\"component\":\"minecraft:stone\",\"scene\":0.5}]}}",
            "{\"tasks\":{\"1\":[{\"component\":\"minecraft:stone\",\"scene\":4294967296}]}}",
            "{\"tasks\":{\"1\":[{\"component\":\"minecraft:stone\",\"sceneId\":1}]}}",
            "{\"tasks\":{\"1\":[{\"component\":\"minecraft:stone\",\"sceneId\":null}]}}",
            "{\"tasks\":{\"1\":[{\"component\":\"minecraft:stone\",\"sceneId\":\"unnamespaced\"}]}}",
            "{\"tasks\":{\"1\":[{\"component\":\"minecraft:stone\",\"sceneId\":\"bad:id with spaces\"}]}}")) {
            assertThrows(JsonParseException.class, () -> parse(json), json);
        }
    }

    @Test
    void keepsExplicitFailuresVisibleAndPreventsAutomaticFallbacksForTheirComponents() {
        QuestSceneLinks links = parse("{\"quests\":{\"222b4665\":["
            + "{\"component\":\"ponder_legacy:machine/compressor\",\"scene\":1},"
            + "{\"component\":\"missing:machine\"}]},\"tasks\":{\"f67e7463\":["
            + "{\"component\":\"modularmachinery:blockinputbus\"}]}}");
        Map<String, List<String>> tasks = new LinkedHashMap<String, List<String>>();
        tasks.put("f67e7463", Arrays.asList("ponder_legacy:machine/compressor", "modularmachinery:blockinputbus"));
        tasks.put("10", Collections.singletonList("missing:auto"));
        Map<String, Integer> available = new LinkedHashMap<String, Integer>();
        available.put("ponder_legacy:machine/compressor", Integer.valueOf(2));
        available.put("modularmachinery:blockinputbus", Integer.valueOf(1));
        List<QuestSceneLinks.Target> resolved = links.resolve("222b4665", tasks,
            "modularmachinery:blockinputbus", available);
        assertEquals(Arrays.asList(new QuestSceneLinks.Target("ponder_legacy:machine/compressor", 1),
            new QuestSceneLinks.Target("missing:machine", 0),
            new QuestSceneLinks.Target("modularmachinery:blockinputbus", 0)), resolved);
        assertThrows(UnsupportedOperationException.class,
            () -> resolved.add(new QuestSceneLinks.Target("minecraft:stone", 0)));
    }

    @Test
    void preservesUnavailableExplicitTargetsWithoutResolvingAliasesOrDroppingTheirReasons() {
        QuestSceneLinks links = parse("{\"quests\":{\"1\":["
            + "{\"component\":\"modularmachinery:blockcontroller\"},"
            + "{\"component\":\"ponder_legacy:machine/compressor\",\"scene\":3}]}}");
        List<QuestSceneLinks.Target> targets = links.resolve("1", Collections.<String, List<String>>emptyMap(), null,
            Collections.singletonMap("ponder_legacy:machine/compressor", Integer.valueOf(3)));
        assertEquals(2, targets.size());
        assertEquals("modularmachinery:blockcontroller", targets.get(0).component);
        assertEquals(3, targets.get(1).scene);
    }

    @Test
    void flagsDisableAutomaticOrAllLinksWithoutChangingTaskData() {
        QuestSceneLinks manual = parse("{\"autoLinkItems\":false,\"quests\":{\"1\":["
            + "{\"component\":\"modularmachinery:blockinputbus\"}]}}");
        Map<String, List<String>> tasks = Collections.singletonMap("2", Collections.singletonList("minecraft:stone"));
        Map<String, Integer> available = new LinkedHashMap<String, Integer>();
        available.put("modularmachinery:blockinputbus", Integer.valueOf(1));
        available.put("minecraft:stone", Integer.valueOf(1));
        assertFalse(manual.autoLinkItems);
        assertEquals(1, manual.resolve("1", tasks, "minecraft:stone", available).size());
        QuestSceneLinks disabled = parse("{\"enabled\":false}");
        assertTrue(disabled.resolve("1", tasks, "minecraft:stone", available).isEmpty());
        assertEquals(1, tasks.size());
        assertEquals("minecraft:stone", tasks.get("2").get(0));
    }

    @Test
    void stableIdIsAuthoritativeEvenWhenLegacyOrdinalHintDisagrees() {
        QuestSceneLinks.Target target = parse("{\"quests\":{\"1\":[{\"component\":\"test:machine\","
            + "\"scene\":42,\"sceneId\":\"test:assembly\"}]}}").forQuest("1").get(0);
        assertEquals(42, target.scene);
        assertEquals("test:assembly", target.sceneId);
        assertEquals("test:assembly", target.stableSceneId());
        assertEquals(new QuestSceneLinks.Target("test:machine", "test:assembly"), target);
    }

    @Test
    void canonicalMmceLegacyMappingsEqualTheirExplicitStableMigration() {
        String component = "ponder_legacy:mmce/modularmachinery/extendable_calculator_subsystem_l4";
        QuestSceneLinks.Target minimum = new QuestSceneLinks.Target(component, 0);
        QuestSceneLinks.Target maximum = new QuestSceneLinks.Target(component, 1);
        assertEquals(component + "/minimum", minimum.stableSceneId());
        assertEquals(component + "/maximum", maximum.stableSceneId());
        QuestSceneLinks.Target stable = new QuestSceneLinks.Target(component, component + "/maximum");
        assertEquals(maximum, stable);
        assertEquals(maximum.hashCode(), stable.hashCode());
        assertNull(new QuestSceneLinks.Target(component, 2).stableSceneId());
        assertNull(new QuestSceneLinks.Target("modularmachinery:blockcontroller", 0).stableSceneId());
    }

    @Test
    void ambiguousMmceBindingSuffixRequiresExplicitIdentityAndIsNeverStripped() {
        String machine = "ponder_legacy:mmce/modularmachinery/machine";
        String binding = machine + "/binding_1";
        assertNull(new QuestSceneLinks.Target(binding, 0).stableSceneId());
        assertNull(new QuestSceneLinks.Target(binding, 1).stableSceneId());
        assertEquals(machine + "/minimum",
            new QuestSceneLinks.Target(binding, machine + "/minimum").stableSceneId());
        assertEquals(binding + "/minimum",
            new QuestSceneLinks.Target(binding, binding + "/minimum").stableSceneId());
    }

    @Test
    void stableIdsDeduplicateWithoutDependingOnOptionalOrdinalHints() {
        QuestSceneLinks links = parse("{\"quests\":{\"1\":[{\"component\":\"test:machine\","
            + "\"scene\":1,\"sceneId\":\"test:assembly\"},{\"component\":\"test:machine\","
            + "\"scene\":2,\"sceneId\":\"test:assembly\"}]}}");
        assertEquals(1, links.forQuest("1").size());
    }

    @Test
    void automaticLinksRemainLazyAndDoNotInventEntriesForMissingComponents() {
        Map<String, List<String>> items = new LinkedHashMap<>();
        items.put("2", Arrays.asList("test:machine", "missing:machine", "test:machine"));
        List<QuestSceneLinks.Target> targets = QuestSceneLinks.defaults().resolve("1", items, "test:machine",
            Collections.singletonMap("test:machine", 2));
        assertEquals(1, targets.size());
        assertTrue(targets.get(0).isAutomatic());
        assertNull(targets.get(0).stableSceneId());
    }

    private static QuestSceneLinks parse(String json) {
        return QuestSceneLinks.parse(new StringReader(json));
    }
}
