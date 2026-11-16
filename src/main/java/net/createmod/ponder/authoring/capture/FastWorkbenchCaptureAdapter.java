package net.createmod.ponder.authoring.capture;

import java.io.File;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

/** Optional exact read-only adapter; never constructs or invokes FastWorkbench objects. */
final class FastWorkbenchCaptureAdapter {
    static final String GUI = "shadows.fastbench.gui.GuiFastBench";
    static final String CLIENT = "shadows.fastbench.gui.ClientContainerFastBench";
    static final String BASE = "shadows.fastbench.gui.ContainerFastBench";
    static final String OUTPUT = "shadows.fastbench.gui.SlotCraftingSucks";
    static final String VERSION = "1.7.4";
    static final String JAR_SHA256 = "DB86C2C728C148FF20E4349FA18767853326EC78E6581AFB0C38954FE2ABF42A";
    static final String SOURCE_GUI_ID = "fastbench:workbench";
    static final String ADAPTER_ID = "fastbench_1_7_4";
    private static Access cached;
    private FastWorkbenchCaptureAdapter() { }

    static boolean supports(GuiScreen screen, Container container) {
        Access access = resolve(container);
        return access != null && screen != null && screen.getClass() == access.gui;
    }

    static synchronized Access resolve(Container container) {
        if (container == null || !CLIENT.equals(container.getClass().getName())) return null;
        try {
            ModContainer mod = Loader.instance().getIndexedModList().get("fastbench");
            if (mod == null || !VERSION.equals(mod.getVersion())) return null;
            File source = mod.getSource().getCanonicalFile();
            if (cached != null && cached.client == container.getClass() && cached.mod == mod
                && cached.source.equals(source) && cached.bytes == source.length() && cached.modified == source.lastModified()) return cached;
            Class<?> client = container.getClass(), base = client.getSuperclass();
            if (base == null || !BASE.equals(base.getName()) || base.getSuperclass() != ContainerWorkbench.class) return null;
            ClassLoader loader = client.getClassLoader();
            Class<?> gui = Class.forName(GUI, false, loader), output = Class.forName(OUTPUT, false, loader);
            if (gui.getSuperclass() != GuiCrafting.class || output.getSuperclass() != SlotCrafting.class) return null;
            Path path = source.toPath();
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path) || Files.size(path) != 26635) return null;
            for (Class<?> type : new Class<?>[] {gui, client, base, output}) {
                if (!source.equals(codeSource(type)) || !source.equals(localSource(
                    type.getResource("/" + type.getName().replace('.', '/') + ".class"), type.getName()))) return null;
            }
            String hash = sha256(path);
            if (!exactPolicy(gui.getName(), client.getName(), base.getName(), output.getName(), mod.getVersion(), hash, true)) return null;
            cached = new Access(gui, client, output, mod, source, hash);
            return cached;
        } catch (Exception | LinkageError unsupported) { return null; }
    }

    static boolean exactPolicy(String gui, String client, String base, String output, String version, String hash, boolean sameSource) {
        return GUI.equals(gui) && CLIENT.equals(client) && BASE.equals(base) && OUTPUT.equals(output)
            && VERSION.equals(version) && JAR_SHA256.equalsIgnoreCase(hash) && sameSource;
    }

    static final class Access {
        private final Class<?> gui, client, output;
        private final ModContainer mod;
        private final File source;
        private final long bytes, modified;
        private final Map<String,String> metadata;
        private Access(Class<?> gui, Class<?> client, Class<?> output, ModContainer mod, File source, String hash) {
            this.gui=gui; this.client=client; this.output=output; this.mod=mod; this.source=source;
            bytes=source.length(); modified=source.lastModified();
            Map<String,String> values=new LinkedHashMap<>();
            values.put("sourceAdapter",ADAPTER_ID); values.put("sourceModId","fastbench"); values.put("sourceModVersion",mod.getVersion());
            values.put("sourceJarSha256",hash); values.put("sourceGuiClass",GUI); values.put("sourceContainerClass",CLIENT);
            metadata=Collections.unmodifiableMap(values);
        }
        boolean outputSlot(Slot slot) { return slot != null && slot.getClass() == output; }
        Map<String,String> metadata() { return metadata; }
    }

    private static File codeSource(Class<?> type) throws Exception {
        URL url=type.getProtectionDomain()==null || type.getProtectionDomain().getCodeSource()==null
            ? null : type.getProtectionDomain().getCodeSource().getLocation();
        return localSource(url, type.getName());
    }
    static File localSource(URL url, String className) throws Exception {
        if (url == null || className == null || url.getRef() != null || url.getQuery() != null) return null;
        if ("jar".equals(url.getProtocol())) {
            // Cleanroom uses the class resource URL itself as CodeSource.
            java.net.URLConnection connection = url.openConnection();
            if (!(connection instanceof JarURLConnection)) return null;
            JarURLConnection jar = (JarURLConnection) connection;
            String entry = jar.getEntryName();
            if (entry != null && !entry.isEmpty() && !entry.equals(className.replace('.', '/') + ".class")) return null;
            url = jar.getJarFileURL();
        }
        if (!"file".equals(url.getProtocol()) || url.getRef() != null || url.getQuery() != null
            || url.getUserInfo() != null || url.getPort() != -1 || !url.getHost().isEmpty()) return null;
        return Paths.get(url.toURI()).toFile().getCanonicalFile();
    }
    private static String sha256(Path path) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try (InputStream stream=Files.newInputStream(path)) { byte[] bytes=new byte[8192]; int count; while((count=stream.read(bytes))!=-1) digest.update(bytes,0,count); }
        StringBuilder hash=new StringBuilder(); for(byte value:digest.digest()) hash.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
        return hash.toString();
    }
}
