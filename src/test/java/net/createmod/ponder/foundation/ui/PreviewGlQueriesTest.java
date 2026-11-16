package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.junit.jupiter.api.Test;

class PreviewGlQueriesTest {

    @Test
    void runtimeReadsEmulatedStateInsteadOfTheNativeDriverState() {
        PreviewGlQueries queries = PreviewGlQueries.forRuntime(name -> {
            assertEquals("com.gtnewhorizons.angelica.glsm.GLStateManager", name);
            return Emulated.class;
        }, Legacy.class);
        FloatBuffer matrix = FloatBuffer.allocate(16);
        IntBuffer viewport = IntBuffer.allocate(16);
        queries.floats(3, matrix);
        queries.integers(7, viewport);
        assertEquals(103F, matrix.get(0));
        assertEquals(107, viewport.get(0));
    }

    @Test
    void runtimeUsesLegacyOrModernNativeQueriesOnlyWhenEmulationIsAbsent() {
        for (Class<?> api : new Class<?>[] { Legacy.class, Modern.class }) {
            assertQueries(PreviewGlQueries.forRuntime(name -> {
                throw new ClassNotFoundException(name);
            }, api));
        }
    }

    @Test
    void runtimeDoesNotHideAnIncompleteOrBrokenEmulationApi() {
        assertThrows(IllegalStateException.class,
            () -> PreviewGlQueries.forRuntime(name -> ScalarOnly.class, Legacy.class));
        PreviewGlQueries queries = PreviewGlQueries.forRuntime(name -> Failing.class, Legacy.class);
        assertSame(Failing.LINKAGE,
            assertThrows(UnsatisfiedLinkError.class, () -> queries.floats(3, FloatBuffer.allocate(16))));
        NoClassDefFoundError brokenDependency = new NoClassDefFoundError("emulation dependency");
        assertSame(brokenDependency, assertThrows(NoClassDefFoundError.class,
            () -> PreviewGlQueries.forRuntime(name -> { throw brokenDependency; }, Legacy.class)));
    }

    public static final class Emulated {
        public static void glGetInteger(int parameter, IntBuffer destination) {
            for (int i = 0; i < 16; i++) {
                destination.put(i, 100 + parameter + i);
            }
        }

        public static void glGetFloat(int parameter, FloatBuffer destination) {
            for (int i = 0; i < 16; i++) {
                destination.put(i, 100F + parameter + i);
            }
        }
    }

    @Test
    void readsLegacyLwjgl2VectorMethods() {
        assertQueries(new PreviewGlQueries(Legacy.class));
    }

    @Test
    void readsLwjgl3VectorNamesWithoutLinkingLegacyOverloads() {
        assertQueries(new PreviewGlQueries(Modern.class));
    }

    @Test
    void reportsAnUnavailableVectorApiInsteadOfAcceptingScalarQueries() {
        IllegalStateException failure =
            assertThrows(IllegalStateException.class, () -> new PreviewGlQueries(ScalarOnly.class));
        assertTrue(failure.getMessage().contains("glGetInteger"));
        assertTrue(failure.getMessage().contains("glGetIntegerv"));
    }

    @Test
    void propagatesNativeRuntimeFailuresWithoutWrappingThemAsReflectionFailures() {
        PreviewGlQueries queries = new PreviewGlQueries(Failing.class);
        assertSame(Failing.RUNTIME,
            assertThrows(IllegalStateException.class, () -> queries.integers(1, IntBuffer.allocate(16))));
        assertSame(Failing.LINKAGE,
            assertThrows(UnsatisfiedLinkError.class, () -> queries.floats(1, FloatBuffer.allocate(16))));
    }

    @Test
    void enforcesTheLegacySixteenEntryMinimumForFourComponentValues() {
        PreviewGlQueries queries = new PreviewGlQueries(Legacy.class);
        assertThrows(IllegalArgumentException.class, () -> queries.integers(1, IntBuffer.allocate(4)));
        assertThrows(IllegalArgumentException.class, () -> queries.floats(1, FloatBuffer.allocate(4)));
        assertEquals(16, PreviewGlQueries.VECTOR_CAPACITY);
    }

    private static void assertQueries(PreviewGlQueries queries) {
        IntBuffer integers = IntBuffer.allocate(PreviewGlQueries.VECTOR_CAPACITY);
        FloatBuffer floats = FloatBuffer.allocate(PreviewGlQueries.VECTOR_CAPACITY);
        queries.integers(7, integers);
        queries.floats(3, floats);
        assertEquals(7, integers.get(0));
        assertEquals(10, integers.get(3));
        assertEquals(0.3F, floats.get(0), 0.00001F);
        assertEquals(0.6F, floats.get(3), 0.00001F);
        assertEquals(0, integers.position());
        assertEquals(0, floats.position());
    }

    public static final class Legacy {
        public static void glGetInteger(int parameter, IntBuffer destination) {
            assertTrue(destination.remaining() >= 16);
            for (int i = 0; i < 4; i++) destination.put(i, parameter + i);
        }

        public static void glGetFloat(int parameter, FloatBuffer destination) {
            assertTrue(destination.remaining() >= 16);
            for (int i = 0; i < 4; i++) destination.put(i, (parameter + i) / 10F);
        }
    }

    public static final class Modern {
        public static int glGetInteger(int parameter) {
            throw new AssertionError("scalar query must not be selected");
        }

        public static float glGetFloat(int parameter) {
            throw new AssertionError("scalar query must not be selected");
        }

        public static void glGetIntegerv(int parameter, IntBuffer destination) {
            Legacy.glGetInteger(parameter, destination);
        }

        public static void glGetFloatv(int parameter, FloatBuffer destination) {
            Legacy.glGetFloat(parameter, destination);
        }
    }

    public static final class ScalarOnly {
        public static int glGetInteger(int parameter) {
            return parameter;
        }
    }

    public static final class Failing {
        static final IllegalStateException RUNTIME = new IllegalStateException("no context");
        static final UnsatisfiedLinkError LINKAGE = new UnsatisfiedLinkError("test native failure");

        public static void glGetInteger(int parameter, IntBuffer destination) {
            throw RUNTIME;
        }

        public static void glGetFloat(int parameter, FloatBuffer destination) {
            throw LINKAGE;
        }
    }
}
