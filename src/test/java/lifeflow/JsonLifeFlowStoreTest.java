package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.FulfilmentRecord;
import lifeflow.model.LifeFlowState;
import lifeflow.persistence.JsonLifeFlowStore;
import lifeflow.persistence.StoragePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JsonLifeFlowStoreTest {
    @TempDir
    Path directory;

    @Test
    void startsEmptyAndRoundTripsOneReadableSnapshot() throws Exception {
        LifeFlowState state = stateWithDonor(1, "D1");

        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            assertTrue(store.load().getDonors().isEmpty());
            assertTrue(Files.exists(directory.resolve("lifeflow.json")));
            assertTrue(store.getStorageInfo().backupCurrent());
            store.save(state);
            assertTrue(store.getStorageInfo().backupCurrent());
        }

        String json = Files.readString(directory.resolve("lifeflow.json"));
        assertTrue(json.contains("\"formatVersion\" : 1"));
        assertTrue(json.contains("\"donors\""));
        try (JsonLifeFlowStore reopened = new JsonLifeFlowStore(directory)) {
            assertEquals(1, reopened.load().getRevision());
            assertEquals("D1", reopened.load().getDonors().get(0).getId());
        }
    }

    @Test
    void rejectsTamperedPrimaryAndRestoresVerifiedBackup() throws Exception {
        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            store.save(stateWithDonor(1, "D1"));
            store.save(stateWithDonor(2, "D2"));
        }
        Path main = directory.resolve("lifeflow.json");
        Files.writeString(main, Files.readString(main).replace("D2", "CHANGED"));

        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            assertThrows(IOException.class, store::load);
            assertTrue(store.getStorageInfo().recoveryRequired());
            LifeFlowState restored = store.restoreLatestBackup();
            assertEquals("D2", restored.getDonors().get(0).getId());
            assertFalse(store.getStorageInfo().recoveryRequired());
        }

        assertTrue(Files.list(directory.resolve("recovery")).findAny().isPresent());
    }

    @Test
    void fallsBackToPreviousBackupAndMakesItCurrent() throws Exception {
        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            store.load();
            store.save(stateWithDonor(1, "D1"));
            store.save(stateWithDonor(2, "D2"));
        }
        Path main = directory.resolve("lifeflow.json");
        Path backup = directory.resolve("backups/lifeflow-backup.json");
        Files.writeString(main, Files.readString(main).replace("D2", "BROKEN"));
        Files.writeString(backup, Files.readString(backup).replace("D2", "BROKEN"));

        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            assertThrows(IOException.class, store::load);
            LifeFlowState restored = store.restoreLatestBackup();
            assertEquals("D1", restored.getDonors().get(0).getId());
            assertTrue(store.getStorageInfo().backupCurrent());
            assertEquals(Files.readString(main), Files.readString(backup));
        }
    }

    @Test
    void detectsAValidButStaleBackupAfterRestart() throws Exception {
        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            store.load();
            store.save(stateWithDonor(1, "D1"));
        }
        Path backup = directory.resolve("backups/lifeflow-backup.json");
        Path previous = directory.resolve("backups/lifeflow-backup.previous.json");
        Files.copy(previous, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            assertEquals("D1", store.load().getDonors().get(0).getId());
            assertFalse(store.getStorageInfo().backupCurrent());
        }
    }

    @Test
    void refusesRecoveryWhenThePrimaryFileIsHealthy() throws Exception {
        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            store.load();
            assertThrows(IOException.class, store::restoreLatestBackup);
            assertFalse(store.getStorageInfo().recoveryRequired());
        }
    }

    @Test
    void missingPrimaryWithCurrentBackupRequiresExplicitRecovery() throws Exception {
        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            store.load();
            store.save(stateWithDonor(1, "D1"));
        }
        Files.delete(directory.resolve("lifeflow.json"));

        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            assertThrows(IOException.class, store::load);
            assertTrue(store.getStorageInfo().recoveryRequired());
            assertEquals("D1", store.restoreLatestBackup().getDonors().get(0).getId());
        }
    }

    @Test
    void missingPrimaryCanRecoverThePreviousBackup() throws Exception {
        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            store.load();
            store.save(stateWithDonor(1, "D1"));
            store.save(stateWithDonor(2, "D2"));
        }
        Files.delete(directory.resolve("lifeflow.json"));
        Files.delete(directory.resolve("backups/lifeflow-backup.json"));

        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            assertThrows(IOException.class, store::load);
            assertEquals("D1", store.restoreLatestBackup().getDonors().get(0).getId());
        }
    }

    @Test
    void laterSaveHealsACorruptCurrentBackup() throws Exception {
        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            store.load();
            store.save(stateWithDonor(1, "D1"));
        }
        Path backup = directory.resolve("backups/lifeflow-backup.json");
        Files.writeString(backup, Files.readString(backup).replace("D1", "BROKEN"));

        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            assertEquals("D1", store.load().getDonors().get(0).getId());
            assertFalse(store.getStorageInfo().backupCurrent());
            store.save(stateWithDonor(2, "D2"));
            assertTrue(store.getStorageInfo().backupCurrent());
        }
        assertTrue(Files.readString(backup).contains("D2"));
    }

    @Test
    void checksumDetectsRevisionTampering() throws Exception {
        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            store.load();
            store.save(stateWithDonor(1, "D1"));
        }
        Path main = directory.resolve("lifeflow.json");
        Files.writeString(main, Files.readString(main)
                .replace("\"revision\" : 1", "\"revision\" : 99"));

        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            assertThrows(IOException.class, store::load);
        }
    }

    @Test
    void invalidCandidateNeverReplacesTheLastGoodFile() throws Exception {
        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            store.save(stateWithDonor(1, "D1"));
            String before = Files.readString(directory.resolve("lifeflow.json"));
            Donor duplicate = new Donor("d1", "Duplicate", 30, 60.0,
                    BloodType.A_POS, null);
            LifeFlowState invalid = new LifeFlowState(2,
                    new ArrayList<>(List.of(
                            new Donor("D1", "First", 30, 60.0,
                                    BloodType.A_POS, null), duplicate)),
                    new ArrayList<BloodUnit>(), new ArrayList<BloodRequest>(),
                    new ArrayList<FulfilmentRecord>());

            assertThrows(IOException.class, () -> store.save(invalid));
            assertEquals(before, Files.readString(directory.resolve("lifeflow.json")));
        }
    }

    @Test
    void refusesASecondProcessLockForTheSameDirectory() throws Exception {
        try (JsonLifeFlowStore first = new JsonLifeFlowStore(directory)) {
            assertEquals(directory.resolve("lifeflow.json").toAbsolutePath(),
                    first.getStorageInfo().dataFile());
            assertThrows(IOException.class, () -> new JsonLifeFlowStore(directory));
        }
    }

    @Test
    void reportsBackupFailureWithoutLosingThePrimarySave() throws Exception {
        Files.writeString(directory.resolve("backups"), "blocks directory creation");

        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            store.save(stateWithDonor(1, "D1"));
            assertFalse(store.getStorageInfo().backupCurrent());
            assertEquals("D1", store.load().getDonors().get(0).getId());
        }
    }

    @Test
    void resolvesOneStableDirectoryWithoutUsingTheWorkingDirectory() {
        Path fromEnvironment = StoragePaths.resolve(
                Map.of("LIFEFLOW_DATA_DIR", "/tmp/lifeflow-custom"), "/users/test");
        Path fromHome = StoragePaths.resolve(Map.of(), "/users/test");

        assertEquals(Path.of("/tmp/lifeflow-custom").toAbsolutePath(), fromEnvironment);
        assertEquals(Path.of("/users/test/.lifeflow").toAbsolutePath(), fromHome);
    }

    private static LifeFlowState stateWithDonor(long revision, String id) {
        Donor donor = new Donor(id, "Aisha", 25, 55.0, BloodType.A_POS, null);
        return new LifeFlowState(revision, new ArrayList<>(List.of(donor)),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
}
