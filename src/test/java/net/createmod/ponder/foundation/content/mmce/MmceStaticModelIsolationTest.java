package net.createmod.ponder.foundation.content.mmce;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

class MmceStaticModelIsolationTest {

    @Test
    void metadataUsesOnlyDefaultRegistryAndAuditedGetters() throws IOException {
        String source = read("MmceDefaultModelLookup.java");
        assertTrue(source.contains("getMethod(\"getMachineDefaultModel\", machineClass)"));
        assertTrue(source.contains("model.getClass() != providerClass"));
        for (String forbidden : new String[] {"getCurrentModel", "getModel\"", "initializePool",
            "getRenderInstance", "setLivingAnimations", "TileEntity", "renderDummy"}) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void geometryUsesFreshBuilderAndStackWithoutNativeRendering() throws IOException {
        String source = read("MmceStaticModelBaker.java");
        assertTrue(source.contains("bridge.builder.newInstance()"));
        assertTrue(source.contains("bridge.stack.newInstance()"));
        assertTrue(source.contains("MmceGeoModelValidation.validate(json)"));
        assertTrue(source.contains("if (quad == null)"));
        for (String forbidden : new String[] {"IGeoRenderer", "MATRIX_STACK", "renderDummy",
            "ControllerModelRenderManager", "GeoModelRenderTask", "GeoModelExternalLoader",
            "getGeoBuilder", "getRenderInstance", "setLivingAnimations", "TileEntity", "GlStateManager"}) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void cacheReadsActiveResourceManagerAndClosesResources() throws IOException {
        String source = read("MmceStaticModelPreviews.java");
        assertTrue(source.contains("registerReloadListener(this)"));
        assertTrue(source.contains("try (IResource resource = resources.getResource(location)"));
        assertFalse(source.contains("new File("));
        assertFalse(source.contains("getWorld("));
        assertFalse(source.contains("getTileEntity("));
    }

    private static String read(String filename) throws IOException {
        Path path = Paths.get("src/main/java/net/createmod/ponder/foundation/content/mmce", filename);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
