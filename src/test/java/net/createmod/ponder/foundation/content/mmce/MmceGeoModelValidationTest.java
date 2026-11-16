package net.createmod.ponder.foundation.content.mmce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class MmceGeoModelValidationTest {

    private static final String CUBE = "{\"origin\":[0,0,0],\"size\":[16,0,16],\"uv\":[0,0]}";

    @Test
    void permitsThinFacesAndParentAfterChild() throws IOException {
        MmceGeoModelValidation.Summary result = MmceGeoModelValidation.validate(model(
            bone("child", "parent", CUBE) + "," + bone("parent", null, "")));
        assertEquals(2, result.bones);
        assertEquals(1, result.cubes);
    }

    @Test
    void rejectsCyclesAndMissingParents() {
        assertThrows(IllegalArgumentException.class, () -> MmceGeoModelValidation.validate(model(
            bone("a", "b", CUBE) + "," + bone("b", "a", ""))));
        assertThrows(IllegalArgumentException.class, () ->
            MmceGeoModelValidation.validate(model(bone("a", "missing", CUBE))));
    }

    @Test
    void rejectsDuplicateBonesAndJsonFields() {
        assertThrows(IllegalArgumentException.class, () -> MmceGeoModelValidation.validate(model(
            bone("a", null, CUBE) + "," + bone("a", null, ""))));
        assertThrows(IllegalArgumentException.class, () ->
            MmceGeoModelValidation.validate("{\"format_version\":\"1.12.0\",\"format_version\":\"1.12.0\"}"));
    }

    @Test
    void rejectsUnreviewedEffectsAndGeometry() {
        assertThrows(IllegalArgumentException.class, () ->
            MmceGeoModelValidation.validate(model(bone("bloom_body", null, CUBE))));
        assertThrows(IllegalArgumentException.class, () -> MmceGeoModelValidation.validate(model(
            "{\"name\":\"a\",\"poly_mesh\":{},\"cubes\":[" + CUBE + "]}")));
    }

    @Test
    void rejectsNumericOverflowAndInvalidVectors() {
        assertThrows(IllegalArgumentException.class, () -> MmceGeoModelValidation.validate(model(
            bone("a", null, CUBE.replace("[0,0,0]", "[1e309,0,0]")))));
        assertThrows(IllegalArgumentException.class, () -> MmceGeoModelValidation.validate(model(
            bone("a", null, CUBE.replace("[16,0,16]", "[16,16]")))));
        assertThrows(IllegalArgumentException.class, () -> MmceGeoModelValidation.validate(model(
            bone("a", null, CUBE.replace("[16,0,16]", "[\"16\",0,16]")))));
    }

    @Test
    void permitsSignedExtentsUsedByCurrentElectricArcFurnaceAsset() throws IOException {
        assertEquals(1, MmceGeoModelValidation.validate(model(
            bone("a", null, CUBE.replace("[16,0,16]", "[-16,1,16]")))).cubes);
    }

    @Test
    void rejectsUnsupportedVersionAndMultipleGeometries() {
        assertThrows(IllegalArgumentException.class, () ->
            MmceGeoModelValidation.validate(model(bone("a", null, CUBE)).replace("1.12.0", "1.16.0")));
        assertThrows(IllegalArgumentException.class, () ->
            MmceGeoModelValidation.validate("{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{},{}]}"));
    }

    @Test
    void rejectsBoneAndCubeBudgets() {
        StringBuilder bones = new StringBuilder();
        for (int i = 0; i <= MmceGeoModelValidation.MAX_BONES; i++) {
            if (i != 0) {
                bones.append(',');
            }
            bones.append(bone("bone" + i, null, CUBE));
        }
        assertThrows(IllegalArgumentException.class, () -> MmceGeoModelValidation.validate(model(bones.toString())));
        StringBuilder cubes = new StringBuilder();
        for (int i = 0; i <= MmceGeoModelValidation.MAX_CUBES; i++) {
            if (i != 0) {
                cubes.append(',');
            }
            cubes.append(CUBE);
        }
        assertThrows(IllegalArgumentException.class, () ->
            MmceGeoModelValidation.validate(model(bone("a", null, cubes.toString()))));
    }

    @Test
    void rejectsExcessiveBoneDepthBeforeNativeTreeExpansion() {
        StringBuilder bones = new StringBuilder();
        for (int i = 0; i <= MmceGeoModelValidation.MAX_DEPTH; i++) {
            if (i != 0) {
                bones.append(',');
            }
            bones.append(bone("b" + i, i == 0 ? null : "b" + (i - 1), i == 0 ? CUBE : ""));
        }
        assertThrows(IllegalArgumentException.class, () -> MmceGeoModelValidation.validate(model(bones.toString())));
    }

    @Test
    void permitsOmittedUvFacesButRejectsUnknownUvFeatures() throws IOException {
        String cube = CUBE.replace("\"uv\":[0,0]",
            "\"uv\":{\"north\":{\"uv\":[0,0],\"uv_size\":[16,16]}}");
        assertEquals(1, MmceGeoModelValidation.validate(model(bone("a", null, cube))).cubes);
        assertThrows(IllegalArgumentException.class, () -> MmceGeoModelValidation.validate(model(
            bone("a", null, cube.replace("\"uv_size\"", "\"uv_rotation\"")))));
    }

    @Test
    void rejectsOversizedAndDeeplyNestedJsonBeforeParsing() {
        char[] oversized = new char[MmceGeoModelValidation.MAX_JSON_BYTES + 1];
        assertThrows(IllegalArgumentException.class, () ->
            MmceGeoModelValidation.validate(new String(oversized)));
        StringBuilder nested = new StringBuilder();
        for (int i = 0; i < 70; i++) {
            nested.append('[');
        }
        for (int i = 0; i < 70; i++) {
            nested.append(']');
        }
        assertThrows(IllegalArgumentException.class, () -> MmceGeoModelValidation.validate(nested.toString()));
    }

    private static String model(String bones) {
        return "{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{\"description\":"
            + "{\"identifier\":\"geometry.test\",\"texture_width\":64,\"texture_height\":64},\"bones\":["
            + bones + "]}]}";
    }

    private static String bone(String name, String parent, String cubes) {
        return "{\"name\":\"" + name + "\"" + (parent == null ? "" : ",\"parent\":\"" + parent + "\"")
            + ",\"cubes\":[" + cubes + "]}";
    }
}
