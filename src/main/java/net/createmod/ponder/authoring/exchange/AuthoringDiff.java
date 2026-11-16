package net.createmod.ponder.authoring.exchange;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.createmod.ponder.authoring.AuthoringDocument;

/** Bounded, read-only JSON Pointer diff. Truncation is explicit, never reported as an unchanged document. */
public final class AuthoringDiff {
    public static final int MAX_DIFFS = 4096;
    public static final int MAX_OUTPUT_CHARS = 512 * 1024;
    private static final int MAX_PATH_CHARS = 1024;
    private static final int VALUE_PREVIEW_CHARS = 120;
    private AuthoringDiff() { }

    public static String compare(AuthoringDocument before, AuthoringDocument after) {
        if (before == null || after == null) throw new IllegalArgumentException("Both documents are required for review");
        State state = new State(); state.visit("", before.toJson(), after.toJson());
        StringBuilder result = new StringBuilder("Before SHA256: ").append(before.sha256()).append("\nAfter SHA256: ").append(after.sha256()).append('\n');
        if (state.count == 0 && !state.truncated) result.append("No JSON value changes.\n");
        else result.append("Listed differences: ").append(state.count).append('\n');
        result.append("Paths use JSON Pointer (~0 = ~, ~1 = /). Values and very long paths are previews.\n");
        result.append(state.output);
        if (state.truncated) result.append("TRUNCATED: additional differences were not enumerated (4096 differences / 512 KiB text limit). Export and review both full JSON documents before confirming.\n");
        else result.append("Comparison complete; value previews are not the full documents. Export full JSON for full values.\n");
        return result.toString();
    }

    private static final class State {
        final StringBuilder output = new StringBuilder(); int count; boolean truncated;
        void visit(String path, JsonElement before, JsonElement after) {
            if (truncated || before == after || before != null && before.equals(after)) return;
            if (before != null && after != null && before.isJsonObject() && after.isJsonObject()) {
                JsonObject left = before.getAsJsonObject(), right = after.getAsJsonObject(); Set<String> keys = new LinkedHashSet<>();
                for (Map.Entry<String, JsonElement> entry : left.entrySet()) keys.add(entry.getKey());
                for (Map.Entry<String, JsonElement> entry : right.entrySet()) keys.add(entry.getKey());
                for (String key : keys) { visit(child(path, key), left.get(key), right.get(key)); if (truncated) break; }
                return;
            }
            if (before != null && after != null && before.isJsonArray() && after.isJsonArray()) {
                JsonArray left = before.getAsJsonArray(), right = after.getAsJsonArray();
                for (int i = 0; i < Math.max(left.size(), right.size()); i++) {
                    visit(child(path, Integer.toString(i)), i < left.size() ? left.get(i) : null, i < right.size() ? right.get(i) : null);
                    if (truncated) break;
                }
                return;
            }
            String row = (before == null ? "ADD " : after == null ? "REMOVE " : "CHANGE ") + (path.isEmpty() ? "/" : path)
                + " : " + preview(before) + " -> " + preview(after) + '\n';
            if (count >= MAX_DIFFS || output.length() + row.length() > MAX_OUTPUT_CHARS) { truncated = true; return; }
            output.append(row); count++;
        }
        String child(String parent, String key) {
            String result = parent + "/" + key.replace("~", "~0").replace("/", "~1");
            return result.length() <= MAX_PATH_CHARS ? result : result.substring(0, MAX_PATH_CHARS - 24) + "...[path shortened]";
        }
        String preview(JsonElement value) {
            if (value == null) return "<missing>";
            if (value.isJsonObject()) return "{object:" + value.getAsJsonObject().entrySet().size() + " fields}";
            if (value.isJsonArray()) return "[array:" + value.getAsJsonArray().size() + " values]";
            String text = value.toString();
            return text.length() <= VALUE_PREVIEW_CHARS ? text : text.substring(0, VALUE_PREVIEW_CHARS) + "...[value shortened]";
        }
    }
}
