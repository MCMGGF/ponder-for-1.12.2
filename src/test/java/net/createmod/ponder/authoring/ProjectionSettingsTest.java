package net.createmod.ponder.authoring;

import net.createmod.ponder.authoring.projection.ProjectionSettings;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectionSettingsTest {
    @Test void validatesAndRoundTripsOnlyOwnedState() {
        ProjectionSettings p=new ProjectionSettings("ponder_authoring:local/a/main","minecraft:stone",false,true,2,-45,0.125,ProjectionSettings.Trigger.POWERED);
        ProjectionSettings copy=ProjectionSettings.fromNbt(p.toNbt(),false);
        assertEquals(p.sceneId,copy.sceneId); assertEquals(p.componentId,copy.componentId);
        assertEquals(315,copy.yaw); assertEquals(2,copy.speed); assertEquals(p.trigger,copy.trigger);
        assertEquals(5,p.tick(25,20));
    }
    @Test void invalidPersistedDataFallsBackRatherThanUsingUnboundedRenderValues() {
        NBTTagCompound invalid=new NBTTagCompound(); invalid.setDouble("Scale",Double.NaN);
        assertEquals(0.125,ProjectionSettings.fromNbt(invalid,true).scale);
        assertThrows(IllegalArgumentException.class,()->new ProjectionSettings("x:a",false,false,0,0,1,null));
        assertThrows(IllegalArgumentException.class,()->new ProjectionSettings("x:a",false,false,1,0,100,null));
        assertThrows(IllegalArgumentException.class,()->new ProjectionSettings("x:a",false,false,1,Double.POSITIVE_INFINITY,1,null));
        assertThrows(IllegalArgumentException.class,()->new ProjectionSettings("https://example",false,false,1,0,1,null));
    }
    @Test void nonLoopingClockStopsAtSceneEnd() {
        ProjectionSettings p=new ProjectionSettings("x:a",false,false,1,0,1,null);
        assertEquals(20,p.tick(25,20)); assertEquals(0,p.tick(-5,20));
    }
}
