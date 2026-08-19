package lifeflow.service;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.EligibilityResult;
import lifeflow.model.EmergencyRequest;
import lifeflow.model.FulfilmentRecord;
import lifeflow.model.LifeFlowState;
import lifeflow.model.MatchOutcome;
import lifeflow.model.MatchResult;
import lifeflow.model.RegularRequest;
import lifeflow.model.RequestStatus;
import lifeflow.model.UnitStatus;
import lifeflow.persistence.LifeFlowStore;
import lifeflow.persistence.StorageInfo;

/** Coordinates validation, domain rules, matching, and atomic persistence. */
public final class LifeFlowController implements AutoCloseable {
    private static final int MAX_PROFILE_AGE = 120;
    private static final double MAX_PROFILE_WEIGHT_KG = 500.0;

    private LifeFlowState state;
    private final LifeFlowStore store;
    private final Clock clock;
    private final DonationPolicy donationPolicy;

    public LifeFlowController(LifeFlowState initialState, LifeFlowStore store) {
        this(initialState, store, Clock.systemDefaultZone());
    }

    public LifeFlowController(LifeFlowState initialState, LifeFlowStore store,
                              Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        donationPolicy = new DonationPolicy(clock);
        LifeFlowState checked = Objects.requireNonNull(initialState, "initialState");
        DataValidator.validate(checked, today());
        state = checked.copy();
    }

    public void addDonor(String id, String name, int age, double weight,
                         BloodType type, LocalDate externalLastDonation)
            throws IOException {
        ArrayList<Donor> donors = state.getDonors();
        String donorId = required(id, "Donor ID");
        if (findDonor(donors, donorId) != null) {
            throw new IllegalArgumentException("Donor ID already exists.");
        }
        validatePersonDetails(name, age, weight);
        requireType(type);
        validateExternalDate(externalLastDonation);
        donors.add(new Donor(donorId, safeText(name, "Name"), age, weight,
                type, externalLastDonation));
        commit(donors, state.getUnits(), state.getRequests(), state.getFulfilments());
    }

    public void updateDonor(String id, String name, int age, double weight,
                            BloodType type, LocalDate externalLastDonation)
            throws IOException {
        ArrayList<Donor> donors = state.getDonors();
        ArrayList<BloodUnit> units = state.getUnits();
        Donor donor = findDonor(donors, required(id, "Donor ID"));
        if (donor == null) {
            throw new IllegalArgumentException("Donor was not found.");
        }
        validatePersonDetails(name, age, weight);
        requireType(type);
        validateExternalDate(externalLastDonation);
        if (hasUnitsForDonor(units, donor.getId()) && donor.getBloodType() != type) {
            throw new IllegalArgumentException(
                    "Donor blood type cannot change after blood units are recorded.");
        }
        donor.updateDetails(safeText(name, "Name"), age, weight, type,
                externalLastDonation);
        commit(donors, units, state.getRequests(), state.getFulfilments());
    }

    public EligibilityResult checkDonorEligibility(String donorId,
                                                    LocalDate donationDate) {
        Donor donor = findDonor(state.getDonors(), required(donorId, "Donor"));
        if (donor == null) {
            throw new IllegalArgumentException("Select a registered donor.");
        }
        return donationPolicy.evaluate(donor, donationDate,
                getEffectiveLastDonationDate(donor.getId()));
    }

    public LocalDate getEffectiveLastDonationDate(String donorId) {
        String requiredId = required(donorId, "Donor ID");
        Donor donor = findDonor(state.getDonors(), requiredId);
        if (donor == null) {
            throw new IllegalArgumentException("Donor was not found.");
        }
        LocalDate latest = donor.getExternalLastDonationDate();
        for (BloodUnit unit : state.getUnits()) {
            if (unit.getDonorId().equalsIgnoreCase(donor.getId())
                    && (latest == null || unit.getDonationDate().isAfter(latest))) {
                latest = unit.getDonationDate();
            }
        }
        return latest;
    }

