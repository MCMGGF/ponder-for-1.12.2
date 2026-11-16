package net.createmod.ponder.foundation.content.mmce;

/** Hard limits are checked before expanding or allocating a runtime preview. */
public final class MmcePreviewLimits {

    public static final int MAX_BLOCKS = 8192;
    public static final int MAX_AXIS_LENGTH = 256;
    public static final int MAX_ALTERNATIVES_PER_POSITION = 4096;
    public static final int MAX_TOTAL_ALTERNATIVES = 262144;
    public static final int MAX_CACHED_STRUCTURES = 8;
    public static final long MAX_BUILD_NANOS = 1500000000L;

    private MmcePreviewLimits() {
    }

    public static void checkBlockCount(long count) {
        if (count < 0 || count > MAX_BLOCKS) {
            throw new IllegalArgumentException("结构位置数量 " + count + " 超过预览限制 " + MAX_BLOCKS);
        }
    }

    public static int checkedExtent(int minimum, int maximum) {
        long extent = (long) maximum - minimum + 1L;
        if (extent < 1 || extent > MAX_AXIS_LENGTH) {
            throw new IllegalArgumentException("结构轴长度 " + extent + " 超过预览限制 " + MAX_AXIS_LENGTH);
        }
        return (int) extent;
    }

    public static long expansionUpperBound(long base, long repeated, long repetitions, long end) {
        if (base < 0 || repeated < 0 || repetitions < 0 || end < 0) {
            throw new IllegalArgumentException("动态结构包含负数尺寸");
        }
        if (repeated > MAX_BLOCKS || repetitions > MAX_BLOCKS || end > MAX_BLOCKS) {
            throw new IllegalArgumentException("动态结构超过预览限制");
        }
        long count = base + repeated * repetitions + end;
        checkBlockCount(count);
        return count;
    }
}
