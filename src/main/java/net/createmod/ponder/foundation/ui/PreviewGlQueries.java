package net.createmod.ponder.foundation.ui;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.opengl.GL11;

/** LWJGL 2 and 3 use different Java names for the same vector queries. */
final class PreviewGlQueries {

    static final int VECTOR_CAPACITY = 16;
    private final Method integerVector;
    private final Method floatVector;

    PreviewGlQueries(Class<?> api) {
        integerVector = resolve(api, "glGetInteger", "glGetIntegerv", IntBuffer.class);
        floatVector = resolve(api, "glGetFloat", "glGetFloatv", FloatBuffer.class);
    }

    static PreviewGlQueries runtime() {
        return RuntimeQueries.INSTANCE;
    }

    static PreviewGlQueries forRuntime(ClassLookup lookup, Class<?> nativeApi) {
        Class<?> api;
        try {
            // 反射调用不会经过 Actinium 的字节码重定向，必须显式读取模拟状态。
            api = lookup.load("com.gtnewhorizons.angelica.glsm.GLStateManager");
        } catch (ClassNotFoundException absent) {
            api = nativeApi;
        }

        return new PreviewGlQueries(api);
    }

    interface ClassLookup {
        Class<?> load(String name) throws ClassNotFoundException;
    }

    private static final class RuntimeQueries {
        private static final PreviewGlQueries INSTANCE = forRuntime(
            name -> Class.forName(name, false, PreviewGlQueries.class.getClassLoader()), GL11.class);
    }

    void integers(int parameter, IntBuffer destination) {
        requireCapacity(destination.remaining());
        invoke(integerVector, parameter, destination);
    }

    void floats(int parameter, FloatBuffer destination) {
        requireCapacity(destination.remaining());
        invoke(floatVector, parameter, destination);
    }

    private static void requireCapacity(int remaining) {
        // LWJGL 2 checks sixteen entries even for a four-component GL value.
        if (remaining < VECTOR_CAPACITY) {
            throw new IllegalArgumentException("OpenGL vector queries require at least sixteen buffer entries");
        }
    }

    private static Method resolve(Class<?> api, String legacy, String modern, Class<?> buffer) {
        try {
            return api.getMethod(legacy, int.class, buffer);
        } catch (NoSuchMethodException absent) {
            try {
                return api.getMethod(modern, int.class, buffer);
            } catch (NoSuchMethodException failure) {
                throw new IllegalStateException("OpenGL vector query unavailable: " + legacy + " / " + modern,
                    failure);
            }
        }
    }

    private static void invoke(Method query, int parameter, Object destination) {
        try {
            query.invoke(null, Integer.valueOf(parameter), destination);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("OpenGL vector query failed: " + query.getName(), cause);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("OpenGL vector query inaccessible: " + query.getName(), failure);
        }
    }
}
