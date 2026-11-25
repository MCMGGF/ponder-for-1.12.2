package net.createmod.ponder.foundation.ui.sandbox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SandboxProfileFingerprintTest {
    @TempDir Path root;

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
