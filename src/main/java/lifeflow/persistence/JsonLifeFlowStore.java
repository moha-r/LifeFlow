package lifeflow.persistence;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.EmergencyRequest;
import lifeflow.model.FulfilmentRecord;
import lifeflow.model.LifeFlowState;
import lifeflow.model.RegularRequest;
import lifeflow.model.RequestStatus;
import lifeflow.model.UnitStatus;
import lifeflow.service.DataValidator;

/** Jackson-backed, checksum-verified, atomic local snapshot storage. */
public final class JsonLifeFlowStore implements LifeFlowStore {
    private static final int FORMAT_VERSION = 1;
    private static final DateTimeFormatter RECOVERY_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final Path directory;
    private final Path dataFile;
    private final Path backupFile;
    private final Path previousBackupFile;
    private final ObjectMapper mapper;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private boolean backupCurrent;
    private boolean recoveryRequired;
    private String detail = "Storage ready";

    public JsonLifeFlowStore(Path directory) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        dataFile = this.directory.resolve("lifeflow.json");
        backupFile = this.directory.resolve("backups/lifeflow-backup.json");
        previousBackupFile = this.directory.resolve(
                "backups/lifeflow-backup.previous.json");
        mapper = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .build();

        Files.createDirectories(this.directory);
        lockChannel = FileChannel.open(this.directory.resolve("lifeflow.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try {
            acquired = lockChannel.tryLock();
        } catch (OverlappingFileLockException exception) {
            acquired = null;
        }
        if (acquired == null) {
            lockChannel.close();
            throw new IOException("LifeFlow is already running with this data directory.");
        }
        lock = acquired;
        backupCurrent = Files.exists(backupFile);
    }

    @Override
    public LifeFlowState load() throws IOException {
        if (Files.notExists(dataFile)) {
            if (Files.exists(backupFile) || Files.exists(previousBackupFile)) {
                recoveryRequired = true;
                backupCurrent = false;
                detail = "Recovery required";
                throw new IOException(
                        "The primary JSON file is missing, but a backup is available.");
            }
            LifeFlowState empty = new LifeFlowState();
            save(empty);
            return empty;
        }
        try {
            LifeFlowState state = readVerified(dataFile);
            backupCurrent = backupMatchesPrimary();
            recoveryRequired = false;
            detail = backupCurrent ? "Storage and backup ready"
                    : "Data loaded; backup needs retry";
            return state;
        } catch (IOException exception) {
            recoveryRequired = true;
            detail = "Recovery required";
            throw exception;
        }
    }

    @Override
    public void save(LifeFlowState state) throws IOException {
        try {
            DataValidator.validate(state);
        } catch (IllegalArgumentException exception) {
            throw new IOException("LifeFlow refused to save invalid data: "
                    + exception.getMessage(), exception);
        }

        Envelope envelope = envelope(state);
        byte[] bytes = mapper.writeValueAsBytes(envelope);
        writeVerifiedAtomically(dataFile, bytes);
        recoveryRequired = false;

        try {
            Files.createDirectories(backupFile.getParent());
        } catch (IOException exception) {
            backupCurrent = false;
            detail = "Data saved; backup needs retry";
            return;
        }
        if (Files.exists(backupFile)) {
            try {
                readVerified(backupFile);
                writeVerifiedAtomically(previousBackupFile,
                        Files.readAllBytes(backupFile));
            } catch (IOException ignored) {
                // Keep an existing valid previous backup when current is damaged.
            }
        }
        try {
            writeVerifiedAtomically(backupFile, bytes);
            backupCurrent = true;
            detail = "Storage and backup ready";
        } catch (IOException exception) {
            backupCurrent = false;
            detail = "Data saved; backup needs retry";
        }
    }

