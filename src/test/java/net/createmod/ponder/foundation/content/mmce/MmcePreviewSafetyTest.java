package net.createmod.ponder.foundation.content.mmce;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MmcePreviewSafetyTest {

    @Test
    void exactBaseClassMatches() {
        assertTrue(MmcePreviewSafety.isClassOrSubclass(
            BlockController.class, BlockController.class.getName()));
    }

    @Test
    void factoryAndAddonSubclassesMatch() {
        String base = BlockController.class.getName();
        assertTrue(MmcePreviewSafety.isClassOrSubclass(BlockFactoryController.class, base));
        assertTrue(MmcePreviewSafety.isClassOrSubclass(AddonFactoryController.class, base));
    }

    @Test
    void unrelatedClassDoesNotMatch() {
        assertFalse(MmcePreviewSafety.isClassOrSubclass(
            OrdinaryBlock.class, BlockController.class.getName()));
        assertFalse(MmcePreviewSafety.isClassOrSubclass(
            BlockController.class, BlockFactoryController.class.getName()));
    }

    @Test
    void matchingRequiresQualifiedClassName() {
        assertFalse(MmcePreviewSafety.isClassOrSubclass(
            BlockController.class, BlockController.class.getSimpleName()));
        assertFalse(MmcePreviewSafety.isNativeControllerBlock(BlockController.class));
        assertFalse(MmcePreviewSafety.isNativeControllerBlock(BlockFactoryController.class));
    }

    @Test
    void missingOptionalTypeIsSafe() {
        assertFalse(MmcePreviewSafety.isNativeControllerBlock(null));
        assertFalse(MmcePreviewSafety.isNativeControllerBlock(OrdinaryBlock.class));
    }

    private static class BlockController {
    }

    private static class BlockFactoryController extends BlockController {
    }

    private static class AddonFactoryController extends BlockFactoryController {
    }

    private static class OrdinaryBlock {
    }
}
