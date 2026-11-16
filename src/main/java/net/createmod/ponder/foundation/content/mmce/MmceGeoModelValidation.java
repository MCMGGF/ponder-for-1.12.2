package net.createmod.ponder.foundation.content.mmce;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

/** Bounded schema admission before handing geometry to GeckoLib's data builder. */
final class MmceGeoModelValidation {

    static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    static final int MAX_BONES = 256;
    static final int MAX_DEPTH = 64;
    static final int MAX_CUBES = 4096;
    private static final int MAX_TOKENS = 500000;

    static final class Summary {
        final int bones;
        final int cubes;

        private Summary(int bones, int cubes) {
            this.bones = bones;
            this.cubes = cubes;
        }
    }

    private MmceGeoModelValidation() {
    }

    static Summary validate(String json) throws IOException {
        if (json == null || json.length() > MAX_JSON_BYTES) {
            throw invalid("几何文件超过 2 MiB 预览限制");
        }
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            preflight(reader, 0, new int[] {0});
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw invalid("几何文件包含多余的 JSON 内容");
            }
        }
        JsonObject root = object(new JsonParser().parse(json), "几何根节点");
        if (!"1.12.0".equals(string(root, "format_version"))) {
            throw invalid("目前仅支持 Bedrock 1.12.0 静态方块几何");
        }
        JsonArray geometries = array(root.get("minecraft:geometry"), "minecraft:geometry");
        if (geometries.size() != 1) {
            throw invalid("几何文件必须包含一个明确的模型");
        }
        JsonObject geometry = object(geometries.get(0), "几何");
        JsonObject description = object(geometry.get("description"), "description");
        positiveSize(description, "texture_width");
        positiveSize(description, "texture_height");
        JsonArray bones = array(geometry.get("bones"), "骨骼");
        if (bones.size() == 0 || bones.size() > MAX_BONES) {
            throw invalid("模型骨骼数量超出 1 至 256 的预览范围");
        }
        Map<String, String> parents = new LinkedHashMap<String, String>();
        int cubes = 0;
        for (JsonElement element : bones) {
            JsonObject bone = object(element, "骨骼");
            String name = string(bone, "name");
            if (name == null || name.isEmpty() || parents.containsKey(name)) {
                throw invalid("骨骼名称为空或重复");
            }
            if (name.startsWith("bloom") || name.startsWith("emissive") || name.startsWith("transparent")) {
                throw invalid("此模型包含尚未适配的发光或半透明骨骼效果");
            }
            parents.put(name, string(bone, "parent"));
            reject(bone, "poly_mesh", "texture_meshes", "binding", "render_group_id");
            vector(bone, "pivot", 3, false);
            vector(bone, "rotation", 3, false);
            if (!bone.has("cubes")) {
                continue;
            }
            JsonArray boneCubes = array(bone.get("cubes"), "方块几何");
            cubes += boneCubes.size();
            if (cubes > MAX_CUBES) {
                throw invalid("模型超过 4096 个几何方块的预览限制");
            }
            for (JsonElement cubeElement : boneCubes) {
                JsonObject cube = object(cubeElement, "方块几何");
                reject(cube, "poly_mesh", "texture_meshes", "material_instance");
                vector(cube, "origin", 3, true);
                vector(cube, "size", 3, true);
                vector(cube, "pivot", 3, false);
                vector(cube, "rotation", 3, false);
                validateUv(cube.get("uv"));
            }
        }
        if (cubes == 0) {
            throw invalid("默认模型没有可显示的方块几何");
        }
        for (String name : parents.keySet()) {
            Set<String> seen = new HashSet<String>();
            String cursor = name;
            while (cursor != null) {
                if (!parents.containsKey(cursor)) {
                    throw invalid("骨骼引用了不存在的父骨骼：" + cursor);
                }
                if (!seen.add(cursor)) {
                    throw invalid("骨骼父子关系存在循环");
                }
                if (seen.size() > MAX_DEPTH) {
                    throw invalid("骨骼层级超过 64 层的预览限制");
                }
                cursor = parents.get(cursor);
            }
        }
        return new Summary(bones.size(), cubes);
    }

    private static void preflight(JsonReader reader, int depth, int[] tokens) throws IOException {
        if (depth > MAX_DEPTH || ++tokens[0] > MAX_TOKENS) {
            throw invalid("几何 JSON 嵌套或数据数量超过预览限制");
        }
        JsonToken token = reader.peek();
        if (token == JsonToken.BEGIN_OBJECT) {
            reader.beginObject();
            Set<String> keys = new HashSet<String>();
            while (reader.hasNext()) {
                if (!keys.add(reader.nextName())) {
                    throw invalid("几何 JSON 包含重复字段");
                }
                preflight(reader, depth + 1, tokens);
            }
            reader.endObject();
        } else if (token == JsonToken.BEGIN_ARRAY) {
            reader.beginArray();
            while (reader.hasNext()) {
                preflight(reader, depth + 1, tokens);
            }
            reader.endArray();
        } else if (token == JsonToken.NUMBER) {
            double number = Double.parseDouble(reader.nextString());
            if (Double.isNaN(number) || Double.isInfinite(number) || Math.abs(number) > 1048576D) {
                throw invalid("几何 JSON 包含非有限数值或过大的坐标");
            }
        } else if (token == JsonToken.STRING) {
            if (reader.nextString().length() > 4096) {
                throw invalid("几何 JSON 字符串过长");
            }
        } else if (token == JsonToken.BOOLEAN) {
            reader.nextBoolean();
        } else if (token == JsonToken.NULL) {
            reader.nextNull();
        } else {
            throw invalid("几何 JSON 语法无效");
        }
    }

    private static void validateUv(JsonElement uv) {
        if (uv != null && uv.isJsonArray()) {
            vectorValue(uv, 2, "UV");
            return;
        }
        JsonObject faces = object(uv, "逐面 UV");
        for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
            String key = face.getKey();
            if (!"north".equals(key) && !"south".equals(key) && !"east".equals(key)
                && !"west".equals(key) && !"up".equals(key) && !"down".equals(key)) {
                throw invalid("包含尚未适配的 UV 字段：" + key);
            }
            JsonObject data = object(face.getValue(), "UV 面");
            reject(data, "material_instance", "uv_rotation");
            vector(data, "uv", 2, true);
            vector(data, "uv_size", 2, true);
        }
    }

    private static void vector(JsonObject owner, String key, int length, boolean required) {
        if (required || owner.has(key)) {
            vectorValue(owner.get(key), length, key);
        }
    }

    private static void vectorValue(JsonElement value, int length, String label) {
        JsonArray array = array(value, label);
        if (array.size() != length) {
            throw invalid(label + " 的坐标数量无效");
        }
        for (JsonElement coordinate : array) {
            if (!coordinate.isJsonPrimitive() || !coordinate.getAsJsonPrimitive().isNumber()) {
                throw invalid(label + " 包含非数值坐标");
            }
        }
    }

    private static void positiveSize(JsonObject owner, String key) {
        JsonElement value = owner.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
            || value.getAsDouble() <= 0 || value.getAsDouble() > 1024
            || value.getAsDouble() != Math.rint(value.getAsDouble())) {
            throw invalid("模型纹理尺寸必须为 1 至 1024 的整数");
        }
    }

    private static void reject(JsonObject owner, String... unsupported) {
        for (String field : unsupported) {
            if (owner.has(field)) {
                throw invalid("包含尚未适配的几何功能：" + field);
            }
        }
    }

    private static String string(JsonObject owner, String key) {
        JsonElement value = owner.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw invalid(key + " 必须是字符串");
        }
        return value.getAsString();
    }

    private static JsonArray array(JsonElement value, String label) {
        if (value == null || !value.isJsonArray()) {
            throw invalid(label + " 必须是数组");
        }
        return value.getAsJsonArray();
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) {
            throw invalid(label + " 必须是对象");
        }
        return value.getAsJsonObject();
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
