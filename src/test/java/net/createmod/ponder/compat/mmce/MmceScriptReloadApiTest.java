package net.createmod.ponder.compat.mmce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class MmceScriptReloadApiTest {

    @Test
    void physicalServerNeverLooksUpModsOrOptionalClasses() throws ReflectiveOperationException {
        CountingClassLoader loader = new CountingClassLoader();
        assertNull(MmceScriptReloadApi.resolve(false, id -> {
            throw new AssertionError("Server must not resolve client mod versions");
        }, loader));
        assertEquals(0, loader.loads.get());
    }

    @Test
    void everyRequiredModMustBePresentBeforeOptionalClassLoading() throws ReflectiveOperationException {
        for (String missing : new String[] {"modularmachinery", "zenutils", "crafttweaker"}) {
            Map<String, String> versions = supportedVersions();
            versions.remove(missing);
            CountingClassLoader loader = new CountingClassLoader();
            assertNull(MmceScriptReloadApi.resolve(true, versions::get, loader), missing);
            assertEquals(0, loader.loads.get(), missing);
        }
    }

    @Test
    void unknownMmceAndZenUtilsVersionsRemainIsolated() throws ReflectiveOperationException {
        for (String changed : new String[] {"modularmachinery", "zenutils"}) {
            for (String version : new String[] {"", "unknown", "2.3.3", "1.28.8", "1.28.7-custom"}) {
                Map<String, String> versions = supportedVersions();
                versions.put(changed, version);
                CountingClassLoader loader = new CountingClassLoader();
                assertNull(MmceScriptReloadApi.resolve(true, versions::get, loader));
                assertEquals(0, loader.loads.get());
            }
        }
    }

    @Test
    void supportedVersionsResolveOnlyTheExpectedOptionalEventApi() {
        Map<String, String> versions = supportedVersions();
        CountingClassLoader loader = new CountingClassLoader();
        ClassNotFoundException failure = assertThrows(ClassNotFoundException.class,
            () -> MmceScriptReloadApi.resolve(true, versions::get, loader));
        assertEquals("youyihj.zenutils.api.reload.ScriptReloadEvent$Pre", failure.getMessage());
        assertEquals(1, loader.loads.get());
    }

    @Test
    void onlyExactReloadEventClassesAreRecognized() throws ReflectiveOperationException {
        MmceScriptReloadApi api = api();
        assertTrue(api.isPre(new Pre()));
        assertFalse(api.isPost(new Pre()));
        assertTrue(api.isPost(new Post()));
        assertFalse(api.isPre(new Post()));
        assertFalse(api.isPost(new StartupPost()));
        assertFalse(api.isPre(new DerivedPre()));
        assertFalse(api.isPre(null));
        assertFalse(api.isPost(null));
        assertTrue(api.usesEventBase(TestEvent.class));
        assertFalse(api.usesEventBase(String.class));
    }

    @Test
    void preAndPostRequireDifferentEventClasses() {
        assertThrows(IllegalArgumentException.class, () -> new MmceScriptReloadApi(Pre.class, Pre.class));
    }

    @Test
    void optionalClassLoadingDoesNotSwallowVirtualMachineErrors() {
        OutOfMemoryError failure = new OutOfMemoryError("Injected VM failure");
        ClassLoader loader = new ClassLoader() {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) {
                throw failure;
            }
        };
        assertSame(failure, assertThrows(OutOfMemoryError.class,
            () -> MmceScriptReloadApi.resolve(true, supportedVersions()::get, loader)));
    }

    @Test
    void optionalClassLoadingDoesNotSwallowThreadDeath() {
        ThreadDeath failure = new ThreadDeath();
        ClassLoader loader = new ClassLoader() {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) {
                throw failure;
            }
        };
        assertSame(failure, assertThrows(ThreadDeath.class,
            () -> MmceScriptReloadApi.resolve(true, supportedVersions()::get, loader)));
    }

    private static MmceScriptReloadApi api() {
        return new MmceScriptReloadApi(Pre.class, Post.class);
    }

    private static Map<String, String> supportedVersions() {
        Map<String, String> result = new HashMap<String, String>();
        result.put("modularmachinery", "2.3.2");
        result.put("zenutils", "1.28.7");
        result.put("crafttweaker", "4.1.20.699");
        return result;
    }

    private static final class CountingClassLoader extends ClassLoader {
        private final AtomicInteger loads = new AtomicInteger();

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            loads.incrementAndGet();
            throw new ClassNotFoundException(name);
        }
    }

    private static class TestEvent { }

    private static class Pre extends TestEvent { }

    private static final class Post extends TestEvent { }

    private static final class StartupPost extends TestEvent { }

    private static final class DerivedPre extends Pre { }
}
