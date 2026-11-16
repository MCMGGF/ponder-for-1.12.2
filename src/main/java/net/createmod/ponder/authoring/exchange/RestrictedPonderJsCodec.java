package net.createmod.ponder.authoring.exchange;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.AuthoringValidation;

/** A fully consumed, literal-only PonderJS subset. This class never evaluates JavaScript. */
public final class RestrictedPonderJsCodec {
    public static final int MAX_SOURCE_CHARS = 2 * 1024 * 1024;
    public static final class ImportResult {
        public final AuthoringDocument document;
        public final AuthoringValidation validation;
        ImportResult(AuthoringDocument document, AuthoringValidation validation) { this.document = document; this.validation = validation; }
    }
    public static final class ExportResult {
        public final String text;
        public final AuthoringValidation validation;
        ExportResult(String text, AuthoringValidation validation) { this.text = text; this.validation = validation; }
    }
    private RestrictedPonderJsCodec() { }

    public static ImportResult importText(String source, String documentId) {
        try {
            Parser parser = new Parser(source); AuthoringDocument document = parser.document(documentId);
            AuthoringValidation validation = new AuthoringValidation.Builder().addAll(AuthoringValidation.validate(document))
                .warning("exchange.restricted_js", "/source", "Literal-only conversion; 1.12.2 registry, asset and capability validation is still required").build();
            return new ImportResult(document, validation);
        } catch (RuntimeException rejected) {
            return new ImportResult(null, new AuthoringValidation.Builder().error("exchange.js_rejected", "/source", String.valueOf(rejected.getMessage())).build());
        }
    }

    public static ExportResult exportText(AuthoringDocument document) {
        AuthoringValidation initial = AuthoringValidation.validate(document);
        if (initial.hasErrors()) return new ExportResult(null, initial);
        AuthoringValidation.Builder issues = new AuthoringValidation.Builder().addAll(initial);
        StringBuilder output = new StringBuilder("// Restricted PonderJS text export; review before external use. No code was executed.\nPonder.registry((event) => {\n  event.create(");
        JsonArray items = new JsonArray(); for (String item : document.getItems()) items.add(item);
        output.append(items).append(")");
        JsonObject root = document.toJson();
        if (root.has("nbtFilter") || root.has("itemNbtFilters"))
            issues.error("exchange.item_filters", "/items", "The restricted PonderJS item list cannot represent NBT binding filters; use the full JSON document instead");
        String inheritedStructure = AuthoringDocument.string(root, "structure", "");
        if (root.has("structures")) issues.error("exchange.structure_pool", "/structures", "Resolve structure pool references to explicit segment structure IDs before text export");
        int index = 0;
        for (JsonObject segment : document.getScenes()) {
            String path = "/scenes/" + index++;
            inheritedStructure = AuthoringDocument.string(segment, "structure", inheritedStructure);
            String structure = inheritedStructure;
            Set<String> segmentFields = new HashSet<>(Arrays.asList("id", "title", "structure", "steps"));
            for (java.util.Map.Entry<String, JsonElement> entry : segment.entrySet()) if (!segmentFields.contains(entry.getKey()))
                issues.error("exchange.segment_field", path + "/" + entry.getKey(), "Unrepresented segment field; convert explicitly before text export");
            output.append("\n    .scene(").append(quote(AuthoringDocument.string(segment, "id", "scene_" + index))).append(", ")
                .append(quote(AuthoringDocument.string(segment, "title", "Scene " + index))).append(", ").append(quote(structure))
                .append(", (scene, util) => {\n");
            int stepIndex = 0;
            for (JsonElement element : segment.getAsJsonArray("steps")) {
                String stepPath = path + "/steps/" + stepIndex++;
                if (!element.isJsonObject()) { issues.error("exchange.step", stepPath, "Step must be an object"); continue; }
                JsonObject step = element.getAsJsonObject();
                try { output.append("      ").append(exportStep(step)).append(";\n"); }
                catch (RuntimeException unsupported) { issues.error("exchange.js_unsupported", stepPath, String.valueOf(unsupported.getMessage())); }
            }
            output.append("    })");
        }
        output.append(";\n});\n");
        if (document.toJson().has("idMappings") && (!document.toJson().get("idMappings").isJsonObject()
            || document.toJson().getAsJsonObject("idMappings").size() != 0))
            issues.error("exchange.id_mappings", "/idMappings", "Apply explicit ID mappings before PonderJS export; mappings are not silently dropped");
        AuthoringValidation validation = issues.build();
        return new ExportResult(validation.hasErrors() ? null : output.toString(), validation);
    }

