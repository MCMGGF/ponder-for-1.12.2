package net.createmod.ponder.authoring.exchange;

import net.createmod.ponder.authoring.AuthoringDocument;

/** Produces reviewable Java source text, not a class file, installed mod, or an executed compilation. */
public final class JavaSceneTextExporter {
    private JavaSceneTextExporter() { }

    public static String exportText(AuthoringDocument document, String packageName, String className) {
        if (document == null || packageName == null || className == null
            || !packageName.matches("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)*")
            || !className.matches("[A-Z][A-Za-z0-9_]*")) throw new IllegalArgumentException("Safe Java package and class names are required");
        for (String part : packageName.split("\\.")) if (part.matches("abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|true|false|null|_"))
            throw new IllegalArgumentException("Java package segments cannot be reserved words");
        String json = document.toJson().toString();
        StringBuilder output = new StringBuilder("package ").append(packageName).append(";\n\n")
            .append("import java.util.List;\nimport net.createmod.ponder.authoring.AuthoringCompiler;\n")
            .append("import net.createmod.ponder.authoring.AuthoringDocument;\nimport net.createmod.ponder.foundation.PonderScene;\n\n")
            .append("// Authoring compiler bridge, not a lossless direct Create/PonderJS API translation.\n")
            .append("// Caller supplies local assets/handlers; validate() must pass before registration.\n");
        if (hasImageSteps(document)) output
            .append("// show_image requires ImageStepHandlers.register(compiler, new ImageAssetPool(authoringRoot)).\n")
            .append("// Both helpers are net.createmod.ponder.authoring.assets; bundle referenced PNGs and explicit imageMappings.\n");
        output.append("public final class ").append(className).append(" {\n  private ").append(className).append("() { }\n")
            .append("  public static AuthoringDocument document() {\n    StringBuilder json = new StringBuilder();\n");
        // Separate append statements avoid Java's 64 KiB constant-pool string limit without hiding source in Base64.
        for (int offset = 0; offset < json.length(); offset += 8000) {
            String slice = json.substring(offset, Math.min(json.length(), offset + 8000));
            output.append("    json.append(").append(javaQuote(slice)).append(");\n");
        }
        output.append("    return AuthoringDocument.parse(json.toString());\n  }\n")
            .append("  public static List<PonderScene> compile(AuthoringCompiler compiler, AuthoringCompiler.AssetResolver assets) {\n")
            .append("    if (compiler == null) throw new IllegalArgumentException(\"Explicit compiler is required\");\n")
            .append("    return compiler.compile(document(), assets);\n  }\n}\n");
        return output.toString();
    }
    private static boolean hasImageSteps(AuthoringDocument document) {
        for (com.google.gson.JsonObject scene : document.getScenes()) {
            if (!scene.has("steps") || !scene.get("steps").isJsonArray()) continue;
            for (com.google.gson.JsonElement step : scene.getAsJsonArray("steps"))
                if (step.isJsonObject() && "show_image".equals(AuthoringDocument.string(step.getAsJsonObject(), "type", ""))) return true;
        }
        return false;
    }
    private static String javaQuote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': result.append("\\\\"); break; case '"': result.append("\\\""); break;
                case '\n': result.append("\\n"); break; case '\r': result.append("\\r"); break; case '\t': result.append("\\t"); break;
                default:
                    if (c < 32 || c > 126) result.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c)); else result.append(c);
            }
        }
        return result.append('"').toString();
    }
}
