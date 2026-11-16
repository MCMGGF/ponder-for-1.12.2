package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** Source contracts complement, rather than replace, native frame and interaction acceptance. */
class Alpha2ForegroundIntegrationSourceTest {
    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/net/createmod/ponder/" + path)), StandardCharsets.UTF_8);
    }
    private static String method(String source, String declaration) {
        int start = source.indexOf(declaration); assertTrue(start >= 0, declaration);
        int opening = source.indexOf('{', start), depth = 0;
        for (int i = opening; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            if (source.charAt(i) == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("Unclosed method " + declaration);
    }
    private static void before(String body, String first, String second) {
        int a = body.indexOf(first), b = body.indexOf(second);
        assertTrue(a >= 0, first); assertTrue(b >= 0, second); assertTrue(a < b, first + " must precede " + second);
    }

    @Test void editorForegroundRunsAfterGeometryAndHostControlsInsideThePreviewOnly() throws Exception {
        String body = method(source("foundation/ui/AuthoringEditorScreen.java"), "public void drawScreen(");
        before(body, "renderer.renderEditorPreview(", "super.drawScreen(");
        before(body, "super.drawScreen(", "renderer.renderEditorForeground(");
        assertTrue(body.contains("layout.preview.width>0 && !diagnosticOnly && !compiled.isEmpty()"));
        String renderer = method(source("foundation/ui/PonderDebugScreen.java"), "public void renderEditorForeground(");
        assertTrue(renderer.contains("scene != externalScene")); assertTrue(renderer.contains("PonderScreenOverlay.run("));
        before(renderer, "drawSceneSpaceOverlays(", "drawForegroundCallouts(");
        assertTrue(renderer.contains("runClippedOverlay(externalOverlayArea"));
    }

    @Test void editorCalloutClicksAndWheelsOwnInputBeforeFormsOrCameraMovement() throws Exception {
        String editor = source("foundation/ui/AuthoringEditorScreen.java");
        String click = method(editor, "protected void mouseClicked(");
        before(click, "renderer.externalCalloutClick(", "f.mouseClicked(");
        before(click, "renderer.externalCalloutClick(", "dragging=true");
        assertTrue(click.contains("playing=false; dragging=false; return;"));
        String wheel = method(editor, "public void handleMouseInput(");
        before(wheel, "renderer.externalCalloutWheel(", "super.handleMouseInput(");
        before(wheel, "renderer.externalCalloutWheel(", "zoom=");
        String drag = method(editor, "protected void mouseClickMove(");
        before(drag, "externalCalloutContains(", "yaw+=");
    }

    @Test void reinitializationCloseAndRejectedRecompileCannotRetainInvisibleInputOrProviders() throws Exception {
        String editor = source("foundation/ui/AuthoringEditorScreen.java");
        assertTrue(method(editor, "public void initGui(").contains("renderer.invalidateExternalPreview()"));
        assertTrue(method(editor, "public void onGuiClosed(").contains("renderer.releaseProjectionResources()"));
        String compile = method(editor, "private void compileDraft(");
        before(compile, "renderer.invalidateExternalPreview()", "workspace.getCompiler().validate(");
    }

    @Test void snapshotProvidersAreTrackedBeforePotentiallyFailingResolveAndReleasedBySceneClear() throws Exception {
        String renderer = source("foundation/ui/PonderDebugScreen.java");
        String resolve = method(renderer, "private PonderGuiSnapshotRegistry.Snapshot resolveSnapshot(");
        before(resolve, "usedSnapshotProviders.add(", "PonderGuiSnapshotRegistry.resolve(");
        String release = method(renderer, "private void releaseSnapshotProviders(");
        before(release, "PonderGuiSnapshotRegistry.releaseProvider(", "usedSnapshotProviders.clear()");
        assertTrue(method(renderer, "private void clearPreviewWorld(").contains("releaseSnapshotProviders()"));
        String prepare = method(renderer, "private void prepareExternalContext(");
        assertTrue(prepare.contains("externalScene != scene"));
        before(prepare, "invalidateExternalPreview()", "externalScene = scene");
        String invalidate = method(renderer, "public void invalidateExternalPreview(");
        before(invalidate, "modelFraming.copy()", "clearPreviewWorld()");
        before(invalidate, "clearPreviewWorld()", "modelFraming.copyFrom(framing)");
        assertTrue(method(renderer, "public void releaseProjectionResources(").contains("clearPreviewWorld()"));
    }

    @Test void triggerReadStateRequiresTheActuallyDisplayedViewerAndRuntimeSceneIdentity() throws Exception {
        String client = method(source("client/ClientProxy.java"), "private void tickClient(");
        before(client, "minecraft.displayGuiScreen(next)", "AuthoringTriggerManager.markOpened(");
        assertTrue(client.contains("minecraft.currentScreen == next"));
        before(client, ".isShowingScene(requestedHint)", "AuthoringTriggerManager.markOpened(");
        String showing = method(source("foundation/ui/PonderDebugScreen.java"), "public boolean isShowingScene(");
        assertTrue(showing.contains("sceneId.equals(selected.getSceneId())"));
        assertFalse(showing.contains("getSchematicLocation()"));
    }

    @Test void worldParticlesReadDepthDoNotWriteItAndRestoreStateWithoutSpawningParticles() throws Exception {
        String renderer = source("foundation/ui/PonderDebugScreen.java");
        String particles = method(renderer, "private void renderWorldParticles(");
        assertTrue(particles.contains("PreviewParticleTimeline.evaluate(scene"));
        assertTrue(particles.contains("GlStateManager.enableDepth()"));
        assertTrue(particles.contains("GlStateManager.depthMask(false)"));
        assertTrue(particles.contains("finally { previous.restore(); }"));
        assertFalse(particles.contains("spawnParticle(")); assertFalse(particles.contains("glClear("));
        String shared = method(renderer, "private void renderSharedSceneGeometry(");
        assertTrue(shared.contains("if (!showOccluded) renderWorldParticles("));
        assertFalse(shared.contains("glClear("));
        String world = method(renderer, "public void renderWorldProjection(");
        assertFalse(world.contains("glClear("));
        assertTrue(world.contains("renderSharedSceneGeometry(scene, frame, tick, false)"));
    }

    @Test void screenAndWorldParticlesShareTheTimelineButNotTheScreenDepthSetup() throws Exception {
        String renderer = source("foundation/ui/PonderDebugScreen.java");
        assertTrue(method(renderer, "private void drawParticleEffectsOverlay(").contains("PreviewParticleTimeline.evaluate(event"));
        String legacy = method(renderer, "public void renderWorldForeground(");
        assertTrue(legacy.contains("renderWorldForeground(scene, tick, projection, area, null)"));
        String world = method(renderer, "void renderWorldForeground(PonderScene scene, float tick, PreviewWorldProjection projection,\n"
            + "        PreviewDebugLayout.Rect area, ProjectorReadingLayout reading)");
        assertTrue(world.contains("withForegroundReadingCanvas(reading"));
        assertTrue(world.contains("worldProjection = reading == null ? projection : projection.withLogicalSize(width, height)"));
        assertTrue(world.contains("PonderScreenOverlay.run(")); assertTrue(world.contains("drawForegroundCallouts("));
        before(world, "worldProjection =", "PonderScreenOverlay.run(");
        String budget = method(source("foundation/ui/ProjectorSceneRenderer.java"), "public static boolean withinBudget(");
        assertTrue(budget.contains("scene.getParticleEvents().size() > 256") || budget.contains("PreviewParticleTimeline.MAX_SCENE_EVENTS"));
    }
}
