package net.createmod.ponder.foundation.content.mmce;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

import javax.vecmath.Matrix3f;
import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

import net.createmod.ponder.foundation.Vec3iAccessor;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3i;

/** Uses only fresh GeckoLib data objects and a private matrix stack. No GL calls. */
public final class MmceStaticModelBaker {

    private Bridge bridge;

    public MmceStaticModelMesh bake(String json, String resourceName)
        throws IOException, ReflectiveOperationException {
        MmceGeoModelValidation.Summary summary = MmceGeoModelValidation.validate(json);
        if (bridge == null) {
            bridge = new Bridge();
        }
        Object raw = bridge.convert.invoke(null, json);
        Object tree = bridge.hierarchy.invoke(null, raw, new ResourceLocation(resourceName));
        Object model = bridge.construct.invoke(bridge.builder.newInstance(), tree);
        Object matrices = bridge.stack.newInstance();
        Vertices vertices = new Vertices(summary.cubes);
        for (Object bone : collection(bridge.topLevelBones.get(model))) {
            bakeBone(bone, matrices, vertices, 1);
        }
        return new MmceStaticModelMesh(vertices.data, vertices.used, summary.bones, summary.cubes);
    }

    private void bakeBone(Object bone, Object matrices, Vertices vertices, int depth)
        throws ReflectiveOperationException {
        if (depth > MmceGeoModelValidation.MAX_DEPTH) {
            throw new IllegalArgumentException("转换后的骨骼层级超过预览限制");
        }
        bridge.push.invoke(matrices);
        try {
            bridge.translateBone.invoke(matrices, bone);
            bridge.pivotBone.invoke(matrices, bone);
            bridge.rotateBone.invoke(matrices, bone);
            bridge.scaleBone.invoke(matrices, bone);
            bridge.unpivotBone.invoke(matrices, bone);
            if (!Boolean.TRUE.equals(bridge.hidden.invoke(bone))) {
                for (Object cube : collection(bridge.childCubes.get(bone))) {
                    bridge.push.invoke(matrices);
                    try {
                        bakeCube(cube, matrices, vertices);
                    } finally {
                        bridge.pop.invoke(matrices);
                    }
                }
            }
            if (!Boolean.TRUE.equals(bridge.hideChildren.invoke(bone))) {
                for (Object child : collection(bridge.childBones.get(bone))) {
                    bakeBone(child, matrices, vertices, depth + 1);
                }
            }
        } finally {
            bridge.pop.invoke(matrices);
        }
    }

    private void bakeCube(Object cube, Object matrices, Vertices target) throws ReflectiveOperationException {
        bridge.pivotCube.invoke(matrices, cube);
        bridge.rotateCube.invoke(matrices, cube);
        bridge.unpivotCube.invoke(matrices, cube);
        Matrix4f modelMatrix = (Matrix4f) bridge.modelMatrix.invoke(matrices);
        Matrix3f normalMatrix = (Matrix3f) bridge.normalMatrix.invoke(matrices);
        Vector3f size = (Vector3f) bridge.cubeSize.get(cube);
        for (Object quad : (Object[]) bridge.quads.get(cube)) {
            if (quad == null) {
                continue;
            }
            Vec3i sourceNormal = (Vec3i) bridge.quadNormal.get(quad);
            Vector3f normal = new Vector3f(Vec3iAccessor.x(sourceNormal),
                Vec3iAccessor.y(sourceNormal), Vec3iAccessor.z(sourceNormal));
            normalMatrix.transform(normal);
            // Match MMCE's flat-cube shading correction without dropping zero-thickness faces.
            if ((size.y == 0 || size.z == 0) && normal.x < 0) {
                normal.x = -normal.x;
            }
            if ((size.x == 0 || size.z == 0) && normal.y < 0) {
                normal.y = -normal.y;
            }
            if ((size.x == 0 || size.y == 0) && normal.z < 0) {
                normal.z = -normal.z;
            }
            Object[] quadVertices = (Object[]) bridge.quadVertices.get(quad);
            if (quadVertices.length != 4) {
                throw new IllegalArgumentException("模型包含非四边形面");
            }
            for (Object vertex : quadVertices) {
                Vector3f source = (Vector3f) bridge.position.get(vertex);
                Vector4f position = new Vector4f(source.x, source.y, source.z, 1F);
                modelMatrix.transform(position);
                target.add(position.x + 0.5F, position.y + 0.01F, position.z + 0.5F,
                    bridge.textureU.getFloat(vertex), bridge.textureV.getFloat(vertex),
                    normal.x, normal.y, normal.z);
            }
        }
    }

