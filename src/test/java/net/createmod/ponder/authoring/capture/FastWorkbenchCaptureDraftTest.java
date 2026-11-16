package net.createmod.ponder.authoring.capture;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonObject;
import net.createmod.ponder.authoring.practice.PracticeDefinition;
import net.createmod.ponder.authoring.practice.PracticeDefinition.Menu;
import net.createmod.ponder.authoring.practice.PracticeJson;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Detached synthetic metadata/privacy fixtures, not a constructed FastBench container. */
class FastWorkbenchCaptureDraftTest {
    @BeforeAll static void bootstrap(){Bootstrap.register();}
    private Map<String,String> metadata(){Map<String,String> value=new LinkedHashMap<>();value.put("sourceAdapter",FastWorkbenchCaptureAdapter.ADAPTER_ID);
        value.put("sourceModId","fastbench");value.put("sourceModVersion","1.7.4");value.put("sourceJarSha256",FastWorkbenchCaptureAdapter.JAR_SHA256);
        value.put("sourceGuiClass",FastWorkbenchCaptureAdapter.GUI);value.put("sourceContainerClass",FastWorkbenchCaptureAdapter.CLIENT);return value;}
    private List<ItemStack> slots(){return new ArrayList<>(Collections.nCopies(10,ItemStack.EMPTY));}
    private CaptureDraft draft(List<ItemStack> slots,Map<String,String> metadata){return new CaptureDraft(Menu.WORKBENCH,slots,Collections.emptyMap(),FastWorkbenchCaptureAdapter.SOURCE_GUI_ID,metadata);}
    @Test void supportedReplacementRetainsActualIdentityButUsesDetachedVanillaMenu(){
        CaptureDraft capture=draft(slots(),metadata());JsonObject step=capture.toStep(),source=step.getAsJsonObject("capture");
        assertEquals("fastbench:workbench",capture.getSourceGuiId());assertEquals("already_open_supported_replacement_menu",source.get("source").getAsString());
        for(Map.Entry<String,String> entry:metadata().entrySet())assertEquals(entry.getValue(),source.get(entry.getKey()).getAsString());
        assertEquals("minecraft:crafting_table",source.get("virtualMenuAdapter").getAsString());
        PracticeDefinition definition=PracticeJson.fromStep(step);assertEquals(Menu.WORKBENCH,definition.getMenu());assertEquals(46,definition.getSlotCount());
        for(int i=10;i<46;i++)assertTrue(definition.getInitialSlots().get(i).isEmpty());
    }
    @Test void replacementSnapshotFiltersNbtAndPreservesOnlyIdCountMeta(){
        List<ItemStack> values=slots();ItemStack source=new ItemStack(Blocks.WOOL,7,14);NBTTagCompound nbt=new NBTTagCompound();nbt.setString("OwnerUUID","private-value");source.setTagCompound(nbt);values.set(9,source);
        CaptureDraft capture=draft(values,metadata());assertEquals(1,capture.getFilteredTagCount());assertEquals(7,capture.getSlots().get(9).count);assertEquals(14,capture.getSlots().get(9).meta);
        assertFalse(capture.toStep().toString().contains("private-value"));assertFalse(capture.getSlots().get(9).toStack().hasTagCompound());assertEquals(nbt,source.getTagCompound());
        source.setCount(1);assertEquals(7,capture.getSlots().get(9).count);
    }
    @Test void originIsImmutableAndDoesNotRetainCallersMapOrPaths(){
        Map<String,String> values=metadata();CaptureDraft capture=draft(slots(),values);values.put("sourceModVersion","untrusted");
        assertEquals("1.7.4",capture.getSourceMetadata().get("sourceModVersion"));assertThrows(UnsupportedOperationException.class,()->capture.getSourceMetadata().put("path","outside"));
        JsonObject first=capture.toStep();first.getAsJsonObject("capture").addProperty("sourceModVersion","changed");assertEquals("1.7.4",capture.toStep().getAsJsonObject("capture").get("sourceModVersion").getAsString());
        assertFalse(capture.toStep().toString().contains("C:\\"));
    }
    @Test void legacyPublicConstructorCannotClaimAnUnverifiedReplacement(){
        assertThrows(IllegalArgumentException.class,()->new CaptureDraft(Menu.WORKBENCH,slots(),Collections.emptyMap(),FastWorkbenchCaptureAdapter.SOURCE_GUI_ID));
        CaptureDraft old=new CaptureDraft(Menu.WORKBENCH,slots(),Collections.emptyMap());assertTrue(old.getSourceMetadata().isEmpty());
        assertEquals("already_open_vanilla_menu",old.toStep().getAsJsonObject("capture").get("source").getAsString());
    }
    @Test void missingExtraAndChangedOriginFieldsAreRejected(){
        for(String key:metadata().keySet()){
            Map<String,String> missing=metadata();missing.remove(key);assertThrows(IllegalArgumentException.class,()->draft(slots(),missing));
            Map<String,String> wrong=metadata();wrong.put(key,"untrusted");assertThrows(IllegalArgumentException.class,()->draft(slots(),wrong));
        }
        Map<String,String> extra=metadata();extra.put("privatePath","not-exported");assertThrows(IllegalArgumentException.class,()->draft(slots(),extra));
        assertThrows(IllegalArgumentException.class,()->draft(slots(),null));
    }
    @Test void replacementOriginCannotBeAppliedToAnotherMenuOrVanillaSource(){
        assertThrows(IllegalArgumentException.class,()->new CaptureDraft(Menu.FURNACE,Collections.nCopies(3,ItemStack.EMPTY),Collections.emptyMap(),FastWorkbenchCaptureAdapter.SOURCE_GUI_ID,metadata()));
        assertThrows(IllegalArgumentException.class,()->new CaptureDraft(Menu.WORKBENCH,slots(),Collections.emptyMap(),"minecraft:crafting_table",metadata()));
    }
}
