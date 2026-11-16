package net.createmod.ponder.compat.mmce;

import java.util.function.Function;

/**
 * The optional API is resolved only for the exact locally audited versions.
 */
final class MmceScriptReloadApi {

    private static final String PRE = "youyihj.zenutils.api.reload.ScriptReloadEvent$Pre";
    private static final String POST = "youyihj.zenutils.api.reload.ScriptReloadEvent$Post";
    private final Class<?> preType;
    private final Class<?> postType;

    MmceScriptReloadApi(Class<?> preType, Class<?> postType) {
        if (preType == postType) {
            throw new IllegalArgumentException("Reload start and completion must use different event types");
        }
        this.preType = preType;
        this.postType = postType;
    }

    static MmceScriptReloadApi resolve(boolean physicalClient, Function<String, String> modVersion,
        ClassLoader classLoader) throws ReflectiveOperationException {
        if (!physicalClient
            || !"2.3.2".equals(modVersion.apply("modularmachinery"))
            || !"1.28.7".equals(modVersion.apply("zenutils"))
            || modVersion.apply("crafttweaker") == null) {
            return null;
        }
        Class<?> pre = Class.forName(PRE, false, classLoader);
        Class<?> post = Class.forName(POST, false, classLoader);
        return new MmceScriptReloadApi(pre, post);
    }

    boolean usesEventBase(Class<?> eventBase) {
        return eventBase.isAssignableFrom(preType) && eventBase.isAssignableFrom(postType);
    }

    boolean isPre(Object event) {
        return event != null && event.getClass() == preType;
    }

    boolean isPost(Object event) {
        return event != null && event.getClass() == postType;
    }

}