    public void addBloodUnit(String id, String donorId, LocalDate donationDate)
            throws IOException {
        ArrayList<Donor> donors = state.getDonors();
        ArrayList<BloodUnit> units = state.getUnits();
        String unitId = required(id, "Unit ID");
        if (findUnit(units, unitId) != null) {
            throw new IllegalArgumentException("Unit ID already exists.");
        }
        Donor donor = findDonor(donors, required(donorId, "Donor"));
        if (donor == null) {
            throw new IllegalArgumentException("Select a registered donor.");
        }
        EligibilityResult eligibility = donationPolicy.evaluate(donor, donationDate,
                getEffectiveLastDonationDate(donor.getId()));
        if (!eligibility.eligible()) {
            throw new IllegalArgumentException(eligibility.message());
        }
        LocalDate expiryDate = donationPolicy.calculateExpiry(donationDate);
        units.add(new BloodUnit(unitId, donor.getId(), donor.getBloodType(),
                donationDate, expiryDate, UnitStatus.AVAILABLE));
        commit(donors, units, state.getRequests(), state.getFulfilments());
    }

    public void updateUnusedBloodUnitDonationDate(String id,
                                                   LocalDate correctedDonationDate)
            throws IOException {
        ArrayList<BloodUnit> units = state.getUnits();
        BloodUnit unit = findUnit(units, required(id, "Unit ID"));
        if (unit == null) {
            throw new IllegalArgumentException("Blood unit was not found.");
        }
        if (unit.getStatus() == UnitStatus.USED) {
            throw new IllegalArgumentException("A used blood unit cannot be edited.");
        }
        if (correctedDonationDate == null) {
            throw new IllegalArgumentException("Donation date is required.");
        }
        if (correctedDonationDate.isAfter(today())) {
            throw new IllegalArgumentException("Donation date cannot be in the future.");
        }
        unit.correctDates(correctedDonationDate,
                donationPolicy.calculateExpiry(correctedDonationDate));
        commit(state.getDonors(), units, state.getRequests(), state.getFulfilments());
    }

    public void addRequest(String id, String requester, BloodType type,
                           int quantity, boolean emergency) throws IOException {
        ArrayList<BloodRequest> requests = state.getRequests();
        String requestId = required(id, "Request ID");
        if (findRequest(requests, requestId) != null) {
            throw new IllegalArgumentException("Request ID already exists.");
        }
        String requesterName = safeText(requester, "Requester");
        requireType(type);
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        BloodRequest request = emergency
                ? new EmergencyRequest(requestId, requesterName, type, quantity,
                        today(), RequestStatus.PENDING)
                : new RegularRequest(requestId, requesterName, type, quantity,
                        today(), RequestStatus.PENDING);
        requests.add(request);
        commit(state.getDonors(), state.getUnits(), requests, state.getFulfilments());
    }

    public void updatePendingRequest(String id, String requester, BloodType type,
                                     int quantity) throws IOException {
        ArrayList<BloodRequest> requests = state.getRequests();
        BloodRequest request = findRequest(requests, required(id, "Request ID"));
        if (request == null) {
            throw new IllegalArgumentException("Blood request was not found.");
        }
        if (request.getStatus() == RequestStatus.FULFILLED) {
            throw new IllegalArgumentException("A fulfilled request cannot be edited.");
        }
        requireType(type);
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        request.updatePendingDetails(safeText(requester, "Requester"), type, quantity);
        commit(state.getDonors(), state.getUnits(), requests, state.getFulfilments());
    }

    public BloodRequest getNextPendingRequest() {
        return matchingService(state.getUnits()).findNextPending(state.getRequests());
    }

    /** Returns the highest-priority pending request that can be fulfilled in full. */
    public BloodRequest getNextFulfillableRequest() {
        return matchingService(state.getUnits()).findNextFulfillable(
                state.getRequests(), today());
    }

