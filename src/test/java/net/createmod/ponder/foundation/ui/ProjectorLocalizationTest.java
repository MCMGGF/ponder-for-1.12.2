package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ProjectorLocalizationTest {
    @Test void bothLanguagesNameTheRegisteredPonderKeyAndCategory() throws Exception {
        for (String language : new String[] {"en_us", "zh_cn"}) {
            Properties values = language(language);
            translated(values, "key.categories.ponder");
            translated(values, "key.ponder.open_ui");
        }
        assertEquals("\u601d\u7d22", language("zh_cn").getProperty("key.categories.ponder"));
        assertEquals("\u6253\u5f00\u601d\u7d22", language("zh_cn").getProperty("key.ponder.open_ui"));
    }

    @Test void projectorCommandsHaveReadableLabelsInBothLanguages() throws Exception {
        for (String language : new String[] {"en_us", "zh_cn"}) {
            Properties values = language(language);
            for (String suffix : new String[] {"projection_choose", "projection_restart", "projection_read"}) {
                String label = translated(values, "ponder.authoring." + suffix);
                assertTrue(label.codePointCount(0, label.length()) > 1);
                for (ProjectorConfigLayout layout : new ProjectorConfigLayout[] {
                    new ProjectorConfigLayout(320,240,1280,960), new ProjectorConfigLayout(1280,720,1280,720)}) {
                    int id = "projection_choose".equals(suffix) ? 3 : "projection_restart".equals(suffix) ? 4 : 7;
                    int conservativeWidth = label.codePoints().map(c -> c > 127 ? 9 : 6).sum();
                    assertTrue(conservativeWidth <= layout.button(id).width - 8, language + ": " + suffix);
                }
            }
        }
    }

    @Test void replacementThermalPanelsHaveMatchingLocalizedReadOnlyNames() throws Exception {
        for (String language : new String[] {"en_us", "zh_cn"}) {
            Properties values = language(language);
            for (String suffix : new String[] {"schematic", "not_live", "panel.augment", "panel.config", "panel.redstone"}) {
                translated(values, "ponder.snapshot.thermal." + suffix);
            }
        }
    }

    private static String translated(Properties values, String key) {
        String value = values.getProperty(key);
        assertNotNull(value, key);
        assertFalse(value.trim().isEmpty(), key);
        assertNotEquals(key, value, key);
        return value;
    }

    private static Properties language(String language) throws Exception {
        Properties result = new Properties();
        try (Reader reader = Files.newBufferedReader(Paths.get("src/main/resources/assets/ponder_legacy/lang/" + language + ".lang"), StandardCharsets.UTF_8)) {
            result.load(reader);
        }
        return result;
    }
}
