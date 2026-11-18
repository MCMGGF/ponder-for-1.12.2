package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthoringSettingsTest {
    @TempDir Path config;

    @AfterEach void resetPreference() throws IOException {
        AuthoringSettings.load(config.resolve("unused"));
    }

    @Test void missingPreferenceDefaultsToDisabledWithoutCreatingFiles() throws Exception {
        AuthoringSettings.load(config);
        assertFalse(AuthoringSettings.isEnabled());
        assertFalse(AuthoringSettings.hasPendingChange());
        assertFalse(Files.exists(config.resolve(AuthoringSettings.FILE_NAME)));
        assertFalse(Files.exists(config.resolve("ponder_legacy").resolve("authoring")));
    }

    @Test void bothCommandsPersistAndOnlyActualChangesReload() throws Exception {
        AuthoringSettings.load(config);
        AtomicLong generation = new AtomicLong();
        assertTrue(AuthoringSettings.change(true, generation::get, () -> {
            assertTrue(AuthoringSettings.isEnabled());
            generation.incrementAndGet();
        }));
        assertTrue(AuthoringSettings.change(true, generation::get, () -> fail("unexpected reload")));
        AuthoringSettings.load(config);
        assertTrue(AuthoringSettings.isEnabled());
        assertTrue(AuthoringSettings.change(false, generation::get, generation::incrementAndGet));
        AuthoringSettings.load(config);
        assertFalse(AuthoringSettings.isEnabled());
        assertEquals(2, generation.get());
    }

    @Test void failedReloadRetainsRuntimeStateAndCanBeRetried() throws Exception {
        AuthoringSettings.load(config);
        AtomicLong generation = new AtomicLong();
        assertFalse(AuthoringSettings.change(true, generation::get, () -> assertTrue(AuthoringSettings.isEnabled())));
        assertFalse(AuthoringSettings.isEnabled());
        assertTrue(AuthoringSettings.hasPendingChange());
        assertTrue(AuthoringSettings.readPreference(config.resolve(AuthoringSettings.FILE_NAME)));
        assertTrue(AuthoringSettings.change(true, generation::get, generation::incrementAndGet));
        assertTrue(AuthoringSettings.isEnabled());
        assertFalse(AuthoringSettings.hasPendingChange());
    }

    @Test void failedWriteLeavesRuntimeUnchangedAndSkipsReload() throws Exception {
        Path unwriteable = config.resolve("file-instead-of-directory");
        Files.createFile(unwriteable);
        assertThrows(IOException.class, () -> AuthoringSettings.load(unwriteable));
        assertThrows(IOException.class, () -> AuthoringSettings.change(true, () -> 0L, () -> fail("unexpected reload")));
        assertFalse(AuthoringSettings.isEnabled());
        assertFalse(AuthoringSettings.hasPendingChange());
    }
}