    private static String exportStep(JsonObject step) {
        if ("scene".equals(AuthoringDocument.string(step,"pointSpace","")))
            throw new IllegalArgumentException("Absolute scene points have no lossless restricted PonderJS mapping; use the full JSON document");
        String type = AuthoringDocument.string(step, "type", "");
        if ("idle".equals(type)) { fields(step, "type", "duration"); return "scene.idle(" + ticks(step, "duration", 20) + ")"; }
        if ("show_structure".equals(type)) {
            fields(step, "type", "fullScene", "direction");
            if (step.has("direction") && !"up".equalsIgnoreCase(step.get("direction").getAsString())) throw new IllegalArgumentException("showStructure only maps the default UP direction");
            return "scene.showStructure()";
        }
        if ("text".equals(type)) {
            fields(step, "type", "duration", "text", "point");
            if (!step.has("text") || !step.get("text").isJsonPrimitive() || !step.getAsJsonPrimitive("text").isString())
                throw new IllegalArgumentException("Localized text objects need explicit literal conversion");
            return "scene.text(" + ticks(step, "duration", 60) + ", " + quote(step.get("text").getAsString())
                + (step.has("point") ? ", " + vector(step.get("point"), false) : "") + ").placeNearTarget()";
        }
        if ("set_block".equals(type)) {
            fields(step, "type", "blockPos", "block", "spawnParticles");
            return "scene.world.setBlock(" + vector(step.get("blockPos"), true) + ", " + quote(requiredString(step, "block"))
                + ", " + bool(step, "spawnParticles", true) + ")";
        }
        if ("destroy_block".equals(type)) { fields(step, "type", "blockPos"); return "scene.world.destroyBlock(" + vector(step.get("blockPos"), true) + ")"; }
        if ("rotate_camera_y".equals(type)) {
            fields(step, "type", "degrees", "duration");
            if (number(step, "duration", 60) != 0) throw new IllegalArgumentException("Camera duration requires a separate idle step for PonderJS export");
            return "scene.rotateCameraY(" + number(step, "degrees", 90) + ")";
        }
        throw new IllegalArgumentException("No lossless restricted PonderJS mapping for " + type);
    }
    private static void fields(JsonObject step, String... allowed) {
        Set<String> keys = new HashSet<>(Arrays.asList(allowed));
        for (java.util.Map.Entry<String, JsonElement> entry : step.entrySet())
            if (!keys.contains(entry.getKey())) throw new IllegalArgumentException("Unrepresented step field: " + entry.getKey());
    }
    private static String requiredString(JsonObject value, String key) {
        JsonElement element = value.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Required literal string " + key);
        return element.getAsString();
    }
    private static double number(JsonObject value, String key, double fallback) {
        if (!value.has(key)) return fallback;
        JsonElement element = value.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Required number " + key);
        double result = element.getAsDouble();
        if (Double.isInfinite(result) || Double.isNaN(result) || Math.abs(result) > 30000000) throw new IllegalArgumentException("Number out of bounds");
        return result;
    }
    private static int ticks(JsonObject value, String key, int fallback) {
        double count = number(value, key, fallback);
        if (count < 0 || count > 120000 || count != Math.rint(count)) throw new IllegalArgumentException("Integral ticks 0..120000 required");
        return (int) count;
    }
    private static boolean bool(JsonObject value, String key, boolean fallback) {
        if (!value.has(key)) return fallback;
        if (!value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isBoolean()) throw new IllegalArgumentException("Required boolean " + key);
        return value.get(key).getAsBoolean();
    }
    private static String vector(JsonElement element, boolean integer) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != 3) throw new IllegalArgumentException("Three-coordinate array required");
        for (JsonElement value : element.getAsJsonArray()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Numeric coordinate required");
            double number = value.getAsDouble();
            if (Double.isNaN(number) || Double.isInfinite(number) || Math.abs(number) > 30000000 || integer && number != Math.rint(number))
                throw new IllegalArgumentException("Invalid coordinate");
        }
        return element.toString();
    }
    static String quote(String text) { return new JsonPrimitive(text).toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029"); }

    private static final class Parser {
        final Lexer lexer;
        int steps;
        Parser(String source) {
            if (source == null || source.length() > MAX_SOURCE_CHARS) throw new IllegalArgumentException("Restricted source exceeds two MiB");
            lexer = new Lexer(source);
        }
        AuthoringDocument document(String id) {
            if (!AuthoringDocument.validId(id)) throw new IllegalArgumentException("Explicit safe document ID required");
            lexer.expect("Ponder"); lexer.expect("."); lexer.expect("registry"); lexer.expect("(");
            boolean wrapped = lexer.take("("); lexer.expect("event"); if (wrapped) lexer.expect(")"); lexer.expect("=>"); lexer.expect("{");
            lexer.expect("event"); lexer.expect("."); lexer.expect("create"); lexer.expect("(");
            JsonArray items = new JsonArray();
            if (lexer.take("[")) { do { items.add(lexer.string()); } while (lexer.take(",")); lexer.expect("]"); }
            else items.add(lexer.string());
            lexer.expect(")");
            JsonArray scenes = new JsonArray();
            while (lexer.take(".")) {
                if (scenes.size() >= 64) throw lexer.error("At most 64 segments");
                lexer.expect("scene"); lexer.expect("("); JsonObject segment = new JsonObject();
                segment.addProperty("id", lexer.string()); lexer.expect(","); segment.addProperty("title", lexer.string()); lexer.expect(",");
                String structure = lexer.string(); if (!structure.isEmpty()) segment.addProperty("structure", structure);
                lexer.expect(","); lexer.expect("("); lexer.expect("scene"); lexer.expect(","); lexer.expect("util"); lexer.expect(")"); lexer.expect("=>"); lexer.expect("{");
                JsonArray body = new JsonArray();
                while (!lexer.take("}")) { if (++steps > 16384) throw lexer.error("Too many steps"); body.add(call()); lexer.expect(";"); }
                lexer.expect(")"); segment.add("steps", body); scenes.add(segment);
            }
            lexer.expect(";"); lexer.expect("}"); lexer.expect(")"); lexer.take(";"); lexer.end();
            JsonObject result = new JsonObject(); result.addProperty("id", id); result.add("items", items); result.add("scenes", scenes);
            JsonObject provenance = new JsonObject(); provenance.addProperty("type", "restricted-ponderjs"); provenance.addProperty("executed", false); result.add("source", provenance);
            return AuthoringDocument.fromJson(result);
        }
        JsonObject call() {
            lexer.expect("scene"); lexer.expect("."); String method = lexer.identifier();
            if ("world".equals(method)) { lexer.expect("."); method += "." + lexer.identifier(); }
            lexer.expect("("); JsonObject step = new JsonObject();
            if ("showStructure".equals(method)) { step.addProperty("type", "show_structure"); step.addProperty("fullScene", true); }
            else if ("idle".equals(method) || "idleSeconds".equals(method)) {
                double time = lexer.number() * ("idleSeconds".equals(method) ? 20 : 1);
                if (time < 0 || time > 120000 || time != Math.rint(time)) throw lexer.error("Idle must resolve to integral ticks 0..120000");
                step.addProperty("type", "idle"); step.addProperty("duration", (int) time);
            } else if ("addKeyframe".equals(method)) { step.addProperty("type", "idle"); step.addProperty("duration", 0); }
            else if ("text".equals(method)) {
                step.addProperty("type", "text"); step.addProperty("duration", lexer.duration()); lexer.expect(","); step.addProperty("text", lexer.string());
                if (lexer.take(",")) step.add("point", lexer.vector(false));
            } else if ("world.setBlock".equals(method)) {
                step.addProperty("type", "set_block"); step.add("blockPos", lexer.vector(true)); lexer.expect(","); step.addProperty("block", lexer.string());
                lexer.expect(","); step.addProperty("spawnParticles", lexer.bool());
            } else if ("world.destroyBlock".equals(method)) { step.addProperty("type", "destroy_block"); step.add("blockPos", lexer.vector(true)); }
            else if ("rotateCameraY".equals(method)) { step.addProperty("type", "rotate_camera_y"); step.addProperty("degrees", lexer.number()); step.addProperty("duration", 0); }
            else throw lexer.error("Unsupported call scene." + method + "; nothing was executed");
            lexer.expect(")");
            if ("text".equals(method)) while (lexer.take(".")) {
                String modifier = lexer.identifier(); lexer.expect("(");
                if ("pointAt".equals(modifier)) {
                    if (step.has("point")) throw lexer.error("Duplicate text point target"); step.add("point", lexer.vector(false));
                } else if (!"placeNearTarget".equals(modifier)) throw lexer.error("Unsupported text modifier " + modifier);
                lexer.expect(")");
            }
            return step;
        }
    }

    private static final class Lexer {
        final String source;
        int position, start;
        String token;
        boolean string;
        Lexer(String source) { this.source = source; next(); }
        IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at character " + start); }
        void expect(String value) { if (!take(value)) throw error("Expected " + value + ", found " + token); }
        boolean take(String value) { if (!string && value.equals(token)) { next(); return true; } return false; }
        String identifier() { if (string || !token.matches("[A-Za-z_$][A-Za-z0-9_$]*")) throw error("Expected identifier"); String value = token; next(); return value; }
        String string() { if (!string) throw error("Only literal strings are accepted"); String value = token; next(); return value; }
        double number() {
            if (string || !token.matches("-?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) throw error("Only literal numbers are accepted");
            double value = Double.parseDouble(token); if (!Double.isFinite(value) || Math.abs(value) > 30000000) throw error("Number exceeds bounds"); next(); return value;
        }
        int duration() { double value = number(); if (value < 0 || value > 120000 || value != Math.rint(value)) throw error("Duration must be integral ticks 0..120000"); return (int) value; }
        boolean bool() { if (take("true")) return true; if (take("false")) return false; throw error("Only literal booleans are accepted"); }
        JsonArray vector(boolean integer) {
            expect("["); JsonArray values = new JsonArray();
            for (int i = 0; i < 3; i++) { if (i != 0) expect(","); double value = number(); if (integer && value != Math.rint(value)) throw error("Block coordinates must be integral"); values.add(value); }
            expect("]"); return values;
        }
        void end() { if (!"<end>".equals(token)) throw error("Trailing or executable code is forbidden"); }
        void next() {
            while (true) {
                while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++;
                if (source.startsWith("//", position)) { int newline = source.indexOf('\n', position + 2); position = newline < 0 ? source.length() : newline + 1; continue; }
                if (source.startsWith("/*", position)) { int end = source.indexOf("*/", position + 2); if (end < 0) throw error("Unterminated comment"); position = end + 2; continue; }
                break;
            }
            start = position; string = false;
            if (position >= source.length()) { token = "<end>"; return; }
            char first = source.charAt(position++);
            if (first == '\'' || first == '"') { token = quoted(first); string = true; return; }
            if (source.startsWith("=>", start)) { position = start + 2; token = "=>"; return; }
            if (Character.isLetter(first) || first == '_' || first == '$') {
                while (position < source.length()) { char c = source.charAt(position); if (!(Character.isLetterOrDigit(c) || c == '_' || c == '$')) break; position++; }
            } else if (Character.isDigit(first) || first == '-' || first == '.' && position < source.length() && Character.isDigit(source.charAt(position))) {
                while (position < source.length() && "0123456789.eE+-".indexOf(source.charAt(position)) >= 0) position++;
            }
            token = source.substring(start, position);
        }
        String quoted(char quote) {
            StringBuilder text = new StringBuilder();
            while (position < source.length()) {
                char c = source.charAt(position++); if (c == quote) return text.toString();
                if (c < 32) throw error("Unescaped control character in string");
                if (c != '\\') { text.append(c); continue; }
                if (position >= source.length()) throw error("Unterminated string escape");
                char escaped = source.charAt(position++);
                switch (escaped) {
                    case '\\': case '\'': case '"': case '/': text.append(escaped); break;
                    case 'n': text.append('\n'); break; case 'r': text.append('\r'); break; case 't': text.append('\t'); break;
                    case 'b': text.append('\b'); break; case 'f': text.append('\f'); break;
                    case 'u':
                        if (position + 4 > source.length()) throw error("Truncated Unicode escape");
                        try { text.append((char) Integer.parseInt(source.substring(position, position + 4), 16)); }
                        catch (NumberFormatException invalid) { throw error("Invalid Unicode escape"); }
                        position += 4; break;
                    default: throw error("Unsupported string escape");
                }
            }
            throw error("Unterminated string");
        }
    }
}
