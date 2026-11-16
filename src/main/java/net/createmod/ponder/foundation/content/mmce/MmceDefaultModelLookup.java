package net.createmod.ponder.foundation.content.mmce;

import java.lang.reflect.Method;

import net.minecraft.util.ResourceLocation;

/** Only the audited MMCE registry lookup and four no-argument field getters. */
final class MmceDefaultModelLookup {

    private static final String BASE = "github.kasuminova.mmce.client.model.";
    private final Class<?> providerClass = load(BASE + "MachineControllerModel");
    private final Class<?> registryClass = load(BASE + "DynamicMachineModelRegistry");
    private final Class<?> machineClass = load("hellfirepvp.modularmachinery.common.machine.DynamicMachine");
    private final Method defaultModel = registryClass.getMethod("getMachineDefaultModel", machineClass);
    private final Method name = providerClass.getMethod("getModelName");
    private final Method geometry = providerClass.getMethod("getModelLocation");
    private final Method texture = providerClass.getMethod("getTextureLocation");
    private final Method animation = providerClass.getMethod("getAnimationFileLocation");

    MmceDefaultModelLookup() throws ReflectiveOperationException {
    }

    MmceStaticModelSpec lookup(Object machine) throws ReflectiveOperationException {
        Object registry = registryClass.getField("INSTANCE").get(null);
        Object model = defaultModel.invoke(registry, machine);
        if (model == null) {
            return MmceStaticModelSpec.unavailable("此机器没有注册默认 Geo 模型，当前显示方块结构");
        }
        if (model.getClass() != providerClass) {
            return MmceStaticModelSpec.unavailable("此机器使用尚未审核的模型提供器，当前显示方块结构");
        }
        Object modelName = name.invoke(model);
        if (!(modelName instanceof String)) {
            return MmceStaticModelSpec.unavailable("默认模型名称无效，当前显示方块结构");
        }
        return MmceStaticModelSpec.bound((String) modelName, resource(geometry.invoke(model), false),
            resource(texture.invoke(model), false), resource(animation.invoke(model), true));
    }

    private static String resource(Object value, boolean optional) {
        if (value == null && optional) {
            return "";
        }
        if (!(value instanceof ResourceLocation) || value.getClass() != ResourceLocation.class) {
            throw new IllegalArgumentException("默认模型未提供有效的资源标识");
        }
        return value.toString();
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, false, MmceDefaultModelLookup.class.getClassLoader());
    }
}
