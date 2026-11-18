package net.createmod.ponder.authoring;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.init.Items;
import net.minecraft.util.ResourceLocation;

/** Published drafts join the existing transactional index, with their own namespace. */
public final class AuthoringPlugin implements PonderPlugin {
    public static final ResourceLocation TAG = Ponder.asResource("local_authoring");
    public static final int MAX_PUBLISHED_DOCUMENTS = 64;
    public static final long MAX_PUBLISHED_JSON_BYTES = 16L * 1024 * 1024;
    public static final int MAX_REGISTERED_BINDINGS = 256;
    private List<AuthoringDocument> reloadDocuments;
    private List<net.createmod.ponder.authoring.trigger.TriggerDefinition> reloadTriggers;
    @Override public String getModId() { return "ponder_authoring"; }
    @Override public void onPonderReloadStart() {
        if (!AuthoringSettings.isEnabled()) return;
        reloadDocuments = new ArrayList<>();
        reloadTriggers = new ArrayList<>();
        AuthoringWorkspace w = AuthoringWorkspace.get();
        try {
            List<String> ids = w.getStore().listPublishedIds();
            if (ids.size() > MAX_PUBLISHED_DOCUMENTS) throw new IOException("More than 64 published local documents");
            long bytes = 0, bindings = 0;
            java.util.Set<String> executable = new java.util.HashSet<>();
            w.getCompiler().getCapabilities().forEach((type, capability) -> {
                if (capability.getStatus() != AuthoringValidation.Status.UNSUPPORTED) executable.add(type);
            });
            for (String id : ids) {
                AuthoringDocument doc = w.getStore().loadPublished(id).getDocument();
                bytes += doc.toJson().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (bytes > MAX_PUBLISHED_JSON_BYTES) throw new IOException("Published JSON exceeds 16 MiB aggregate budget");
                bindings += (long) doc.getItems().size() * doc.getScenes().size();
                if (bindings > MAX_REGISTERED_BINDINGS) throw new IOException("More than 256 local storyboard/item bindings");
                // Registration must not compile timelines or repeatedly decode structure NBT at world entry.
                AuthoringValidation validation = AuthoringValidation.validate(doc, executable);
                if (!validation.canPublish()) throw new IllegalStateException("Invalid published draft " + id + ": " + validation.getIssues());
                AuthoringItemBindings.resolve(doc);
                reloadDocuments.add(doc);
                net.createmod.ponder.authoring.trigger.TriggerDefinition trigger =
                    net.createmod.ponder.authoring.trigger.TriggerDefinition.parse(doc);
                if (trigger != null) reloadTriggers.add(trigger);
            }
        } catch (IOException invalid) { throw new IllegalStateException("Cannot read local published scenes", invalid); }
    }
    @Override public void onPonderReloadEnd(boolean successful) {
        if (successful && reloadTriggers != null)
            net.createmod.ponder.authoring.trigger.TriggerRegistry.replacePublished(reloadTriggers);
        reloadDocuments = null; reloadTriggers = null;
    }
    @Override public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        if (!AuthoringSettings.isEnabled() || reloadDocuments == null) return;
        AuthoringWorkspace w = AuthoringWorkspace.get();
        for (AuthoringDocument doc : reloadDocuments) {
            Map<ResourceLocation, List<AuthoringItemBindings.Binding>> grouped = new LinkedHashMap<>();
            for (AuthoringItemBindings.Binding binding : AuthoringItemBindings.resolve(doc))
                grouped.computeIfAbsent(binding.getItemId(), id -> new ArrayList<>()).add(binding);
            for (Map.Entry<ResourceLocation, List<AuthoringItemBindings.Binding>> group : grouped.entrySet()) {
                final List<AuthoringItemBindings.Binding> filters = group.getValue();
                for (int i = 0; i < doc.getScenes().size(); i++) {
                    final int index = i;
                    helper.addStoryBoard(group.getKey(), doc.getRuntimeSceneId(i),
                        (scene, util) -> w.getCompiler().storyBoard(doc, index, w.getAssets()).program(scene, util), TAG)
                        .withStackFilter(stack -> filters.stream().anyMatch(binding -> binding.test(stack)));
                }
            }
        }
    }
    @Override public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        if (!AuthoringSettings.isEnabled()) return;
        helper.registerTag(TAG).title("\u672c\u5730\u521b\u4f5c").description("\u5df2\u53d1\u5e03\u7684\u672c\u5730\u6559\u5b66")
            .item(Items.WRITABLE_BOOK).addToIndex().register();
    }
}
