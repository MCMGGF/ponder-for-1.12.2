package net.createmod.ponder.authoring.capture;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiFurnace;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

/** Exact optional GUI provenance only; vanilla container and inventory checks remain mandatory. */
final class HammerFurnaceCaptureAdapter {
    static final String GUI = "com.zeitheron.hammercore.client.gui.impl.smooth.GuiFurnaceSmooth";
    static final String CONTAINER = "net.minecraft.inventory.ContainerFurnace";
    static final String VERSION = "12.2.58";
    static final String JAR_SHA256 = "34E22EE309113239848AA2F44CC7CCBA5BDF285A4DB92031AFE70E744F3B940A";
    static final String SOURCE_GUI_ID = "hammercore:furnace_smooth";
    static final String ADAPTER_ID = "hammercore_12_2_58_furnace";
    private static Access cached;
    private HammerFurnaceCaptureAdapter() { }

    static boolean supports(GuiScreen screen, Container container) { return resolve(screen, container) != null; }

    static synchronized Access resolve(GuiScreen screen, Container container) {
        if (screen == null || container == null || !GUI.equals(screen.getClass().getName())
            || screen.getClass().getSuperclass() != GuiFurnace.class || container.getClass() != ContainerFurnace.class
            || ((GuiFurnace) screen).inventorySlots != container) return null;
        try {
            ModContainer mod = Loader.instance().getIndexedModList().get("hammercore");
            if (mod == null || !VERSION.equals(mod.getVersion())) return null;
            File source = mod.getSource().getCanonicalFile();
            if (cached != null && cached.gui == screen.getClass() && cached.mod == mod && cached.source.equals(source)
                && cached.bytes == source.length() && cached.modified == source.lastModified()) return cached;
            Path path = source.toPath();
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path) || Files.size(path) != 2591232) return null;
            Class<?> gui = screen.getClass();
            File codeSource = gui.getProtectionDomain() == null || gui.getProtectionDomain().getCodeSource() == null ? null
                : FastWorkbenchCaptureAdapter.localSource(gui.getProtectionDomain().getCodeSource().getLocation(), GUI);
            File resourceSource = FastWorkbenchCaptureAdapter.localSource(gui.getResource("/" + GUI.replace('.', '/') + ".class"), GUI);
            if (!source.equals(codeSource) || !source.equals(resourceSource)) return null;
            String hash = sha256(path);
            if (!exactPolicy(gui.getName(), gui.getSuperclass().getName(), container.getClass().getName(), mod.getVersion(), hash, true, true)) return null;
            cached = new Access(gui, mod, source, hash);
            return cached;
        } catch (Exception | LinkageError unsupported) { return null; }
    }

    static boolean exactPolicy(String gui, String base, String container, String version, String hash, boolean sameSource, boolean modLoaded) {
        return modLoaded && GUI.equals(gui) && GuiFurnace.class.getName().equals(base) && CONTAINER.equals(container)
            && VERSION.equals(version) && JAR_SHA256.equalsIgnoreCase(hash) && sameSource;
    }

    static final class Access {
        private final Class<?> gui;
        private final ModContainer mod;
        private final File source;
        private final long bytes, modified;
        private final Map<String, String> metadata;
        private Access(Class<?> gui, ModContainer mod, File source, String hash) {
            this.gui = gui; this.mod = mod; this.source = source; bytes = source.length(); modified = source.lastModified();
            Map<String, String> values = new LinkedHashMap<>();
            values.put("sourceAdapter", ADAPTER_ID); values.put("sourceModId", "hammercore"); values.put("sourceModVersion", mod.getVersion());
            values.put("sourceJarSha256", hash); values.put("sourceGuiClass", GUI); values.put("sourceContainerClass", CONTAINER);
            metadata = Collections.unmodifiableMap(values);
        }
        Map<String, String> metadata() { return metadata; }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = Files.newInputStream(path)) { byte[] bytes = new byte[8192]; int count; while ((count = stream.read(bytes)) != -1) digest.update(bytes, 0, count); }
        StringBuilder hash = new StringBuilder(); for (byte value : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return hash.toString();
    }
}