    public MatchResult processNextRequest(LocalDate date) throws IOException {
        if (date == null) {
            throw new IllegalArgumentException("Processing date is required.");
        }
        ArrayList<BloodUnit> units = state.getUnits();
        ArrayList<BloodRequest> requests = state.getRequests();
        BloodInventory inventory = BloodInventory.from(units);
        MatchingService service = new MatchingService(inventory);
        BloodRequest highestPriority = service.findNextPending(requests);
        if (highestPriority == null) {
            return new MatchResult(MatchOutcome.NO_PENDING_REQUEST, null, List.of(),
                    0, "No pending requests.");
        }
        if (date.isAfter(today())) {
            throw new IllegalArgumentException(
                    "Processing date cannot be in the future.");
        }
        if (date.isBefore(highestPriority.getRequestDate())) {
            throw new IllegalArgumentException(
                    "Processing date must be between the request date and today.");
        }
        BloodRequest request = service.findNextFulfillable(requests, date);
        if (request == null) {
            return new MatchResult(MatchOutcome.INSUFFICIENT_STOCK, highestPriority,
                    List.of(), availableFor(highestPriority, inventory, date),
                    "No pending request can be fulfilled in full. "
                            + highestPriority.getId() + " needs "
                            + highestPriority.getQuantity() + " unit(s) of "
                            + highestPriority.getBloodType() + ", but only "
                            + availableFor(highestPriority, inventory, date)
                            + " are available.");
        }
        if (date.isBefore(request.getRequestDate())) {
            throw new IllegalArgumentException(
                    "Processing date must be between the request date and today.");
        }
        int available = availableFor(request, inventory, date);
        if (available < request.getQuantity()) {
            return new MatchResult(MatchOutcome.INSUFFICIENT_STOCK, request,
                    List.of(), available, request.getId() + " needs "
                            + request.getQuantity() + " unit(s) of "
                            + request.getBloodType() + ", but only " + available
                            + " are available.");
        }
        ArrayList<BloodUnit> matched = service.match(request, date);
        ArrayList<FulfilmentRecord> fulfilments = state.getFulfilments();
        fulfilments.add(new FulfilmentRecord(request.getId(), date,
                matched.stream().map(BloodUnit::getId).toList()));
        commit(state.getDonors(), units, requests, fulfilments);
        return new MatchResult(MatchOutcome.FULFILLED, request, matched, available,
                "Request fulfilled using " + matched.size() + " unit(s).");
    }