    private static Collection<?> collection(Object value) {
        if (!(value instanceof Collection)) {
            throw new IllegalArgumentException("GeckoLib 返回的几何层级无效");
        }
        return (Collection<?>) value;
    }

    private static final class Vertices {
        private final float[] data;
        private int used;

        private Vertices(int cubes) {
            data = new float[cubes * 6 * 4 * MmceStaticModelMesh.STRIDE];
        }

        private void add(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
            if (used + MmceStaticModelMesh.STRIDE > data.length) {
                throw new IllegalArgumentException("GeckoLib 生成的顶点数量超过预览预算");
            }
            data[used++] = x;
            data[used++] = y;
            data[used++] = z;
            data[used++] = u;
            data[used++] = v;
            data[used++] = nx;
            data[used++] = ny;
            data[used++] = nz;
        }
    }

    private static final class Bridge {
        private static final String BASE = "software.bernie.geckolib3.";
        private final Class<?> raw = load(BASE + "geo.raw.pojo.RawGeoModel");
        private final Class<?> tree = load(BASE + "geo.raw.tree.RawGeometryTree");
        private final Class<?> builderClass = load(BASE + "geo.render.GeoBuilder");
        private final Class<?> stackClass = load(BASE + "util.MatrixStack");
        private final Class<?> bone = load(BASE + "geo.render.built.GeoBone");
        private final Class<?> cube = load(BASE + "geo.render.built.GeoCube");
        private final Class<?> quad = load(BASE + "geo.render.built.GeoQuad");
        private final Class<?> vertex = load(BASE + "geo.render.built.GeoVertex");
        private final Constructor<?> builder = builderClass.getConstructor();
        private final Constructor<?> stack = stackClass.getConstructor();
        private final Method convert = load(BASE + "geo.raw.pojo.Converter").getMethod("fromJsonString", String.class);
        private final Method hierarchy = tree.getMethod("parseHierarchy", raw, ResourceLocation.class);
        private final Method construct = builderClass.getMethod("constructGeoModel", tree);
        private final Field topLevelBones = load(BASE + "geo.render.built.GeoModel").getField("topLevelBones");
        private final Field childCubes = bone.getField("childCubes");
        private final Field childBones = bone.getField("childBones");
        private final Method hidden = bone.getMethod("isHidden");
        private final Method hideChildren = bone.getMethod("childBonesAreHiddenToo");
        private final Method push = stackClass.getMethod("push");
        private final Method pop = stackClass.getMethod("pop");
        private final Method translateBone = stackClass.getMethod("translate", bone);
        private final Method pivotBone = stackClass.getMethod("moveToPivot", bone);
        private final Method rotateBone = stackClass.getMethod("rotate", bone);
        private final Method scaleBone = stackClass.getMethod("scale", bone);
        private final Method unpivotBone = stackClass.getMethod("moveBackFromPivot", bone);
        private final Method pivotCube = stackClass.getMethod("moveToPivot", cube);
        private final Method rotateCube = stackClass.getMethod("rotate", cube);
        private final Method unpivotCube = stackClass.getMethod("moveBackFromPivot", cube);
        private final Method modelMatrix = stackClass.getMethod("getModelMatrix");
        private final Method normalMatrix = stackClass.getMethod("getNormalMatrix");
        private final Field cubeSize = cube.getField("size");
        private final Field quads = cube.getField("quads");
        private final Field quadNormal = quad.getField("normal");
        private final Field quadVertices = quad.getField("vertices");
        private final Field position = vertex.getField("position");
        private final Field textureU = vertex.getField("textureU");
        private final Field textureV = vertex.getField("textureV");

        private Bridge() throws ReflectiveOperationException {
        }

        private static Class<?> load(String name) throws ClassNotFoundException {
            return Class.forName(name, false, MmceStaticModelBaker.class.getClassLoader());
        }
    }
}
