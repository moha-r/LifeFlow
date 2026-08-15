package lifeflow.persistence;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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

/** Reads and writes the three pipe-delimited application data files. */
public class FileManager {
    private static final String DONOR_HEADER =
            "id|name|age|weightKg|bloodType|lastDonationDate";
    private static final String UNIT_HEADER =
            "id|donorId|bloodType|donationDate|expiryDate|status";
    private static final String REQUEST_HEADER =
            "id|kind|requesterName|bloodType|quantity|requestDate|status";

    private final Path dataDirectory;

    public FileManager(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    public ArrayList<Donor> loadDonors() throws IOException {
        Path file = dataDirectory.resolve("donors.txt");
        ArrayList<Donor> donors = new ArrayList<>();
        if (Files.notExists(file)) {
            return donors;
        }

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            requireHeader(reader, file, DONOR_HEADER);
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    String[] values = fields(line, 6);
                    LocalDate lastDonation = values[5].isBlank()
                            ? null : LocalDate.parse(values[5]);
                    donors.add(new Donor(values[0], values[1],
                            Integer.parseInt(values[2]),
                            Double.parseDouble(values[3]),
                            BloodType.valueOf(values[4]), lastDonation));
                } catch (RuntimeException exception) {
                    throw malformed(file, lineNumber, exception);
                }
            }
        }
        return donors;
    }

    public ArrayList<BloodUnit> loadUnits() throws IOException {
        Path file = dataDirectory.resolve("blood_units.txt");
        ArrayList<BloodUnit> units = new ArrayList<>();
        if (Files.notExists(file)) {
            return units;
        }

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            requireHeader(reader, file, UNIT_HEADER);
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    String[] values = fields(line, 6);
                    units.add(new BloodUnit(values[0], values[1],
                            BloodType.valueOf(values[2]),
                            LocalDate.parse(values[3]),
                            LocalDate.parse(values[4]),
                            UnitStatus.valueOf(values[5])));
                } catch (RuntimeException exception) {
                    throw malformed(file, lineNumber, exception);
                }
            }
        }
        return units;
    }

    public ArrayList<BloodRequest> loadRequests() throws IOException {
        Path file = dataDirectory.resolve("requests.txt");
        ArrayList<BloodRequest> requests = new ArrayList<>();
        if (Files.notExists(file)) {
            return requests;
        }

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            requireHeader(reader, file, REQUEST_HEADER);
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    String[] values = fields(line, 7);
                    BloodRequest request;
                    if (values[1].equals("REGULAR")) {
                        request = new RegularRequest(values[0], values[2],
                                BloodType.valueOf(values[3]),
                                Integer.parseInt(values[4]),
                                LocalDate.parse(values[5]),
                                RequestStatus.valueOf(values[6]));
                    } else if (values[1].equals("EMERGENCY")) {
                        request = new EmergencyRequest(values[0], values[2],
                                BloodType.valueOf(values[3]),
                                Integer.parseInt(values[4]),
                                LocalDate.parse(values[5]),
                                RequestStatus.valueOf(values[6]));
                    } else {
                        throw new IllegalArgumentException("Unknown request kind");
                    }
                    requests.add(request);
                } catch (RuntimeException exception) {
                    throw malformed(file, lineNumber, exception);
                }
            }
        }
        return requests;
    }

    public void saveDonors(ArrayList<Donor> donors) throws IOException {
        Files.createDirectories(dataDirectory);
        try (BufferedWriter writer = Files.newBufferedWriter(
                dataDirectory.resolve("donors.txt"))) {
            writer.write(DONOR_HEADER);
            writer.newLine();
            for (Donor donor : donors) {
                String lastDonation = donor.getLastDonationDate() == null
                        ? "" : donor.getLastDonationDate().toString();
                writer.write(String.join("|", donor.getId(), donor.getName(),
                        Integer.toString(donor.getAge()),
                        Double.toString(donor.getWeightKg()),
                        donor.getBloodType().name(), lastDonation));
                writer.newLine();
            }
        }
    }

    public void saveUnits(ArrayList<BloodUnit> units) throws IOException {
        Files.createDirectories(dataDirectory);
        try (BufferedWriter writer = Files.newBufferedWriter(
                dataDirectory.resolve("blood_units.txt"))) {
            writer.write(UNIT_HEADER);
            writer.newLine();
            for (BloodUnit unit : units) {
                writer.write(String.join("|", unit.getId(), unit.getDonorId(),
                        unit.getBloodType().name(), unit.getDonationDate().toString(),
                        unit.getExpiryDate().toString(), unit.getStatus().name()));
                writer.newLine();
            }
        }
    }

    public void saveRequests(ArrayList<BloodRequest> requests) throws IOException {
        Files.createDirectories(dataDirectory);
        try (BufferedWriter writer = Files.newBufferedWriter(
                dataDirectory.resolve("requests.txt"))) {
            writer.write(REQUEST_HEADER);
            writer.newLine();
            for (BloodRequest request : requests) {
                writer.write(String.join("|", request.getId(), request.getKind(),
                        request.getRequesterName(), request.getBloodType().name(),
                        Integer.toString(request.getQuantity()),
                        request.getRequestDate().toString(),
                        request.getStatus().name()));
                writer.newLine();
            }
        }
    }

    private String[] fields(String line, int expected) {
        String[] values = line.split("\\|", -1);
        if (values.length != expected) {
            throw new IllegalArgumentException("Wrong field count");
        }
        return values;
    }

    private void requireHeader(BufferedReader reader, Path file,
                               String expectedHeader) throws IOException {
        String header = reader.readLine();
        if (!expectedHeader.equals(header)) {
            throw malformed(file, 1,
                    new IllegalArgumentException("Wrong or missing header"));
        }
    }

    private IOException malformed(Path file, int lineNumber, Exception cause) {
        return new IOException("Invalid data in " + file.getFileName()
                + " at line " + lineNumber, cause);
    }
}
