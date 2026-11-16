package net.createmod.ponder.foundation.content.mmce;

/** Keeps native MMCE controller lifecycles out of the detached preview world. */
public final class MmcePreviewSafety {

    private static final String CONTROLLER_BLOCK =
        "hellfirepvp.modularmachinery.common.block.BlockController";

    private MmcePreviewSafety() {
    }

    public static boolean isNativeControllerBlock(Class<?> blockClass) {
        // BlockFactoryController and addon controllers inherit BlockController.
        // Comparing loaded superclass names keeps MMCE an optional dependency.
        return isClassOrSubclass(blockClass, CONTROLLER_BLOCK);
    }

    static boolean isClassOrSubclass(Class<?> type, String baseClassName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (current.getName().equals(baseClassName)) {
                return true;
            }
        }
        return false;
    }
}
