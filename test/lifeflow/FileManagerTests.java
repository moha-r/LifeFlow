package lifeflow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.EmergencyRequest;
import lifeflow.model.RegularRequest;
import lifeflow.model.RequestStatus;
import lifeflow.model.UnitStatus;
import lifeflow.persistence.FileManager;

final class FileManagerTests {
    private FileManagerTests() {
    }

    static void run() throws IOException {
        treatsMissingFilesAsEmptyLists();
        roundTripsAllDataAndRequestSubclasses();
        reportsMalformedHeadersAtLineOne();
        reportsMalformedLinesWithTheLineNumber();
    }

    private static void treatsMissingFilesAsEmptyLists() throws IOException {
        FileManager manager = new FileManager(Files.createTempDirectory("lifeflow-empty-"));

        assert manager.loadDonors().isEmpty();
        assert manager.loadUnits().isEmpty();
        assert manager.loadRequests().isEmpty();
    }

    private static void roundTripsAllDataAndRequestSubclasses() throws IOException {
        Path directory = Files.createTempDirectory("lifeflow-roundtrip-");
        FileManager manager = new FileManager(directory);

        ArrayList<Donor> donors = new ArrayList<>();
        donors.add(new Donor("D1", "Aisha Noor", 25, 55.5, BloodType.A_POS,
                LocalDate.of(2026, 8, 3)));
        ArrayList<BloodUnit> units = new ArrayList<>();
        units.add(new BloodUnit("U1", "D1", BloodType.A_POS,
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 9, 3),
                UnitStatus.AVAILABLE));
        ArrayList<BloodRequest> requests = new ArrayList<>();
        requests.add(new RegularRequest("R1", "Clinic A", BloodType.A_POS,
                1, LocalDate.of(2026, 8, 3), RequestStatus.PENDING));
        requests.add(new EmergencyRequest("R2", "Hospital B", BloodType.O_NEG,
                2, LocalDate.of(2026, 8, 3), RequestStatus.FULFILLED));

        manager.saveDonors(donors);
        manager.saveUnits(units);
        manager.saveRequests(requests);

        ArrayList<Donor> loadedDonors = manager.loadDonors();
        ArrayList<BloodUnit> loadedUnits = manager.loadUnits();
        ArrayList<BloodRequest> loadedRequests = manager.loadRequests();
        assert loadedDonors.size() == 1;
        assert loadedDonors.get(0).getName().equals("Aisha Noor");
        assert loadedDonors.get(0).getLastDonationDate().equals(LocalDate.of(2026, 8, 3));
        assert loadedUnits.size() == 1;
        assert loadedUnits.get(0).getStatus() == UnitStatus.AVAILABLE;
        assert loadedRequests.get(0) instanceof RegularRequest;
        assert loadedRequests.get(1) instanceof EmergencyRequest;
        assert loadedRequests.get(1).getStatus() == RequestStatus.FULFILLED;
    }

    private static void reportsMalformedLinesWithTheLineNumber() throws IOException {
        Path directory = Files.createTempDirectory("lifeflow-bad-line-");
        Files.writeString(directory.resolve("donors.txt"),
                "id|name|age|weightKg|bloodType|lastDonationDate\n"
                        + "D1|Missing fields\n");
        FileManager manager = new FileManager(directory);

        boolean failed = false;
        try {
            manager.loadDonors();
        } catch (IOException exception) {
            failed = exception.getMessage().contains("donors.txt")
                    && exception.getMessage().contains("line 2");
        }

        assert failed : "Malformed data must report the file and line number";
    }

    private static void reportsMalformedHeadersAtLineOne() throws IOException {
        Path directory = Files.createTempDirectory("lifeflow-bad-header-");
        Files.writeString(directory.resolve("donors.txt"),
                "wrong|header\nD1|Aisha|25|55.0|A_POS|\n");
        FileManager manager = new FileManager(directory);

        boolean failed = false;
        try {
            manager.loadDonors();
        } catch (IOException exception) {
            failed = exception.getMessage().contains("donors.txt")
                    && exception.getMessage().contains("line 1");
        }

        assert failed : "Malformed headers must be rejected at line 1";
    }
}
