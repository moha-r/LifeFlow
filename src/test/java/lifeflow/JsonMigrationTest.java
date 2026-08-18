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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import lifeflow.persistence.JsonLifeFlowStore;

final class JsonMigrationTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    @TempDir
    Path directory;

    @Test
    void migratesVersionOneAndRemovesDuplicatedDonorDonationDate() throws Exception {
        Map<String, Object> donor = donor("D001", TODAY.minusMonths(4).toString());
        Map<String, Object> unit = unit("U001", "D001", TODAY.minusMonths(4),
                TODAY.minusMonths(4).plusDays(90), "AVAILABLE");
        writeVersionOne(List.of(donor), List.of(unit), List.of(), List.of());

        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            var state = store.load();

            assertEquals(1, state.getDonors().size());
            assertEquals(null,
                    state.getDonors().get(0).getExternalLastDonationDate());
            assertEquals(state.getUnits().get(0).getDonationDate().plusDays(35),
                    state.getUnits().get(0).getExpiryDate());
            assertTrue(store.getStorageInfo().detail().contains("Migrated"));
        }

        String json = Files.readString(directory.resolve("lifeflow.json"));
        assertTrue(json.contains("\"formatVersion\" : 2"));
        assertTrue(json.contains("\"externalLastDonationDate\""));
        assertFalse(json.contains("\"lastDonationDate\""));
        assertTrue(Files.list(directory.resolve("recovery"))
                .anyMatch(path -> path.getFileName().toString()
                        .startsWith("lifeflow-v1-before-migration-")));
    }

    @Test
    void preservesLegacyDateAsExternalWhenNoUnitRepresentsIt() throws Exception {
        LocalDate external = TODAY.minusMonths(5);
        writeVersionOne(List.of(donor("D001", external.toString())),
                List.of(), List.of(), List.of());

        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            assertEquals(external,
                    store.load().getDonors().get(0).getExternalLastDonationDate());
        }
    }

    @Test
    void invalidLegacyHistoryLeavesOriginalFileUntouched() throws Exception {
        Map<String, Object> donor = donor("D001", null);
        Map<String, Object> first = unit("U001", "D001", TODAY.minusMonths(2),
                TODAY.minusMonths(2).plusDays(35), "AVAILABLE");
        Map<String, Object> second = unit("U002", "D001", TODAY.minusMonths(1),
                TODAY.minusMonths(1).plusDays(35), "AVAILABLE");
        writeVersionOne(List.of(donor), List.of(first, second), List.of(), List.of());
        Path dataFile = directory.resolve("lifeflow.json");
        byte[] original = Files.readAllBytes(dataFile);

        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            assertThrows(IOException.class, store::load);
        }

        assertTrue(MessageDigest.isEqual(original, Files.readAllBytes(dataFile)));
    }

    @Test
    void migrationFailureIsReportedWithoutCallingHealthyDataCorrupt() throws Exception {
        Map<String, Object> donor = donor("D001", null);
        Map<String, Object> first = unit("U001", "D001", TODAY.minusMonths(2),
                TODAY.minusMonths(2).plusDays(35), "AVAILABLE");
        Map<String, Object> second = unit("U002", "D001", TODAY.minusMonths(1),
                TODAY.minusMonths(1).plusDays(35), "AVAILABLE");
        writeVersionOne(List.of(donor), List.of(first, second), List.of(), List.of());

        try (JsonLifeFlowStore store = new JsonLifeFlowStore(directory)) {
            assertThrows(IOException.class, store::load);
            assertTrue(store.getStorageInfo().detail().startsWith("Migration failed"));
        }
    }

    private void writeVersionOne(List<Map<String, Object>> donors,
                                 List<Map<String, Object>> units,
                                 List<Map<String, Object>> requests,
                                 List<Map<String, Object>> fulfilments)
            throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .build();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bloodUnits", units);
        data.put("donors", donors);
        data.put("fulfilments", fulfilments);
        data.put("requests", requests);
        Map<String, Object> checksumContent = new LinkedHashMap<>();
        checksumContent.put("data", data);
        checksumContent.put("formatVersion", 1);
        checksumContent.put("revision", 7);
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(checksumContent)));
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("checksum", checksum);
        envelope.put("data", data);
        envelope.put("formatVersion", 1);
        envelope.put("revision", 7);
        envelope.put("savedAt", "2026-08-18T14:30:00");
        Files.write(directory.resolve("lifeflow.json"),
                mapper.writeValueAsBytes(envelope));
    }

    private static Map<String, Object> donor(String id, String lastDonationDate) {
        Map<String, Object> donor = new LinkedHashMap<>();
        donor.put("age", 25);
        donor.put("bloodType", "A_POS");
        donor.put("id", id);
        donor.put("lastDonationDate", lastDonationDate);
        donor.put("name", "Aisha");
        donor.put("weightKg", 55.0);
        return donor;
    }

    private static Map<String, Object> unit(String id, String donorId,
                                            LocalDate donation,
                                            LocalDate expiry, String status) {
        Map<String, Object> unit = new LinkedHashMap<>();
        unit.put("bloodType", "A_POS");
        unit.put("donationDate", donation.toString());
        unit.put("donorId", donorId);
        unit.put("expiryDate", expiry.toString());
        unit.put("id", id);
        unit.put("status", status);
        return unit;
    }
}
