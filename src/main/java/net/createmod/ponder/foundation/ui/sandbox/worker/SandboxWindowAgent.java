package net.createmod.ponder.foundation.ui.sandbox.worker;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.security.ProtectionDomain;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/** Child-JVM premain runs before GLFW and Cleanroom create a window, including parent-loaded LWJGL. */
public final class SandboxWindowAgent implements ClassFileTransformer, CodeTransform {
    public static Path create(Path job) throws IOException {
        Path file = job.resolve("worker-window.jar");
        writeAgent(file, null);
        return file.toAbsolutePath();
    }

    private static void writeAgent(Path file, String classpath) throws IOException {
        String name = SandboxWindowAgent.class.getName();
        String entry = name.replace('.', '/') + ".class";
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Premain-Class", name);
        if (classpath != null) manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classpath);
        try (InputStream bytes = SandboxWindowAgent.class.getResourceAsStream("/" + entry);
             JarOutputStream jar = new JarOutputStream(Files.newOutputStream(file), manifest)) {
            if (bytes == null) throw new IOException("Missing sandbox window agent class");
            jar.putNextEntry(new JarEntry(entry));
            bytes.transferTo(jar);
            jar.closeEntry();
        }
    }

    public static void premain(String args, Instrumentation instrumentation) {
        if (System.getProperty("ponder.sandbox.job") == null)
            throw new IllegalStateException("Sandbox window agent requires a worker job");
        // The private world and its JVM have the same lifetime as the client that submitted the job.
        Long parent = Long.getLong("ponder.sandbox.parent");
        if (parent != null) {
            ProcessHandle owner = ProcessHandle.of(parent).orElseThrow(() -> new IllegalStateException("Recorder host exited"));
            owner.onExit().thenRun(() -> Runtime.getRuntime().halt(0));
        }
        instrumentation.addTransformer(new SandboxWindowAgent());
    }

    /** Windows javaLauncher's argv code page cannot carry every NTFS name. Keep paths inside Java. */
    public static Path prepareUnicodeLaunch(Path job, List<String> args, String main, Charset encoding) throws IOException {
        int classpathIndex = args.indexOf("-cp") + 1;
        String classpath = args.get(classpathIndex);
        Properties properties = new Properties();
        properties.setProperty("classpath", classpath);
        properties.setProperty("main", main);
        int mainIndex = args.indexOf(main);
        List<String> applicationArgs = args.subList(mainIndex + 1, args.size());
        properties.setProperty("count", Integer.toString(applicationArgs.size()));
        for (int i = 0; i < applicationArgs.size(); i++) properties.setProperty("arg." + i, applicationArgs.get(i));
        try (var output = Files.newOutputStream(job.resolve("worker-launch.properties"))) {
            properties.store(output, null);
        }
        String urls = Arrays.stream(classpath.split(Pattern.quote(File.pathSeparator)))
            .map(path -> Path.of(path).toUri().toASCIIString()).collect(Collectors.joining(" "));
        args.subList(mainIndex, args.size()).clear();
        args.add(SandboxWindowAgent.class.getName());
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            String prefix = "-Djava.library.path=";
            if (!arg.startsWith(prefix) || encoding.newEncoder().canEncode(arg)) continue;
            List<String> nativeDirs = new ArrayList<>();
            for (String directory : arg.substring(prefix.length()).split(Pattern.quote(File.pathSeparator))) {
                if (encoding.newEncoder().canEncode(directory)) { nativeDirs.add(directory); continue; }
                if (!Files.isDirectory(Path.of(directory))) continue;
                String alias = "natives-" + nativeDirs.size();
                Path copy = Files.createDirectory(job.resolve(alias));
                try (var files = Files.list(Path.of(directory))) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) Files.copy(file, copy.resolve(file.getFileName()));
                }
                nativeDirs.add("../" + alias);
            }
            args.set(i, prefix + String.join(File.pathSeparator, nativeDirs));
        }
        // The Windows native instrumentation loader also expands relative paths through its ANSI code page.
        Path temporary = null;
        Path agent = job.resolve("worker-window.jar");
        if (!encoding.newEncoder().canEncode(agent.toAbsolutePath().toString())) {
            Path directory = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();
            if (!encoding.newEncoder().canEncode(directory.toString())) {
                directory = job.toAbsolutePath().getParent();
                while (directory != null && (!encoding.newEncoder().canEncode(directory.toString()) || !Files.isWritable(directory)))
                    directory = directory.getParent();
                if (directory == null) throw new IOException("No writable native-encoding directory for the Java window agent");
            }
            agent = temporary = Files.createTempFile(directory, "ponder-worker-", ".jar");
            temporary.toFile().deleteOnExit();
        }
        try {
            writeAgent(agent, urls);
            args.set(classpathIndex, agent.toAbsolutePath().toString());
            args.set(args.indexOf("-javaagent:../worker-window.jar"), "-javaagent:" + agent.toAbsolutePath());
            return temporary;
        } catch (IOException | RuntimeException failure) {
            if (temporary != null) Files.deleteIfExists(temporary);
            throw failure;
        }
    }

    public static void main(String[] ignored) throws Exception {
        Properties launch = new Properties();
        try (var input = Files.newInputStream(Path.of("../worker-launch.properties"))) { launch.load(input); }
        System.setProperty("java.class.path", launch.getProperty("classpath"));
        String[] args = new String[Integer.parseInt(launch.getProperty("count"))];
        for (int i = 0; i < args.length; i++) args[i] = launch.getProperty("arg." + i);
        Class.forName(launch.getProperty("main")).getMethod("main", String[].class).invoke(null, (Object) args);
    }

    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> existing, ProtectionDomain domain, byte[] bytes) {
        if (!"org/lwjgl/glfw/GLFW".equals(name)) return null;
        ClassFile format = ClassFile.of();
        return format.transformClass(format.parse(bytes),
            ClassTransform.transformingMethodBodies(method -> method.methodName().equalsString("glfwCreateWindow"), this));
    }

    @Override
    public void atStart(CodeBuilder code) {
        // LWJGLXX sets GLFW_VISIBLE true just before calling GLFW. The native call must see these last.
        ClassDesc glfw = ClassDesc.of("org.lwjgl.glfw.GLFW");
        MethodTypeDesc hint = MethodTypeDesc.ofDescriptor("(II)V");
        code.loadConstant(0x00020004).iconst_0().invokestatic(glfw, "glfwWindowHint", hint); // GLFW_VISIBLE
        code.loadConstant(0x00020001).iconst_0().invokestatic(glfw, "glfwWindowHint", hint); // GLFW_FOCUSED
    }

    @Override
    public void accept(CodeBuilder builder, CodeElement element) { builder.with(element); }
}
