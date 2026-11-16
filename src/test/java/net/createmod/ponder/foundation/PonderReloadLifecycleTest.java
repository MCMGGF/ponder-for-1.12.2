package net.createmod.ponder.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.createmod.ponder.CommonProxy;
import net.createmod.ponder.PonderBackportMod;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.SceneRegistryAccess;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.createmod.ponder.client.ClientProxy;
import net.createmod.ponder.foundation.content.mmce.MmceStaticModelPreviews;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PonderReloadLifecycleTest {

    private CommonProxy originalProxy;
    private Object originalRegistry;
    private long originalGeneration;
    private List<PonderPlugin> originalPlugins;

    @BeforeAll
    static void registerBlocks() {
        Bootstrap.register();
    }

    @BeforeEach
    void isolateRegistry() throws Exception {
        originalProxy = PonderBackportMod.proxy;
        originalRegistry = field("active").get(null);
        originalGeneration = PonderIndex.getGeneration();
        originalPlugins = new ArrayList<PonderPlugin>(plugins());
        plugins().clear();
    }

    @AfterEach
    void restoreRegistry() throws Exception {
        PonderBackportMod.proxy = originalProxy;
        field("active").set(null, originalRegistry);
        field("generation").setLong(null, originalGeneration);
        plugins().clear();
        plugins().addAll(originalPlugins);
    }

    @Test
    void successfulReloadPublishesBeforeReleasingClientCaches() throws Exception {
        SceneRegistryAccess before = PonderIndex.getSceneAccess();
        List<SceneRegistryAccess> observed = new ArrayList<SceneRegistryAccess>();
        ThreadLocal<?> building = (ThreadLocal<?>) field("BUILDING").get(null);
        PonderBackportMod.proxy = new CommonProxy() {
            @Override
            public void onPonderReload() {
                assertNull(building.get());
                observed.add(PonderIndex.getSceneAccess());
                assertEquals(originalGeneration + 1, PonderIndex.getGeneration());
            }
        };

        PonderIndex.reload();

        assertEquals(1, observed.size());
        assertNotSame(before, observed.get(0));
        assertSame(PonderIndex.getSceneAccess(), observed.get(0));
    }

    @Test
    void failedReloadRetainsThePublishedRegistryButStillReleasesOptionalCaches() {
        SceneRegistryAccess before = PonderIndex.getSceneAccess();
        List<SceneRegistryAccess> observed = new ArrayList<SceneRegistryAccess>();
        PonderIndex.addPlugin(new PonderPlugin() {
            @Override
            public String getModId() {
                return "reload_test";
            }

            @Override
            public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
                throw new IllegalStateException("Injected registry failure");
            }
        });
        PonderBackportMod.proxy = new CommonProxy() {
            @Override
            public void onPonderReload() {
                observed.add(PonderIndex.getSceneAccess());
            }
        };

        PonderIndex.reload();

        assertEquals(1, observed.size());
        assertSame(before, observed.get(0));
        assertSame(before, PonderIndex.getSceneAccess());
        assertEquals(originalGeneration, PonderIndex.getGeneration());
    }

    @Test
    void clientReloadInvalidatesMeshesWithoutAClientContextOrOptionalMods() {
        MmceStaticModelPreviews previews = MmceStaticModelPreviews.getInstance();
        long before = previews.getResourceGeneration();

        new ClientProxy().onPonderReload();

        assertEquals(before + 1, previews.getResourceGeneration());
        assertEquals(0, previews.getCachedCount());
        assertEquals(0, previews.getCachedBytes());
    }

    @Test
    void serverAndBootstrapReloadDoNotRequireAClientProxy() {
        PonderBackportMod.proxy = null;
        PonderIndex.reload();
        PonderBackportMod.proxy = new CommonProxy();
        PonderIndex.reload();
        assertEquals(originalGeneration + 2, PonderIndex.getGeneration());
    }

    @Test
    void pluginHooksBracketRegistrationAndObserveThePublishedIndex() throws Exception {
        PonderBackportMod.proxy = null;
        List<String> calls = new ArrayList<String>();
        ThreadLocal<?> building = (ThreadLocal<?>) field("BUILDING").get(null);
        PonderIndex.addPlugin(new PonderPlugin() {
            @Override
            public String getModId() {
                return "lifecycle_test";
            }

            @Override
            public void onPonderReloadStart() {
                calls.add("start");
            }

            @Override
            public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
                calls.add("scenes");
            }

            @Override
            public void registerSharedText(SharedTextRegistrationHelper helper) {
                calls.add("text");
            }

            @Override
            public void onPonderReloadEnd(boolean successful) {
                assertNull(building.get());
                assertEquals(originalGeneration + 1, PonderIndex.getGeneration());
                calls.add("end:" + successful);
            }
        });

        PonderIndex.reload();

        assertEquals(Arrays.asList("start", "scenes", "text", "end:true"), calls);
    }

    @Test
    void failingStartUnwindsStartedPluginsInReverseAndDoesNotRunRegistration() {
        PonderBackportMod.proxy = null;
        List<String> calls = new ArrayList<String>();
        for (String name : Arrays.asList("a", "b", "c")) {
            PonderIndex.addPlugin(new PonderPlugin() {
                @Override
                public String getModId() {
                    return name;
                }

                @Override
                public void onPonderReloadStart() {
                    calls.add("start:" + name);
                    if ("b".equals(name)) {
                        throw new IllegalStateException("Injected start failure");
                    }
                }

                @Override
                public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
                    calls.add("scenes:" + name);
                }

                @Override
                public void onPonderReloadEnd(boolean successful) {
                    calls.add("end:" + name + ":" + successful);
                }
            });
        }

        PonderIndex.reload();

        assertEquals(Arrays.asList("start:a", "start:b", "end:b:false", "end:a:false"), calls);
        assertEquals(originalGeneration, PonderIndex.getGeneration());
    }

    @Test
    void sharedTextFailureRollsBackPluginStateBeforeClientCleanup() {
        SceneRegistryAccess before = PonderIndex.getSceneAccess();
        int[] state = { 7 };
        PonderIndex.addPlugin(new PonderPlugin() {
            private int saved;

            @Override
            public String getModId() {
                return "rollback_test";
            }

            @Override
            public void onPonderReloadStart() {
                saved = state[0];
            }

            @Override
            public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
                state[0] = 42;
            }

            @Override
            public void registerSharedText(SharedTextRegistrationHelper helper) {
                throw new LinkageError("Injected optional API failure");
            }

            @Override
            public void onPonderReloadEnd(boolean successful) {
                if (!successful) {
                    state[0] = saved;
                }
            }
        });
        PonderBackportMod.proxy = new CommonProxy() {
            @Override
            public void onPonderReload() {
                assertEquals(7, state[0]);
                assertSame(before, PonderIndex.getSceneAccess());
            }
        };

        PonderIndex.reload();

        assertEquals(7, state[0]);
        assertEquals(originalGeneration, PonderIndex.getGeneration());
    }

    @Test
    void pluginsAddedDuringRegistrationWaitUntilTheNextReload() {
        PonderBackportMod.proxy = null;
        List<String> calls = new ArrayList<String>();
        PonderPlugin late = new PonderPlugin() {
            @Override
            public String getModId() {
                return "late";
            }

            @Override
            public void onPonderReloadStart() {
                calls.add("start");
            }

            @Override
            public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
                calls.add("scenes");
            }

            @Override
            public void registerSharedText(SharedTextRegistrationHelper helper) {
                calls.add("text");
            }

            @Override
            public void onPonderReloadEnd(boolean successful) {
                calls.add("end:" + successful);
            }
        };
        PonderIndex.addPlugin(new PonderPlugin() {
            private boolean added;

            @Override
            public String getModId() {
                return "early";
            }

            @Override
            public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
                if (!added) {
                    added = true;
                    PonderIndex.addPlugin(late);
                }
            }
        });

        PonderIndex.reload();
        assertEquals(0, calls.size());
        PonderIndex.reload();
        assertEquals(Arrays.asList("start", "scenes", "text", "end:true"), calls);
    }

    @Test
    void failingEndHookDoesNotSkipOtherPluginCleanup() {
        PonderBackportMod.proxy = null;
        List<String> calls = new ArrayList<String>();
        for (String name : Arrays.asList("a", "b")) {
            PonderIndex.addPlugin(new PonderPlugin() {
                @Override
                public String getModId() {
                    return name;
                }

                @Override
                public void onPonderReloadEnd(boolean successful) {
                    calls.add(name + ":" + successful);
                    if ("b".equals(name)) {
                        throw new IllegalStateException("Injected cleanup failure");
                    }
                }
            });
        }

        PonderIndex.reload();
        assertEquals(Arrays.asList("b:true", "a:true"), calls);
        assertEquals(originalGeneration + 1, PonderIndex.getGeneration());
    }

    @Test
    void reloadRemainsNonRecursiveDuringEndHooksAndProxyCleanup() {
        List<String> calls = new ArrayList<String>();
        PonderIndex.addPlugin(new PonderPlugin() {
            @Override
            public String getModId() {
                return "recursive_test";
            }

            @Override
            public void onPonderReloadEnd(boolean successful) {
                assertThrows(IllegalStateException.class, PonderIndex::reload);
                calls.add("plugin");
            }
        });
        PonderBackportMod.proxy = new CommonProxy() {
            @Override
            public void onPonderReload() {
                assertThrows(IllegalStateException.class, PonderIndex::reload);
                calls.add("proxy");
            }
        };

        PonderIndex.reload();
        PonderIndex.reload();

        assertEquals(Arrays.asList("plugin", "proxy", "plugin", "proxy"), calls);
        assertEquals(originalGeneration + 2, PonderIndex.getGeneration());
    }

    @Test
    void assertionErrorInEndHookDoesNotSkipRollbackOrProxyCleanup() throws Exception {
        SceneRegistryAccess before = PonderIndex.getSceneAccess();
        List<String> calls = new ArrayList<String>();
        int[] state = { 7 };
        boolean[] fail = { true };
        AssertionError cleanupFailure = new AssertionError("Injected end assertion");
        for (String name : Arrays.asList("a", "b")) {
            PonderIndex.addPlugin(new PonderPlugin() {
                private int saved;

                @Override
                public String getModId() {
                    return name;
                }

                @Override
                public void onPonderReloadStart() {
                    saved = state[0];
                }

                @Override
                public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
                    state[0] = 42;
                }

                @Override
                public void registerSharedText(SharedTextRegistrationHelper helper) {
                    if (fail[0] && "b".equals(name)) {
                        throw new IllegalStateException("Injected registration failure");
                    }
                }

                @Override
                public void onPonderReloadEnd(boolean successful) {
                    calls.add(name + ":" + successful);
                    if (fail[0] && "b".equals(name)) {
                        throw cleanupFailure;
                    }
                    if (!successful) {
                        state[0] = saved;
                    }
                }
            });
        }
        PonderBackportMod.proxy = new CommonProxy() {
            @Override
            public void onPonderReload() {
                calls.add("proxy");
            }
        };

        assertSame(cleanupFailure, assertThrows(AssertionError.class, PonderIndex::reload));

        assertEquals(Arrays.asList("b:false", "a:false", "proxy"), calls);
        assertEquals(7, state[0]);
        assertSame(before, PonderIndex.getSceneAccess());
        assertEquals(originalGeneration, PonderIndex.getGeneration());
        assertNull(((ThreadLocal<?>) field("BUILDING").get(null)).get());
        fail[0] = false;
        calls.clear();
        PonderIndex.reload();
        assertEquals(Arrays.asList("b:true", "a:true", "proxy"), calls);
        assertEquals(originalGeneration + 1, PonderIndex.getGeneration());
    }

    @Test
    void seriousRegistrationFailureRetainsAllCleanupFailures() {
        SceneRegistryAccess before = PonderIndex.getSceneAccess();
        List<String> calls = new ArrayList<String>();
        boolean[] fail = { true };
        AssertionError registrationFailure = new AssertionError("Injected registration assertion");
        ThreadDeath endFailure = new ThreadDeath();
        LinkageError otherEndFailure = new LinkageError("Injected cleanup linkage failure");
        VirtualMachineError proxyFailure = new VirtualMachineError("Injected proxy VM failure") {
        };
        for (String name : Arrays.asList("a", "b")) {
            PonderIndex.addPlugin(new PonderPlugin() {
                @Override
                public String getModId() {
                    return name;
                }

                @Override
                public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
                    if (fail[0] && "a".equals(name)) {
                        throw registrationFailure;
                    }
                }

                @Override
                public void onPonderReloadEnd(boolean successful) {
                    calls.add(name + ":" + successful);
                    if (fail[0]) {
                        if ("b".equals(name)) {
                            throw endFailure;
                        }
                        throw otherEndFailure;
                    }
                }
            });
        }
        PonderBackportMod.proxy = new CommonProxy() {
            @Override
            public void onPonderReload() {
                calls.add("proxy");
                if (fail[0]) {
                    throw proxyFailure;
                }
            }
        };

        assertSame(registrationFailure, assertThrows(AssertionError.class, PonderIndex::reload));

        assertEquals(Arrays.asList("b:false", "a:false", "proxy"), calls);
        assertEquals(Arrays.asList(endFailure, otherEndFailure, proxyFailure),
            Arrays.asList(registrationFailure.getSuppressed()));
        assertSame(before, PonderIndex.getSceneAccess());
        assertEquals(originalGeneration, PonderIndex.getGeneration());
        fail[0] = false;
        calls.clear();
        PonderIndex.reload();
        assertEquals(Arrays.asList("b:true", "a:true", "proxy"), calls);
        assertEquals(originalGeneration + 1, PonderIndex.getGeneration());
    }

    @Test
    void virtualMachineErrorInEndHookStillAttemptsEveryCleanupAfterPublication() {
        List<String> calls = new ArrayList<String>();
        boolean[] fail = { true };
        VirtualMachineError endFailure = new VirtualMachineError("Injected end VM failure") {
        };
        ThreadDeath proxyFailure = new ThreadDeath();
        for (String name : Arrays.asList("a", "b")) {
            PonderIndex.addPlugin(new PonderPlugin() {
                @Override
                public String getModId() {
                    return name;
                }

                @Override
                public void onPonderReloadEnd(boolean successful) {
                    calls.add(name + ":" + successful);
                    if (fail[0] && "b".equals(name)) {
                        throw endFailure;
                    }
                }
            });
        }
        PonderBackportMod.proxy = new CommonProxy() {
            @Override
            public void onPonderReload() {
                calls.add("proxy");
                if (fail[0]) {
                    throw proxyFailure;
                }
            }
        };

        assertSame(endFailure, assertThrows(VirtualMachineError.class, PonderIndex::reload));

        assertEquals(Arrays.asList("b:true", "a:true", "proxy"), calls);
        assertEquals(Arrays.asList(proxyFailure), Arrays.asList(endFailure.getSuppressed()));
        assertEquals(originalGeneration + 1, PonderIndex.getGeneration());
        fail[0] = false;
        calls.clear();
        PonderIndex.reload();
        assertEquals(Arrays.asList("b:true", "a:true", "proxy"), calls);
        assertEquals(originalGeneration + 2, PonderIndex.getGeneration());
    }

    @Test
    void repeatedRegistrationErrorDoesNotSelfSuppressOrSkipCleanup() {
        List<String> calls = new ArrayList<String>();
        boolean[] fail = { true };
        AssertionError failure = new AssertionError("Same failure in registration and cleanup");
        for (String name : Arrays.asList("a", "b")) {
            PonderIndex.addPlugin(new PonderPlugin() {
                @Override
                public String getModId() {
                    return name;
                }

                @Override
                public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
                    if (fail[0]) {
                        throw failure;
                    }
                }

                @Override
                public void onPonderReloadEnd(boolean successful) {
                    calls.add(name + ":" + successful);
                    if (fail[0] && "b".equals(name)) {
                        throw failure;
                    }
                }
            });
        }
        PonderBackportMod.proxy = new CommonProxy() {
            @Override
            public void onPonderReload() {
                calls.add("proxy");
            }
        };

        assertSame(failure, assertThrows(AssertionError.class, PonderIndex::reload));

        assertEquals(Arrays.asList("b:false", "a:false", "proxy"), calls);
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(originalGeneration, PonderIndex.getGeneration());
        fail[0] = false;
        PonderIndex.reload();
        assertEquals(originalGeneration + 1, PonderIndex.getGeneration());
    }

    @Test
    void proxyFailuresRemainVisibleAndDoNotLeaveTheReloadGuardSet() {
        List<String> calls = new ArrayList<String>();
        Throwable[] pending = { null };
        PonderIndex.addPlugin(new PonderPlugin() {
            @Override
            public String getModId() {
                return "proxy_failure_test";
            }

            @Override
            public void onPonderReloadEnd(boolean successful) {
                calls.add("plugin:" + successful);
            }
        });
        PonderBackportMod.proxy = new CommonProxy() {
            @Override
            public void onPonderReload() {
                calls.add("proxy");
                if (pending[0] instanceof Error) {
                    throw (Error) pending[0];
                }
                if (pending[0] != null) {
                    throw (RuntimeException) pending[0];
                }
            }
        };
        List<Throwable> failures = Arrays.asList(new IllegalStateException("Injected proxy failure"),
            new LinkageError("Injected proxy linkage failure"), new ThreadDeath());
        for (Throwable failure : failures) {
            pending[0] = failure;
            assertSame(failure, assertThrows(failure.getClass(), PonderIndex::reload));
        }

        pending[0] = null;
        PonderIndex.reload();

        assertEquals(Arrays.asList("plugin:true", "proxy", "plugin:true", "proxy",
            "plugin:true", "proxy", "plugin:true", "proxy"), calls);
        assertEquals(originalGeneration + failures.size() + 1, PonderIndex.getGeneration());
    }

    @SuppressWarnings("unchecked")
    private static List<PonderPlugin> plugins() throws Exception {
        return (List<PonderPlugin>) field("PLUGINS").get(null);
    }

    private static Field field(String name) throws Exception {
        Field field = PonderIndex.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