    public LifeFlowState getStateSnapshot() {
        return state.copy();
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public ArrayList<Donor> getDonors() { return state.getDonors(); }
    public ArrayList<BloodUnit> getUnits() { return state.getUnits(); }
    public ArrayList<BloodRequest> getRequests() { return state.getRequests(); }
    public ArrayList<FulfilmentRecord> getFulfilments() { return state.getFulfilments(); }
    public long getRevision() { return state.getRevision(); }

    public int getPendingRequestCount() {
        return (int) state.getRequests().stream()
                .filter(request -> request.getStatus() == RequestStatus.PENDING).count();
    }

    public int getPendingEmergencyCount() {
        return (int) state.getRequests().stream()
                .filter(request -> request.getStatus() == RequestStatus.PENDING)
                .filter(request -> request instanceof EmergencyRequest).count();
    }

    public int getAvailableUnitCount(LocalDate date) {
        return (int) state.getUnits().stream().filter(unit -> unit.isAvailable(date)).count();
    }

    public HashMap<BloodType, Integer> getStockCounts(LocalDate date) {
        return BloodInventory.from(state.getUnits()).getStockCounts(date);
    }

    public ArrayList<BloodUnit> getAvailableUnits(BloodType type, LocalDate date) {
        return BloodInventory.from(state.getUnits()).getAvailableUnits(type, date);
    }

    public String getNextUnitId() {
        return nextId("U", state.getUnits().stream().map(BloodUnit::getId).toList());
    }

    public String getNextDonorId() {
        return nextId("D", state.getDonors().stream().map(Donor::getId).toList());
    }

    public String getNextRequestId() {
        return nextId("R", state.getRequests().stream().map(BloodRequest::getId).toList());
    }

    public StorageInfo getStorageInfo() { return store.getStorageInfo(); }

    public void saveAll() throws IOException { store.save(state.copy()); }

    @Override
    public void close() throws IOException { store.close(); }

    private void commit(ArrayList<Donor> donors, ArrayList<BloodUnit> units,
                        ArrayList<BloodRequest> requests,
                        ArrayList<FulfilmentRecord> fulfilments) throws IOException {
        LifeFlowState candidate = new LifeFlowState(state.getRevision() + 1,
                donors, units, requests, fulfilments);
        DataValidator.validate(candidate, today());
        store.save(candidate);
        state = candidate.copy();
    }

    private MatchingService matchingService(ArrayList<BloodUnit> units) {
        return new MatchingService(BloodInventory.from(units));
    }

    private static int availableFor(BloodRequest request,
                                    BloodInventory inventory, LocalDate date) {
        return inventory.getAvailableUnits(request.getBloodType(), date).size();
    }

    private static Donor findDonor(List<Donor> donors, String id) {
        return donors.stream().filter(donor -> donor.getId().equalsIgnoreCase(id))
                .findFirst().orElse(null);
    }

    private static BloodUnit findUnit(List<BloodUnit> units, String id) {
        return units.stream().filter(unit -> unit.getId().equalsIgnoreCase(id))
                .findFirst().orElse(null);
    }

    private static BloodRequest findRequest(List<BloodRequest> requests, String id) {
        return requests.stream().filter(request -> request.getId().equalsIgnoreCase(id))
                .findFirst().orElse(null);
    }

    private static boolean hasUnitsForDonor(List<BloodUnit> units, String donorId) {
        return units.stream().anyMatch(unit ->
                unit.getDonorId().equalsIgnoreCase(donorId));
    }

    private static String nextId(String prefix, List<String> ids) {
        long highest = 0;
        for (String id : ids) {
            if (id == null || id.length() <= prefix.length()
                    || !id.regionMatches(true, 0, prefix, 0, prefix.length())) {
                continue;
            }
            String number = id.substring(prefix.length());
            if (!number.chars().allMatch(Character::isDigit)) {
                continue;
            }
            try {
                highest = Math.max(highest, Long.parseLong(number));
            } catch (NumberFormatException ignored) {
                // Custom identifiers outside the long range do not affect numbering.
            }
        }
        if (highest == Long.MAX_VALUE) {
            throw new IllegalStateException("No more automatic IDs are available.");
        }
        return String.format(Locale.ROOT, "%s%06d", prefix, highest + 1);
    }

    private void validatePersonDetails(String name, int age, double weight) {
        safeText(name, "Name");
        if (age < 1 || age > MAX_PROFILE_AGE) {
            throw new IllegalArgumentException("Age must be between 1 and 120.");
        }
        if (!Double.isFinite(weight) || weight <= 0
                || weight > MAX_PROFILE_WEIGHT_KG) {
            throw new IllegalArgumentException(
                    "Weight must be greater than 0 and no more than 500 kg.");
        }
    }

    private void validateExternalDate(LocalDate externalLastDonation) {
        if (externalLastDonation != null && externalLastDonation.isAfter(today())) {
            throw new IllegalArgumentException(
                    "External donation date cannot be in the future.");
        }
    }

    private static void requireType(BloodType type) {
        if (type == null) {
            throw new IllegalArgumentException("Blood type is required.");
        }
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    private static String safeText(String value, String fieldName) {
        String text = required(value, fieldName);
        if (text.contains("|") || text.contains("\n") || text.contains("\r")) {
            throw new IllegalArgumentException(
                    fieldName + " cannot contain | or line breaks.");
        }
        return text;
    }
}
