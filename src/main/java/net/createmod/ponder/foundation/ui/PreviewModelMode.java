package net.createmod.ponder.foundation.ui;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** A screen owns one explicit model request, including a failed result. */
final class PreviewModelMode<T> {

    private T preview;
    private boolean attempted;
    private boolean appearance;
    private boolean initialFit;
    private boolean renderFailed;
    private String note = "";

    boolean chooseAppearance(Supplier<T> loader, Predicate<T> available, Function<T, String> describe) {
        if (!attempted) {
            attempted = true;
            preview = loader.get();
            note = describe.apply(preview);
        }
        appearance = !renderFailed && preview != null && available.test(preview);
        return appearance;
    }

    void chooseStructure() {
        appearance = false;
    }

    void fail(String reason) {
        attempted = true;
        appearance = false;
        renderFailed = true;
        note = reason;
    }

    boolean invalidate(boolean current) {
        if (!attempted || current) {
            return false;
        }
        preview = null;
        attempted = false;
        appearance = false;
        renderFailed = false;
        note = "\u8d44\u6e90\u6216\u7d22\u5f15\u5df2\u66f4\u65b0\uff0c\u8bf7\u91cd\u65b0\u9009\u62e9\u5916\u89c2";
        return true;
    }

    boolean claimInitialFit() {
        if (!appearance || initialFit) {
            return false;
        }
        initialFit = true;
        return true;
    }

    T getPreview() {
        return preview;
    }

    boolean isAppearance() {
        return appearance;
    }

    boolean wasAttempted() {
        return attempted;
    }

    String getNote() {
        return note;
    }

    void clear() {
        preview = null;
        attempted = false;
        appearance = false;
        initialFit = false;
        renderFailed = false;
        note = "";
    }
}
