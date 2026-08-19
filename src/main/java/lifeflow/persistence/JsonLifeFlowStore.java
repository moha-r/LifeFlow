package lifeflow.persistence;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import lifeflow.service.DonationPolicy;

/** Jackson-backed, checksum-verified, atomic local snapshot storage. */
public final class JsonLifeFlowStore implements LifeFlowStore {
    private static final int LEGACY_FORMAT_VERSION = 1;
    private static final int FORMAT_VERSION = 2;
    private static final DateTimeFormatter RECOVERY_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final Path directory;
    private final Path dataFile;
    private final Path backupFile;
    private final Path previousBackupFile;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private boolean backupCurrent;
    private boolean recoveryRequired;
    private String detail = "Storage ready";

    public JsonLifeFlowStore(Path directory) throws IOException {
        this(directory, Clock.systemDefaultZone());
    }

    public JsonLifeFlowStore(Path directory, Clock clock) throws IOException {
        this.clock = Objects.requireNonNull(clock, "clock");
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
            int version = readFormatVersion(dataFile);
            LifeFlowState state;
            if (version == LEGACY_FORMAT_VERSION) {
                state = migratePrimaryFromVersionOne();
                recoveryRequired = false;
                return state;
            }
            state = readVersionTwoVerified(dataFile);
            backupCurrent = backupMatchesPrimary();
            recoveryRequired = false;
            detail = backupCurrent ? "Storage and backup ready"
                    : "Data loaded; backup needs retry";
            return state;
        } catch (IOException exception) {
            recoveryRequired = true;
            if (!detail.startsWith("Migration")) {
                detail = "Recovery required";
            }
            throw exception;
        }
    }

    @Override
    public void save(LifeFlowState state) throws IOException {
        validateForStorage(state);
        byte[] bytes = mapper.writeValueAsBytes(envelope(state));
        writeVersionTwoAtomically(dataFile, bytes);
        recoveryRequired = false;
        updateBackups(bytes);
    }

    public LifeFlowState restoreLatestBackup() throws IOException {
        if (!recoveryRequired) {
            throw new IOException("Recovery is not required for the current data file.");
        }
        LifeFlowState restored;
        try {
            restored = readAnyVerified(backupFile);
        } catch (IOException currentFailure) {
            restored = readAnyVerified(previousBackupFile);
        }
        byte[] bytes = mapper.writeValueAsBytes(envelope(restored));
        Path temporary = writeVersionTwoTemporary(dataFile, bytes);
        try {
            if (Files.exists(dataFile)) {
                Path recoveryDirectory = directory.resolve("recovery");
                Files.createDirectories(recoveryDirectory);
                Files.copy(dataFile, uniqueRecoveryPath(recoveryDirectory,
                        "lifeflow-corrupt-"));
            }
            replaceAtomically(temporary, dataFile);
        } finally {
            Files.deleteIfExists(temporary);
        }
        recoveryRequired = false;
        updateBackups(bytes);
        detail = backupCurrent ? "Storage restored from backup"
                : "Data restored; backup needs retry";
        return restored;
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

    private LifeFlowState migratePrimaryFromVersionOne() throws IOException {
        LegacyEnvelope legacy = readVersionOneEnvelope(dataFile);
        Path recoveryDirectory = directory.resolve("recovery");
        Files.createDirectories(recoveryDirectory);
        Files.copy(dataFile, uniqueRecoveryPath(recoveryDirectory,
                "lifeflow-v1-before-migration-"));
        try {
            LifeFlowState migrated = fromLegacy(legacy.revision, legacy.data);
            validateForStorage(migrated);
            byte[] bytes = mapper.writeValueAsBytes(envelope(migrated));
            writeVersionTwoAtomically(dataFile, bytes);
            updateBackups(bytes);
            detail = backupCurrent
                    ? "Migrated Version 1 data to Version 2; backup ready"
                    : "Migrated Version 1 data to Version 2; backup needs retry";
            return migrated;
        } catch (RuntimeException | IOException exception) {
            detail = "Migration failed; original Version 1 file preserved";
            throw new IOException("Version 1 data could not be migrated: "
                    + exception.getMessage(), exception);
        }
    }

    private Path uniqueRecoveryPath(Path recoveryDirectory, String prefix) {
        String base = prefix + LocalDateTime.now(clock).format(RECOVERY_TIME);
        Path candidate = recoveryDirectory.resolve(base + ".json");
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = recoveryDirectory.resolve(base + "-" + suffix + ".json");
            suffix++;
        }
        return candidate;
    }

    private void updateBackups(byte[] primaryBytes) {
        try {
            Files.createDirectories(backupFile.getParent());
            if (Files.exists(backupFile)) {
                try {
                    readAnyVerified(backupFile);
                    writeRawAtomically(previousBackupFile,
                            Files.readAllBytes(backupFile));
                } catch (IOException ignored) {
                    // Preserve an existing verified previous backup.
                }
            }
            writeVersionTwoAtomically(backupFile, primaryBytes);
            backupCurrent = true;
            detail = "Storage and backup ready";
        } catch (IOException exception) {
            backupCurrent = false;
            detail = "Data saved; backup needs retry";
        }
    }

    private boolean backupMatchesPrimary() {
        if (Files.notExists(backupFile)) {
            return false;
        }
        try {
            readVersionTwoVerified(backupFile);
            return Files.mismatch(dataFile, backupFile) == -1;
        } catch (IOException exception) {
            return false;
        }
    }

    private int readFormatVersion(Path file) throws IOException {
        if (Files.notExists(file)) {
            throw new IOException("Storage file is missing: " + file);
        }
        JsonNode root = mapper.readTree(file.toFile());
        JsonNode version = root == null ? null : root.get("formatVersion");
        if (version == null || !version.canConvertToInt()) {
            throw new IOException("JSON storage format version is missing.");
        }
        return version.asInt();
    }

    private LifeFlowState readAnyVerified(Path file) throws IOException {
        int version = readFormatVersion(file);
        if (version == FORMAT_VERSION) {
            return readVersionTwoVerified(file);
        }
        if (version == LEGACY_FORMAT_VERSION) {
            LegacyEnvelope legacy = readVersionOneEnvelope(file);
            try {
                LifeFlowState state = fromLegacy(legacy.revision, legacy.data);
                validateForStorage(state);
                return state;
            } catch (RuntimeException exception) {
                throw new IOException("Legacy backup contains invalid LifeFlow data: "
                        + exception.getMessage(), exception);
            }
        }
        throw new IOException("Unsupported JSON storage format version: " + version);
    }

    private LifeFlowState readVersionTwoVerified(Path file) throws IOException {
        try {
            Envelope envelope = mapper.readValue(file.toFile(), Envelope.class);
            if (envelope.formatVersion != FORMAT_VERSION || envelope.data == null
                    || envelope.revision < 0 || envelope.checksum == null) {
                throw new IOException("Unsupported or incomplete JSON storage file.");
            }
            verifyChecksum(envelope.checksum,
                    checksum(envelope.formatVersion, envelope.revision, envelope.data));
            LifeFlowState state = fromPayload(envelope.revision, envelope.data);
            validateForStorage(state);
            return state;
        } catch (RuntimeException exception) {
            throw new IOException("JSON storage contains invalid LifeFlow data: "
                    + exception.getMessage(), exception);
        }
    }

    private LegacyEnvelope readVersionOneEnvelope(Path file) throws IOException {
        try {
            LegacyEnvelope envelope = mapper.readValue(file.toFile(), LegacyEnvelope.class);
            if (envelope.formatVersion != LEGACY_FORMAT_VERSION
                    || envelope.data == null || envelope.revision < 0
                    || envelope.checksum == null) {
                throw new IOException("Unsupported or incomplete Version 1 JSON file.");
            }
            verifyChecksum(envelope.checksum, legacyChecksum(envelope.formatVersion,
                    envelope.revision, envelope.data));
            return envelope;
        } catch (RuntimeException exception) {
            throw new IOException("Version 1 JSON data is invalid: "
                    + exception.getMessage(), exception);
        }
    }

    private void validateForStorage(LifeFlowState state) throws IOException {
        try {
            DataValidator.validate(state, LocalDate.now(clock));
        } catch (IllegalArgumentException exception) {
            throw new IOException("LifeFlow refused invalid data: "
                    + exception.getMessage(), exception);
        }
    }

    private static void verifyChecksum(String expected, String actual)
            throws IOException {
        if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8))) {
            throw new IOException("JSON storage checksum does not match.");
        }
    }

    private Envelope envelope(LifeFlowState state) throws IOException {
        Envelope envelope = new Envelope();
        envelope.formatVersion = FORMAT_VERSION;
        envelope.revision = state.getRevision();
        envelope.savedAt = LocalDateTime.now(clock).toString();
        envelope.data = toPayload(state);
        envelope.checksum = checksum(envelope.formatVersion, envelope.revision,
                envelope.data);
        return envelope;
    }

    private String checksum(int formatVersion, long revision, Payload payload)
            throws IOException {
        ChecksumContent content = new ChecksumContent();
        content.formatVersion = formatVersion;
        content.revision = revision;
        content.data = payload;
        return digest(content);
    }

    private String legacyChecksum(int formatVersion, long revision,
                                  LegacyPayload payload) throws IOException {
        LegacyChecksumContent content = new LegacyChecksumContent();
        content.formatVersion = formatVersion;
        content.revision = revision;
        content.data = payload;
        return digest(content);
    }

    private String digest(Object content) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(content)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is not available.", exception);
        }
    }

    private void writeVersionTwoAtomically(Path target, byte[] bytes)
            throws IOException {
        Path temporary = writeVersionTwoTemporary(target, bytes);
        try {
            replaceAtomically(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path writeVersionTwoTemporary(Path target, byte[] bytes)
            throws IOException {
        Path temporary = writeRawTemporary(target, bytes);
        try {
            readVersionTwoVerified(temporary);
            return temporary;
        } catch (IOException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
    }

    private void writeRawAtomically(Path target, byte[] bytes) throws IOException {
        Path temporary = writeRawTemporary(target, bytes);
        try {
            readAnyVerified(temporary);
            replaceAtomically(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path writeRawTemporary(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true);
        }
        return temporary;
    }

    private static void replaceAtomically(Path temporary, Path target)
            throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic file replacement is not supported for "
                    + target + ". The previous data was preserved.", exception);
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
            data.externalLastDonationDate = date(
                    donor.getExternalLastDonationDate());
            payload.donors.add(data);
        }
        copyUnitsToPayload(state, payload.bloodUnits);
        copyRequestsToPayload(state, payload.requests);
        copyFulfilmentsToPayload(state, payload.fulfilments);
        return payload;
    }

    private static void copyUnitsToPayload(LifeFlowState state,
                                           List<UnitData> output) {
        for (BloodUnit unit : state.getUnits()) {
            UnitData data = new UnitData();
            data.id = unit.getId();
            data.donorId = unit.getDonorId();
            data.bloodType = unit.getBloodType().name();
            data.donationDate = date(unit.getDonationDate());
            data.expiryDate = date(unit.getExpiryDate());
            data.status = unit.getStatus().name();
            output.add(data);
        }
    }

    private static void copyRequestsToPayload(LifeFlowState state,
                                              List<RequestData> output) {
        for (BloodRequest request : state.getRequests()) {
            RequestData data = new RequestData();
            data.id = request.getId();
            data.kind = request.getKind();
            data.requesterName = request.getRequesterName();
            data.bloodType = request.getBloodType().name();
            data.quantity = request.getQuantity();
            data.requestDate = date(request.getRequestDate());
            data.status = request.getStatus().name();
            output.add(data);
        }
    }

    private static void copyFulfilmentsToPayload(
            LifeFlowState state, List<FulfilmentData> output) {
        for (FulfilmentRecord record : state.getFulfilments()) {
            FulfilmentData data = new FulfilmentData();
            data.requestId = record.requestId();
            data.processedDate = date(record.processedDate());
            data.unitIds.addAll(record.unitIds());
            output.add(data);
        }
    }

    private static LifeFlowState fromPayload(long revision, Payload payload) {
        ArrayList<Donor> donors = new ArrayList<>();
        for (DonorData data : safe(payload.donors)) {
            donors.add(new Donor(data.id, data.name, data.age, data.weightKg,
                    BloodType.valueOf(data.bloodType),
                    parseDate(data.externalLastDonationDate)));
        }
        return stateFromParts(revision, donors, safe(payload.bloodUnits),
                safe(payload.requests), safe(payload.fulfilments), false);
    }

    private static LifeFlowState fromLegacy(long revision, LegacyPayload payload) {
        List<UnitData> unitData = safe(payload.bloodUnits);
        Map<String, LocalDate> latestByDonor = new HashMap<>();
        for (UnitData unit : unitData) {
            LocalDate donation = LocalDate.parse(unit.donationDate);
            latestByDonor.merge(unit.donorId.toLowerCase(java.util.Locale.ROOT),
                    donation, (first, second) -> first.isAfter(second) ? first : second);
        }
        ArrayList<Donor> donors = new ArrayList<>();
        for (LegacyDonorData data : safe(payload.donors)) {
            LocalDate legacyDate = parseDate(data.lastDonationDate);
            LocalDate latest = latestByDonor.get(
                    data.id.toLowerCase(java.util.Locale.ROOT));
            LocalDate external = legacyDate != null && legacyDate.equals(latest)
                    ? null : legacyDate;
            donors.add(new Donor(data.id, data.name, data.age, data.weightKg,
                    BloodType.valueOf(data.bloodType), external));
        }
        return stateFromParts(revision, donors, unitData, safe(payload.requests),
                safe(payload.fulfilments), true);
    }

    private static LifeFlowState stateFromParts(
            long revision, ArrayList<Donor> donors, List<UnitData> unitData,
            List<RequestData> requestData, List<FulfilmentData> fulfilmentData,
            boolean normaliseLegacyExpiry) {
        ArrayList<BloodUnit> units = new ArrayList<>();
        for (UnitData data : unitData) {
            LocalDate donation = LocalDate.parse(data.donationDate);
            LocalDate expiry = LocalDate.parse(data.expiryDate);
            LocalDate maximum = donation.plusDays(
                    DonationPolicy.UNIT_SHELF_LIFE_DAYS);
            if (normaliseLegacyExpiry && expiry.isAfter(maximum)) {
                expiry = maximum;
            }
            units.add(new BloodUnit(data.id, data.donorId,
                    BloodType.valueOf(data.bloodType), donation, expiry,
                    UnitStatus.valueOf(data.status)));
        }
        ArrayList<BloodRequest> requests = new ArrayList<>();
        for (RequestData data : requestData) {
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
        for (FulfilmentData data : fulfilmentData) {
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
        public String externalLastDonationDate;
    }

    public static final class LegacyEnvelope {
        public int formatVersion;
        public long revision;
        public String savedAt;
        public String checksum;
        public LegacyPayload data;
    }

    public static final class LegacyChecksumContent {
        public int formatVersion;
        public long revision;
        public LegacyPayload data;
    }

    public static final class LegacyPayload {
        public List<LegacyDonorData> donors = new ArrayList<>();
        public List<UnitData> bloodUnits = new ArrayList<>();
        public List<RequestData> requests = new ArrayList<>();
        public List<FulfilmentData> fulfilments = new ArrayList<>();
    }

    public static final class LegacyDonorData {
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
