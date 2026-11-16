package net.createmod.ponder.authoring.exchange;

import static org.junit.jupiter.api.Assertions.*;
import net.createmod.ponder.authoring.AuthoringDocument;
import org.junit.jupiter.api.Test;

class JavaImageExportContractTest {
    private AuthoringDocument document(String step) {
        return AuthoringDocument.parse("{\"id\":\"local:export\",\"items\":[\"minecraft:stone\"],\"imageMappings\":{\"modern/front.png\":\"local:front\"},\"scenes\":[{\"id\":\"main\",\"steps\":[" + step + "]}]}");
    }
    @Test void imageBridgeRequiresExplicitRegisteredCompilerAndReferencedAssetsWithoutExecutingAnything() {
        String source = JavaSceneTextExporter.exportText(document("{\"type\":\"show_image\",\"image\":\"modern/front.png\",\"duration\":80}"), "local.ponder", "ImageScene");
        assertTrue(source.contains("ImageStepHandlers.register(compiler, new ImageAssetPool(authoringRoot))"));
        assertTrue(source.contains("bundle referenced PNGs and explicit imageMappings"));
        assertTrue(source.contains("compile(AuthoringCompiler compiler, AuthoringCompiler.AssetResolver assets)"));
        assertTrue(source.contains("return compiler.compile(document(), assets);"));
        assertTrue(source.contains("modern/front.png")); assertTrue(source.contains("local:front"));
        assertFalse(source.contains("new AuthoringCompiler()")); assertFalse(source.contains("new ImageAssetPool(authoringRoot));\n"));
        assertFalse(source.contains("Files.read")); assertFalse(source.contains("java.net"));
    }
    @Test void nonImageExportKeepsTheExistingBridgeContractWithoutAnImageRegistrationClaim() {
        String source = JavaSceneTextExporter.exportText(document("{\"type\":\"idle\",\"duration\":20}"), "local.ponder", "IdleScene");
        assertFalse(source.contains("// show_image requires"));
        assertTrue(source.contains("Caller supplies local assets/handlers"));
        assertTrue(source.contains("return compiler.compile(document(), assets);"));
    }
}
