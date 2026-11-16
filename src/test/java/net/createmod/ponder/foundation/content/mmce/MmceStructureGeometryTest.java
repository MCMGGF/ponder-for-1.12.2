package net.createmod.ponder.foundation.content.mmce;

import java.util.Arrays;
import java.util.Collections;

public final class MmceStructureGeometryTest {

    private static int assertions;

    public static void main(String[] args) {
        MmceStructureGeometry.Bounds negative = MmceStructureGeometry.bounds(
            Arrays.asList(new int[] { -4, -3, -8 }, new int[] { 2, 6, 1 }));
        check(negative.originX == 4 && negative.originY == 3 && negative.originZ == 8, "Negative origin");
        check(negative.sizeX == 7 && negative.sizeY == 10 && negative.sizeZ == 10, "Inclusive dimensions");
        for (int x = -4; x <= 2; x++) {
            for (int y = -3; y <= 6; y++) {
                for (int z = -8; z <= 1; z++) {
                    int[] transformed = negative.toPreview(x, y, z);
                    check(transformed[0] >= 0 && transformed[1] >= 0 && transformed[2] >= 0,
                        "Negative preview position");
                    check(transformed[0] < negative.sizeX && transformed[1] < negative.sizeY
                        && transformed[2] < negative.sizeZ, "Preview escaped dimensions");
                    check(Arrays.equals(new int[] { x, y, z },
                        negative.toOriginal(transformed[0], transformed[1], transformed[2])), "Round-trip changed shape");
                }
            }
        }
        MmceStructureGeometry.Bounds positive = MmceStructureGeometry.bounds(
            Collections.singletonList(new int[] { 4, 5, 6 }));
        check(positive.originX == 0 && positive.originY == 0 && positive.originZ == 0, "Lost controller origin");
        check(positive.sizeX == 5 && positive.sizeY == 6 && positive.sizeZ == 7, "Origin excluded");
        MmceStructureGeometry.Bounds tall = MmceStructureGeometry.bounds(
            Arrays.asList(new int[] { 0, -100, 0 }, new int[] { 0, 155, 0 }));
        check(tall.sizeY == 256, "Valid tall structure rejected");
        rejects(() -> MmceStructureGeometry.bounds(Collections.singletonList(new int[] { 0, 256, 0 })));
        rejects(() -> MmceStructureGeometry.bounds(Collections.singletonList(new int[] { Integer.MIN_VALUE, 0, 0 })));
        rejects(() -> MmceStructureGeometry.bounds(Collections.singletonList(new int[] { Integer.MAX_VALUE, 0, 0 })));
        rejects(() -> MmceStructureGeometry.bounds(Collections.singletonList(new int[] { 1, 2 })));
        rejects(() -> MmcePreviewLimits.checkBlockCount(8193));
        rejects(() -> MmcePreviewLimits.checkBlockCount(-1));
        check(MmcePreviewLimits.expansionUpperBound(20, 10, 4, 7) == 67, "Dynamic count wrong");
        rejects(() -> MmcePreviewLimits.expansionUpperBound(10, 8192, 8192, 10));
        rejects(() -> MmcePreviewLimits.expansionUpperBound(10, 2, -1, 10));
        rejects(() -> MmcePreviewLimits.checkedExtent(Integer.MIN_VALUE, Integer.MAX_VALUE));
        System.out.println("MmceStructureGeometryTest: " + assertions + " assertions passed");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void rejects(Runnable operation) {
        assertions++;
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Invalid structure data was accepted");
    }
}
