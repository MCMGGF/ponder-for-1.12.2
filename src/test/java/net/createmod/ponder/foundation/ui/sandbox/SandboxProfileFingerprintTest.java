package net.createmod.ponder.foundation.ui.sandbox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SandboxProfileFingerprintTest {
    @TempDir Path root;

    @Test
    void excludesHostWorldHistoryFromFingerprintAndWorkerCopyWhileKeepingGlobalSettings() throws Exception {
        Path game = Files.createDirectory(root.resolve("game"));
        SandboxProfile profile = new SandboxProfile(game, root, List.of(), "1.12.2", "en_us", 1, false, 800, 600, "");
        String original = profile.fingerprint();
        String firstWorld = "# Configuration file\n~CONFIG_VERSION: 0.1.0\nworld-42 {\n S:filterText=@app\n}\n";
        Path settings = write(game, "config/jei/worldSettings.cfg", firstWorld.getBytes(StandardCharsets.UTF_8));
        Path menu = write(game, "fancymenu_data/last_world.fmdata", "world = First".getBytes(StandardCharsets.UTF_8));
        assertEquals(original, profile.fingerprint());
        Files.writeString(settings, firstWorld + "world123 {\n B:overlayEnabled=true\n}\n");
        Files.writeString(menu, "world = Second");
        assertEquals(original, profile.fingerprint());
        var copyTree = SandboxProfile.class.getDeclaredMethod("copyTree", Path.class, Path.class);
        copyTree.setAccessible(true);
        Path isolated = root.resolve("isolated");
        for (String folder : List.of("config", "fancymenu_data"))
            copyTree.invoke(null, game.resolve(folder), isolated.resolve(folder));
        assertFalse(Files.exists(isolated.resolve(game.relativize(settings))));
        assertFalse(Files.exists(isolated.resolve(game.relativize(menu))));
        String mixed = firstWorld + "general {\n B:showIcons=true\n}\n";
        Files.writeString(settings, mixed);
        assertNotEquals(original, profile.fingerprint());
        copyTree.invoke(null, game.resolve("config"), isolated.resolve("config"));
        assertEquals(mixed, Files.readString(isolated.resolve(game.relativize(settings))));
    }

    @Test
    void generatedCommentsAndBinaryCachesDoNotInvalidateRecordings() throws Exception {
        Path game = Files.createDirectory(root.resolve("game"));
        SandboxProfile profile = new SandboxProfile(game, root, List.of(), "1.12.2", "en_us", 1, false, 800, 600, "");
        String[][] configs = {
            {"IC2.ini", "; created Monday", "rubberTree = true"},
            {"splash.properties", "! today", "textColor=0xABFFFF"},
            {"InvTweaks/InvTweaks.cfg", "#Wed Nov 25 16:55:38 CST 2026", "autoRefill=true"},
            {"astralsorcery/amulet_enchantments.cfg", "    # [default: generated registry order]", "S:data <\n    # literal list entry\n>"},
            {"continued.properties", "# generated", "name=line\\\n#value\nraw=é"}
        };
        for (String[] cfg : configs) write(game, "config/" + cfg[0],
            (cfg[1] + "\r\n" + cfg[2]).getBytes(StandardCharsets.ISO_8859_1));
        String original = profile.fingerprint();
        for (String[] cfg : configs) write(game, "config/" + cfg[0],
            (cfg[1] + " changed length\n" + cfg[2]).getBytes(StandardCharsets.ISO_8859_1));
        for (String file : List.of("stellarcore_blockstate_cache_a71.dat", "tconstruct_oredictcache.dat"))
            write(game, "config/" + file, new byte[]{1, 0, -1});
        assertEquals(original, profile.fingerprint());
        for (String[] cfg : configs) {
            Path file = game.resolve("config").resolve(cfg[0]);
            byte[] before = Files.readAllBytes(file);
            String changed = cfg[2].replace("#value", "#other").replace("# literal", "# other");
            if (changed.equals(cfg[2])) changed += "2";
            Files.writeString(file, changed, StandardCharsets.ISO_8859_1);
            assertNotEquals(original, profile.fingerprint(), cfg[0]);
            Files.write(file, before);
        }
        assertEquals(original, profile.fingerprint());
    }

    @Test
    void sceneEditsAreIndependentButMachineConfigurationAndPackContentsInvalidateTheEnvironment() throws Exception {
        Path game = Files.createDirectory(root.resolve("game"));
        Path scene = write(game, "config/ponder/ponder_demo/second.json", new byte[]{'{', '}'});
        byte[] configBytes = {65, 61, 49, 13, 10};
        Path machine = write(game, "config/ae2.cfg", configBytes);
        Path pack = game.resolve("resourcepacks/Example");
        byte[] textureBytes = {0, 1, 2, -1};
        Path texture = write(pack, "assets/example/textures/gui/machine.png", textureBytes);
        SandboxProfile profile = new SandboxProfile(game, root.resolve("assets"),
            List.of(new SandboxProfile.Pack(pack, "Example")), "1.12.2", "zh_cn", 2, false, 1280, 720, "");

        String original = profile.fingerprint();
        Path script = write(game, "scripts/ponder/packs/example/tutorial.json", new byte[]{'{', '}'});
        assertEquals(original, profile.fingerprint());
        Files.writeString(script, "{\"title\":\"另一场景\"}");
        Files.delete(scene);
        assertEquals(original, profile.fingerprint());
        Files.write(machine, new byte[]{65, 61, 50, 13, 10});
        assertNotEquals(original, profile.fingerprint());
        Files.write(machine, configBytes);
        assertEquals(original, profile.fingerprint());
        Files.write(texture, new byte[]{0, 9, 2, -1});
        assertNotEquals(original, profile.fingerprint());
        Files.write(texture, textureBytes);
        assertEquals(original, profile.fingerprint());

        var copyTree = SandboxProfile.class.getDeclaredMethod("copyTree", Path.class, Path.class);
        copyTree.setAccessible(true);
        Path copied = root.resolve("isolated");
        for (String folder : List.of("config", "scripts", "resourcepacks"))
            copyTree.invoke(null, game.resolve(folder), copied.resolve(folder));
        for (Path file : List.of(script, machine, texture)) {
            Path copy = copied.resolve(game.relativize(file));
            assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(copy));
            assertFalse(Files.isSameFile(file, copy));
        }
        assertArrayEquals(configBytes, Files.readAllBytes(machine));
        assertArrayEquals(textureBytes, Files.readAllBytes(texture));
        assertEquals("{\"title\":\"另一场景\"}", Files.readString(script));
    }

    private static Path write(Path base, String relative, byte[] bytes) throws Exception {
        Path file = base.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.write(file, bytes);
    }
}