    public LifeFlowState restoreLatestBackup() throws IOException {
        if (!recoveryRequired) {
            throw new IOException("Recovery is not required for the current data file.");
        }
        LifeFlowState restored;
        byte[] bytes;
        try {
            restored = readVerified(backupFile);
            bytes = Files.readAllBytes(backupFile);
        } catch (IOException currentFailure) {
            restored = readVerified(previousBackupFile);
            bytes = Files.readAllBytes(previousBackupFile);
        }

        Path temporary = writeVerifiedTemporary(dataFile, bytes);
        try {
            if (Files.exists(dataFile)) {
                Path recoveryDirectory = directory.resolve("recovery");
                Files.createDirectories(recoveryDirectory);
                Path archived = uniqueRecoveryPath(recoveryDirectory);
                Files.copy(dataFile, archived);
            }
            replaceAtomically(temporary, dataFile);
        } finally {
            Files.deleteIfExists(temporary);
        }
        try {
            writeVerifiedAtomically(backupFile, bytes);
            backupCurrent = true;
            detail = "Storage restored from backup";
        } catch (IOException exception) {
            backupCurrent = false;
            detail = "Data restored; backup needs retry";
        }
        recoveryRequired = false;
        return restored;
    }

    private Path uniqueRecoveryPath(Path recoveryDirectory) {
        String base = "lifeflow-corrupt-"
                + LocalDateTime.now().format(RECOVERY_TIME);
        Path candidate = recoveryDirectory.resolve(base + ".json");
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = recoveryDirectory.resolve(base + "-" + suffix + ".json");
            suffix++;
        }
        return candidate;
    }

    private boolean backupMatchesPrimary() {
        if (Files.notExists(backupFile)) {
            return false;
        }
        try {
            readVerified(backupFile);
            return Files.mismatch(dataFile, backupFile) == -1;
        } catch (IOException exception) {
            return false;
        }
    }

    @Override
    public StorageInfo getStorageInfo() {
        return new StorageInfo(dataFile, backupCurrent, recoveryRequired, detail);
    }

    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            lockChannel.close();
        }
    }

    private void writeVerifiedAtomically(Path target, byte[] bytes) throws IOException {
        Path temporary = writeVerifiedTemporary(target, bytes);
        try {
            replaceAtomically(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path writeVerifiedTemporary(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                channel.write(ByteBuffer.wrap(bytes));
                channel.force(true);
            }
            readVerified(temporary);
            return temporary;
        } catch (IOException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
    }

    private void replaceAtomically(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic file replacement is not supported for "
                    + target + ". The previous data was preserved.", exception);
        }
    }

    private LifeFlowState readVerified(Path file) throws IOException {
        if (Files.notExists(file)) {
            throw new IOException("Storage file is missing: " + file);
        }
        try {
            Envelope envelope = mapper.readValue(file.toFile(), Envelope.class);
            if (envelope.formatVersion != FORMAT_VERSION || envelope.data == null
                    || envelope.revision < 0 || envelope.checksum == null) {
                throw new IOException("Unsupported or incomplete JSON storage file.");
            }
            String actual = checksum(envelope.formatVersion, envelope.revision,
                    envelope.data);
            if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),
                    envelope.checksum.getBytes(StandardCharsets.UTF_8))) {
                throw new IOException("JSON storage checksum does not match.");
            }
            LifeFlowState state = fromPayload(envelope.revision, envelope.data);
            DataValidator.validate(state);
            return state;
        } catch (IllegalArgumentException exception) {
            throw new IOException("JSON storage contains invalid LifeFlow data: "
                    + exception.getMessage(), exception);
        }
    }

    private Envelope envelope(LifeFlowState state) throws IOException {
        Envelope envelope = new Envelope();
        envelope.formatVersion = FORMAT_VERSION;
        envelope.revision = state.getRevision();
        envelope.savedAt = LocalDateTime.now().toString();
        envelope.data = toPayload(state);
        envelope.checksum = checksum(envelope.formatVersion, envelope.revision,
                envelope.data);
        return envelope;
    }

    private String checksum(int formatVersion, long revision, Payload payload)
            throws IOException {
        try {
            ChecksumContent content = new ChecksumContent();
            content.formatVersion = formatVersion;
            content.revision = revision;
            content.data = payload;
            byte[] canonical = mapper.writeValueAsBytes(content);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is not available.", exception);
        }
    }

    private static Payload toPayload(LifeFlowState state) {
        Payload payload = new Payload();
        for (Donor donor : state.getDonors()) {
            DonorData data = new DonorData();
            data.id = donor.getId();
            data.name = donor.getName();
            data.age = donor.getAge();
            data.weightKg = donor.getWeightKg();
            data.bloodType = donor.getBloodType().name();
            data.lastDonationDate = date(donor.getLastDonationDate());
            payload.donors.add(data);
        }
        for (BloodUnit unit : state.getUnits()) {
            UnitData data = new UnitData();
            data.id = unit.getId();
            data.donorId = unit.getDonorId();
            data.bloodType = unit.getBloodType().name();
            data.donationDate = date(unit.getDonationDate());
            data.expiryDate = date(unit.getExpiryDate());
            data.status = unit.getStatus().name();
            payload.bloodUnits.add(data);
        }
        for (BloodRequest request : state.getRequests()) {
            RequestData data = new RequestData();
            data.id = request.getId();
            data.kind = request.getKind();
            data.requesterName = request.getRequesterName();
            data.bloodType = request.getBloodType().name();
            data.quantity = request.getQuantity();
            data.requestDate = date(request.getRequestDate());
            data.status = request.getStatus().name();
            payload.requests.add(data);
        }
        for (FulfilmentRecord record : state.getFulfilments()) {
            FulfilmentData data = new FulfilmentData();
            data.requestId = record.requestId();
            data.processedDate = date(record.processedDate());
            data.unitIds.addAll(record.unitIds());
            payload.fulfilments.add(data);
        }
        return payload;
    }

    private static LifeFlowState fromPayload(long revision, Payload payload) {
        ArrayList<Donor> donors = new ArrayList<>();
        for (DonorData data : safe(payload.donors)) {
            donors.add(new Donor(data.id, data.name, data.age, data.weightKg,
                    BloodType.valueOf(data.bloodType), parseDate(data.lastDonationDate)));
        }
        ArrayList<BloodUnit> units = new ArrayList<>();
        for (UnitData data : safe(payload.bloodUnits)) {
            units.add(new BloodUnit(data.id, data.donorId,
                    BloodType.valueOf(data.bloodType),
                    LocalDate.parse(data.donationDate),
                    LocalDate.parse(data.expiryDate), UnitStatus.valueOf(data.status)));
        }
        ArrayList<BloodRequest> requests = new ArrayList<>();
        for (RequestData data : safe(payload.requests)) {
            BloodRequest request;
            if ("EMERGENCY".equals(data.kind)) {
                request = new EmergencyRequest(data.id, data.requesterName,
                        BloodType.valueOf(data.bloodType), data.quantity,
                        LocalDate.parse(data.requestDate),
                        RequestStatus.valueOf(data.status));
            } else if ("REGULAR".equals(data.kind)) {
                request = new RegularRequest(data.id, data.requesterName,
                        BloodType.valueOf(data.bloodType), data.quantity,
                        LocalDate.parse(data.requestDate),
                        RequestStatus.valueOf(data.status));
            } else {
                throw new IllegalArgumentException("Unknown request kind: " + data.kind);
            }
            requests.add(request);
        }
        ArrayList<FulfilmentRecord> fulfilments = new ArrayList<>();
        for (FulfilmentData data : safe(payload.fulfilments)) {
            fulfilments.add(new FulfilmentRecord(data.requestId,
                    LocalDate.parse(data.processedDate), safe(data.unitIds)));
        }
        return new LifeFlowState(revision, donors, units, requests, fulfilments);
    }

    private static String date(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private static LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public static final class Envelope {
        public int formatVersion;
        public long revision;
        public String savedAt;
        public String checksum;
        public Payload data;
    }

    public static final class ChecksumContent {
        public int formatVersion;
        public long revision;
        public Payload data;
    }

    public static final class Payload {
        public List<DonorData> donors = new ArrayList<>();
        public List<UnitData> bloodUnits = new ArrayList<>();
        public List<RequestData> requests = new ArrayList<>();
        public List<FulfilmentData> fulfilments = new ArrayList<>();
    }

    public static final class DonorData {
        public String id;
        public String name;
        public int age;
        public double weightKg;
        public String bloodType;
        public String lastDonationDate;
    }

    public static final class UnitData {
        public String id;
        public String donorId;
        public String bloodType;
        public String donationDate;
        public String expiryDate;
        public String status;
    }

    public static final class RequestData {
        public String id;
        public String kind;
        public String requesterName;
        public String bloodType;
        public int quantity;
        public String requestDate;
        public String status;
    }

    public static final class FulfilmentData {
        public String requestId;
        public String processedDate;
        public List<String> unitIds = new ArrayList<>();
    }
}
