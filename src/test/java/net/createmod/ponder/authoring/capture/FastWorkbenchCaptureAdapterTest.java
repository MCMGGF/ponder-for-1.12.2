package net.createmod.ponder.authoring.capture;

import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic provenance policy tests only, not an actual installed FastBench GUI acceptance. */
class FastWorkbenchCaptureAdapterTest {
    @TempDir Path directory;
    private boolean accepted(String gui,String client,String base,String output,String version,String hash,boolean sameSource){
        return FastWorkbenchCaptureAdapter.exactPolicy(gui,client,base,output,version,hash,sameSource);
    }
    @Test void exactAuditedPolicyIsAcceptedButNotClaimedAsNativeEvidence(){
        assertTrue(accepted(FastWorkbenchCaptureAdapter.GUI,FastWorkbenchCaptureAdapter.CLIENT,FastWorkbenchCaptureAdapter.BASE,
            FastWorkbenchCaptureAdapter.OUTPUT,"1.7.4",FastWorkbenchCaptureAdapter.JAR_SHA256,true));
    }
    @Test void namesAloneCannotAuthorizeAReplacement(){
        assertFalse(accepted(FastWorkbenchCaptureAdapter.GUI,FastWorkbenchCaptureAdapter.CLIENT,FastWorkbenchCaptureAdapter.BASE,
            FastWorkbenchCaptureAdapter.OUTPUT,"1.7.4",FastWorkbenchCaptureAdapter.JAR_SHA256,false));
    }
    @Test void arbitraryClassNamesOrSubclassNamesAreNotAccepted(){
        String[] expected={FastWorkbenchCaptureAdapter.GUI,FastWorkbenchCaptureAdapter.CLIENT,FastWorkbenchCaptureAdapter.BASE,FastWorkbenchCaptureAdapter.OUTPUT};
        for(int i=0;i<expected.length;i++){String[] names=expected.clone();names[i]+="$Untrusted";
            assertFalse(accepted(names[0],names[1],names[2],names[3],"1.7.4",FastWorkbenchCaptureAdapter.JAR_SHA256,true));}
    }
    @Test void unknownVersionAndDifferentJarAreExplicitlyRejected(){
        for(String version:new String[]{"1.7.3","1.7.5","1.12.2-1.7.4","","1.7.4-custom"})
            assertFalse(accepted(FastWorkbenchCaptureAdapter.GUI,FastWorkbenchCaptureAdapter.CLIENT,FastWorkbenchCaptureAdapter.BASE,
                FastWorkbenchCaptureAdapter.OUTPUT,version,FastWorkbenchCaptureAdapter.JAR_SHA256,true));
        assertFalse(accepted(FastWorkbenchCaptureAdapter.GUI,FastWorkbenchCaptureAdapter.CLIENT,FastWorkbenchCaptureAdapter.BASE,
            FastWorkbenchCaptureAdapter.OUTPUT,"1.7.4",new String(new char[64]).replace('\0','0'),true));
    }
    @Test void missingClientObjectsNeverInitializeThirdPartyContainers(){
        assertNull(FastWorkbenchCaptureAdapter.resolve(null));
        assertFalse(FastWorkbenchCaptureAdapter.supports(null,null));
    }
    @Test void cleanroomClassResourceCodeSourceResolvesOnlyItsLocalJar() throws Exception {
        File source=directory.resolve("Fast Workbench.jar").toFile().getCanonicalFile();
        URL file=source.toURI().toURL();
        URL resource=new URL("jar:"+file.toExternalForm()+"!/"+FastWorkbenchCaptureAdapter.GUI.replace('.','/')+".class");
        assertEquals(source,FastWorkbenchCaptureAdapter.localSource(file,FastWorkbenchCaptureAdapter.GUI));
        assertEquals(source,FastWorkbenchCaptureAdapter.localSource(resource,FastWorkbenchCaptureAdapter.GUI));
        assertEquals(source,FastWorkbenchCaptureAdapter.localSource(new URL("jar:"+file.toExternalForm()+"!/"),FastWorkbenchCaptureAdapter.GUI));
        assertFalse(source.exists(),"Parsing provenance must not open, create or download a JAR");
    }
    @Test void wrongEntryAndTraversalNeverAuthorizeAnotherClass() throws Exception {
        String jar="jar:"+directory.resolve("FastWorkbench.jar").toUri().toURL().toExternalForm()+"!/";
        for(String entry:new String[]{"other/GuiFastBench.class","shadows/fastbench/gui/../gui/GuiFastBench.class",
            "/shadows/fastbench/gui/GuiFastBench.class","shadows/fastbench/gui/GuiFastBench.class?raw=1"})
            assertNull(FastWorkbenchCaptureAdapter.localSource(new URL(jar+entry),FastWorkbenchCaptureAdapter.GUI));
    }
    @Test void remoteJarAndNonFileSourcesAreRejectedWithoutConnecting() throws Exception {
        for(String source:new String[]{"https://example.invalid/FastWorkbench.jar","file://example.invalid/FastWorkbench.jar",
            "jar:https://example.invalid/FastWorkbench.jar!/shadows/fastbench/gui/GuiFastBench.class",
            "jar:file://example.invalid/FastWorkbench.jar!/shadows/fastbench/gui/GuiFastBench.class"}) {
            try {
                URL url=new URL(source);
                assertNull(FastWorkbenchCaptureAdapter.localSource(url,FastWorkbenchCaptureAdapter.GUI));
            } catch (MalformedURLException rejectedByJdk) {
                // Recent JDKs reject non-local file URLs before the adapter can see them.
                assertTrue(source.startsWith("file://") || source.startsWith("jar:file://"));
            }
        }
    }
    @Test void fragmentsAndFileQueriesAreNotProvenance() throws Exception {
        String file=directory.resolve("FastWorkbench.jar").toUri().toURL().toExternalForm();
        assertNull(FastWorkbenchCaptureAdapter.localSource(new URL(file+"?x=1"),FastWorkbenchCaptureAdapter.GUI));
        assertNull(FastWorkbenchCaptureAdapter.localSource(new URL(file+"#other"),FastWorkbenchCaptureAdapter.GUI));
        assertNull(FastWorkbenchCaptureAdapter.localSource(new URL("jar:"+file+"!/shadows/fastbench/gui/GuiFastBench.class#other"),FastWorkbenchCaptureAdapter.GUI));
        assertNull(FastWorkbenchCaptureAdapter.localSource(null,FastWorkbenchCaptureAdapter.GUI));
        assertNull(FastWorkbenchCaptureAdapter.localSource(new URL(file),null));
    }
}
