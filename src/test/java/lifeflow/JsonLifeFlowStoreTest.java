package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
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
        assertTrue(json.contains("\"formatVersion\" : 2"));
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
    void corruptDonorBloodTypeLoadsAsIOExceptionInsteadOfCrashing() throws Exception {
        JsonLifeFlowStore.Payload payload = new JsonLifeFlowStore.Payload();
        JsonLifeFlowStore.DonorData donor = new JsonLifeFlowStore.DonorData();
        donor.id = "D1";
        donor.name = "Aisha";
        donor.age = 25;
        donor.weightKg = 55.0;
        donor.bloodType = null;
        payload.donors.add(donor);
        writeTamperedEnvelope(payload, false);

        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            IOException failure = assertThrows(IOException.class, store::load);
            assertTrue(failure.getMessage().contains("invalid LifeFlow data"));
        }
    }

    @Test
    void invalidDonorDateLoadsAsIOExceptionInsteadOfCrashing() throws Exception {
        JsonLifeFlowStore.Payload payload = new JsonLifeFlowStore.Payload();
        JsonLifeFlowStore.DonorData donor = new JsonLifeFlowStore.DonorData();
        donor.id = "D1";
        donor.name = "Aisha";
        donor.age = 25;
        donor.weightKg = 55.0;
        donor.bloodType = "A_POS";
        donor.externalLastDonationDate = "2026-13-99";
        payload.donors.add(donor);
        writeTamperedEnvelope(payload, false);

        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            IOException failure = assertThrows(IOException.class, store::load);
            assertTrue(failure.getMessage().contains("invalid LifeFlow data"));
        }
    }

    @Test
    void unknownUnitEnumLoadsAsIOExceptionInsteadOfCrashing() throws Exception {
        JsonLifeFlowStore.Payload payload = new JsonLifeFlowStore.Payload();
        JsonLifeFlowStore.UnitData unit = new JsonLifeFlowStore.UnitData();
        unit.id = "U1";
        unit.donorId = "D1";
        unit.bloodType = "O_POS";
        unit.donationDate = "2026-08-01";
        unit.expiryDate = "2026-09-05";
        unit.status = "MYSTERY";
        payload.bloodUnits.add(unit);
        writeTamperedEnvelope(payload, false);

        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            IOException failure = assertThrows(IOException.class, store::load);
            assertTrue(failure.getMessage().contains("invalid LifeFlow data"));
        }
    }

    @Test
    void corruptLegacyBackupLoadsAsIOExceptionInsteadOfCrashing() throws Exception {
        JsonLifeFlowStore.LegacyPayload payload = new JsonLifeFlowStore.LegacyPayload();
        JsonLifeFlowStore.LegacyDonorData donor = new JsonLifeFlowStore.LegacyDonorData();
        donor.id = "D1";
        donor.name = "Aisha";
        donor.age = 25;
        donor.weightKg = 55.0;
        donor.bloodType = "A_POS";
        donor.lastDonationDate = "2026-13-99";
        payload.donors.add(donor);
        writeTamperedEnvelope(payload, true);

        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            IOException failure = assertThrows(IOException.class, store::load);
            assertTrue(failure.getMessage().contains("could not be migrated"));
        }
    }

    private void writeTamperedEnvelope(Object payload, boolean legacy) throws Exception {
        ObjectMapper canonical = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .build();
        String checksum = tamperedChecksum(canonical, payload, legacy);
        Object envelope;
        if (legacy) {
            JsonLifeFlowStore.LegacyEnvelope legacyEnvelope =
                    new JsonLifeFlowStore.LegacyEnvelope();
            legacyEnvelope.formatVersion = 1;
            legacyEnvelope.revision = 1;
            legacyEnvelope.savedAt = "2026-08-19T00:00:00";
            legacyEnvelope.checksum = checksum;
            legacyEnvelope.data = (JsonLifeFlowStore.LegacyPayload) payload;
            envelope = legacyEnvelope;
        } else {
            JsonLifeFlowStore.Envelope versionTwo = new JsonLifeFlowStore.Envelope();
            versionTwo.formatVersion = 2;
            versionTwo.revision = 1;
            versionTwo.savedAt = "2026-08-19T00:00:00";
            versionTwo.checksum = checksum;
            versionTwo.data = (JsonLifeFlowStore.Payload) payload;
            envelope = versionTwo;
        }
        Files.writeString(directory.resolve("lifeflow.json"),
                canonical.writeValueAsString(envelope));
    }

    private static String tamperedChecksum(ObjectMapper canonical, Object payload,
                                           boolean legacy) throws Exception {
        Object content;
        if (legacy) {
            JsonLifeFlowStore.LegacyChecksumContent legacyContent =
                    new JsonLifeFlowStore.LegacyChecksumContent();
            legacyContent.formatVersion = 1;
            legacyContent.revision = 1;
            legacyContent.data = (JsonLifeFlowStore.LegacyPayload) payload;
            content = legacyContent;
        } else {
            JsonLifeFlowStore.ChecksumContent versionTwo =
                    new JsonLifeFlowStore.ChecksumContent();
            versionTwo.formatVersion = 2;
            versionTwo.revision = 1;
            versionTwo.data = (JsonLifeFlowStore.Payload) payload;
            content = versionTwo;
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(
                digest.digest(canonical.writeValueAsBytes(content)));
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
