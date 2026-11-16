package net.createmod.ponder.authoring.exchange;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.createmod.ponder.authoring.AuthoringCompiler;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.AuthoringValidation;
import net.createmod.ponder.foundation.PonderSchematic;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

/** Explicit offline context exchange; no network, credentials, remote URLs or filesystem discovery. */
public final class OfflineAiExchange {
    public static final class RegistryContext {
        private final Set<String> blocks, items;
        public RegistryContext(Collection<String> blocks, Collection<String> items) {
            this.blocks = ids(blocks); this.items = ids(items);
        }
        public static RegistryContext snapshotLoadedRegistries() {
            Set<String> blocks = new LinkedHashSet<>(), items = new LinkedHashSet<>();
            for (ResourceLocation id : Block.REGISTRY.getKeys()) blocks.add(id.toString());
            for (ResourceLocation id : Item.REGISTRY.getKeys()) items.add(id.toString());
            return new RegistryContext(blocks, items);
        }
        private static Set<String> ids(Collection<String> source) {
            if (source == null || source.size() > 100000) throw new IllegalArgumentException("Explicit bounded registry IDs are required");
            Set<String> result = new LinkedHashSet<>();
            for (String value : source) {
                if (!AuthoringDocument.validId(value)) throw new IllegalArgumentException("Invalid registry ID"); result.add(value);
            }
            return java.util.Collections.unmodifiableSet(result);
        }
    }
    public static final class DraftResult {
        public final AuthoringDocument document;
        public final AuthoringValidation validation;
        DraftResult(AuthoringDocument document, AuthoringValidation validation) { this.document = document; this.validation = validation; }
    }
    private OfflineAiExchange() { }

    public static String exportPrompt(String instruction, AuthoringDocument draft, RegistryContext registry,
        Map<String, PonderSchematic> explicitStructures) {
        return exportPrompt(instruction, draft, registry, explicitStructures, new AuthoringCompiler());
    }

    public static String exportPrompt(String instruction, AuthoringDocument draft, RegistryContext registry,
        Map<String, PonderSchematic> explicitStructures, AuthoringCompiler compiler) {
        if (instruction == null || instruction.length() > 32768 || registry == null || explicitStructures == null
            || explicitStructures.size() > 4096 || compiler == null)
            throw new IllegalArgumentException("Bounded instruction, registry, explicit structure map and compiler are required");
        JsonObject context = new JsonObject(); context.addProperty("formatVersion", 1); context.addProperty("targetVersion", "1.12.2");
        context.addProperty("instruction", instruction); context.addProperty("offlineOnly", true);
        context.add("blockIds", array(registry.blocks)); context.add("itemIds", array(registry.items));
        JsonArray capabilities = new JsonArray();
        for (AuthoringValidation.Capability capability : compiler.getCapabilities().values()) {
            JsonObject value = new JsonObject(); value.addProperty("type", capability.getType()); value.addProperty("status", capability.getStatus().name());
            value.addProperty("reason", capability.getReason()); capabilities.add(value);
        }
        context.add("capabilities", capabilities);
        JsonArray structures = new JsonArray();
        long summarizedBlocks = 0;
        for (Map.Entry<String, PonderSchematic> entry : explicitStructures.entrySet()) {
            if (!AuthoringDocument.validId(entry.getKey()) || entry.getValue() == null) throw new IllegalArgumentException("Explicit structure IDs and data are required");
            PonderSchematic source = entry.getValue(); summarizedBlocks += source.getBlocks().size();
            if (source.getBlocks().size() > 262144 || summarizedBlocks > 1048576) throw new IllegalArgumentException("Explicit structure context exceeds the summary budget");
            JsonObject value = new JsonObject(); value.addProperty("id", entry.getKey());
            JsonArray size = new JsonArray(); size.add(source.getSize().getX()); size.add(source.getSize().getY()); size.add(source.getSize().getZ());
            value.add("size", size); value.addProperty("blockCount", source.getBlocks().size());
            Set<String> palette = new LinkedHashSet<>();
            for (IBlockState state : source.getBlocks().values()) palette.add(String.valueOf(state.getBlock().getRegistryName()));
            value.add("palette", array(palette)); value.addProperty("tileNbtOmitted", true); structures.add(value);
        }
        context.add("structures", structures);
        if (draft != null) context.add("draft", draft.toJson());
        String output = "Return one JSON authoring document, not JavaScript or Markdown. Use only the supplied 1.12.2 IDs and local structure IDs. "
            + "Preserve unsupported steps as draft data and describe required conversions; never invent successful execution. "
            + "Do not request API keys or fetch resources. All changes are preview-only until explicit validation and publication.\n" + context;
        if (output.length() > 8 * 1024 * 1024) throw new IllegalArgumentException("Context exceeds eight MiB text budget");
        return output;
    }

    public static DraftResult validateDraft(String json, AuthoringCompiler compiler, AuthoringCompiler.AssetResolver resolver) {
        if (compiler == null) throw new IllegalArgumentException("Explicit compiler is required");
        try {
            AuthoringDocument document = AuthoringDocument.parse(json);
            return new DraftResult(document, compiler.validate(document, resolver));
        } catch (RuntimeException malformed) {
            return new DraftResult(null, new AuthoringValidation.Builder().error("exchange.json", "/", String.valueOf(malformed.getMessage())).build());
        }
    }
    private static JsonArray array(Collection<String> values) { JsonArray result = new JsonArray(); for (String value : values) result.add(value); return result; }
}
