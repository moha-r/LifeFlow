package lifeflow.service;

import java.io.IOException;
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

/** Coordinates validation, domain services, and atomic JSON persistence. */
public final class LifeFlowController implements AutoCloseable {
    private LifeFlowState state;
    private final LifeFlowStore store;

    public LifeFlowController(LifeFlowState initialState, LifeFlowStore store) {
        this.store = Objects.requireNonNull(store, "store");
        DataValidator.validate(Objects.requireNonNull(initialState, "initialState"));
        state = initialState.copy();
    }

    public void addDonor(String id, String name, int age, double weight,
                         BloodType type, LocalDate lastDonation) throws IOException {
        ArrayList<Donor> donors = state.getDonors();
        String donorId = required(id, "Donor ID");
        if (findDonor(donors, donorId) != null) {
            throw new IllegalArgumentException("Donor ID already exists.");
        }
        validatePersonDetails(name, age, weight);
        requireType(type);
        if (lastDonation != null && lastDonation.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Last donation cannot be in the future.");
        }
        donors.add(new Donor(donorId, safeText(name, "Name"), age, weight,
                type, lastDonation));
        commit(donors, state.getUnits(), state.getRequests(), state.getFulfilments());
    }

    public void addBloodUnit(String id, String donorId, LocalDate donationDate,
                             LocalDate expiryDate) throws IOException {
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
        requireDates(donationDate, expiryDate);
        EligibilityResult eligibility = donor.checkEligibility(donationDate);
        if (!eligibility.eligible()) {
            throw new IllegalArgumentException(eligibility.message());
        }
        if (expiryDate.isBefore(donationDate)) {
            throw new IllegalArgumentException("Expiry date cannot be before donation date.");
        }
        units.add(new BloodUnit(unitId, donor.getId(), donor.getBloodType(),
                donationDate, expiryDate, UnitStatus.AVAILABLE));
        donor.recordDonation(donationDate);
        commit(donors, units, state.getRequests(), state.getFulfilments());
    }

    public void updateDonor(String id, String name, int age, double weight,
                            BloodType type, LocalDate lastDonation) throws IOException {
        ArrayList<Donor> donors = state.getDonors();
        ArrayList<BloodUnit> units = state.getUnits();
        Donor donor = findDonor(donors, required(id, "Donor ID"));
        if (donor == null) {
            throw new IllegalArgumentException("Donor was not found.");
        }
        validatePersonDetails(name, age, weight);
        requireType(type);
        if (lastDonation != null && lastDonation.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Last donation cannot be in the future.");
        }
        if (hasUnitsForDonor(units, donor.getId()) && donor.getBloodType() != type) {
            throw new IllegalArgumentException(
                    "Donor blood type cannot change after blood units are recorded.");
        }
        if (hasUnitsForDonor(units, donor.getId())
                && !Objects.equals(donor.getLastDonationDate(), lastDonation)) {
            throw new IllegalArgumentException(
                    "Last donation cannot change after blood units are recorded.");
        }
        donor.updateDetails(safeText(name, "Name"), age, weight, type, lastDonation);
        commit(donors, units, state.getRequests(), state.getFulfilments());
    }

    public void updateBloodUnitExpiry(String id, LocalDate expiryDate)
            throws IOException {
        ArrayList<BloodUnit> units = state.getUnits();
        BloodUnit unit = findUnit(units, required(id, "Unit ID"));
        if (unit == null) {
            throw new IllegalArgumentException("Blood unit was not found.");
        }
        if (unit.getStatus() == UnitStatus.USED) {
            throw new IllegalArgumentException("A used blood unit cannot be edited.");
        }
        if (unit.isExpired(LocalDate.now())) {
            throw new IllegalArgumentException("An expired blood unit cannot be edited.");
        }
        if (expiryDate == null || expiryDate.isBefore(unit.getDonationDate())) {
            throw new IllegalArgumentException("Expiry date cannot be before donation date.");
        }
        unit.updateExpiryDate(expiryDate);
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
                        LocalDate.now(), RequestStatus.PENDING)
                : new RegularRequest(requestId, requesterName, type, quantity,
                        LocalDate.now(), RequestStatus.PENDING);
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

    public MatchResult processNextRequest(LocalDate date) throws IOException {
        if (date == null) {
            throw new IllegalArgumentException("Processing date is required.");
        }
        ArrayList<BloodUnit> units = state.getUnits();
        ArrayList<BloodRequest> requests = state.getRequests();
        BloodInventory inventory = inventoryFrom(units);
        MatchingService service = new MatchingService(inventory);
        BloodRequest request = service.findNextPending(requests);
        if (request == null) {
            return new MatchResult(MatchOutcome.NO_PENDING_REQUEST, null, List.of(),
                    0, "No pending requests.");
        }
        if (date.isAfter(LocalDate.now())
                || date.isBefore(request.getRequestDate())) {
            throw new IllegalArgumentException(
                    "Processing date must be between the request date and today.");
        }

        int available = inventory.getAvailableUnits(request.getBloodType(), date).size();
        if (available < request.getQuantity()) {
            return new MatchResult(MatchOutcome.INSUFFICIENT_STOCK, request,
                    List.of(), available, "Insufficient compatible stock.");
        }

        ArrayList<BloodUnit> matched = service.match(request, date);
        ArrayList<FulfilmentRecord> fulfilments = state.getFulfilments();
        fulfilments.add(new FulfilmentRecord(request.getId(), date,
                matched.stream().map(BloodUnit::getId).toList()));
        commit(state.getDonors(), units, requests, fulfilments);
        return new MatchResult(MatchOutcome.FULFILLED, request, matched, available,
                "Request fulfilled using " + matched.size() + " unit(s).");
    }

    public EligibilityResult checkDonorEligibility(String donorId, LocalDate date) {
        Donor donor = findDonor(state.getDonors(), required(donorId, "Donor"));
        if (donor == null) {
            throw new IllegalArgumentException("Select a registered donor.");
        }
        return donor.checkEligibility(date);
    }

    public ArrayList<Donor> getDonors() {
        return state.getDonors();
    }

    public ArrayList<BloodUnit> getUnits() {
        return state.getUnits();
    }

    public ArrayList<BloodRequest> getRequests() {
        return state.getRequests();
    }

    public ArrayList<FulfilmentRecord> getFulfilments() {
        return state.getFulfilments();
    }

    public long getRevision() {
        return state.getRevision();
    }

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
        return inventoryFrom(state.getUnits()).getStockCounts(date);
    }

    public ArrayList<BloodUnit> getAvailableUnits(BloodType type, LocalDate date) {
        return inventoryFrom(state.getUnits()).getAvailableUnits(type, date);
    }

    public String getNextUnitId() {
        ArrayList<String> ids = new ArrayList<>();
        for (BloodUnit unit : state.getUnits()) {
            ids.add(unit.getId());
        }
        return nextId("U", ids);
    }

    public String getNextRequestId() {
        ArrayList<String> ids = new ArrayList<>();
        for (BloodRequest request : state.getRequests()) {
            ids.add(request.getId());
        }
        return nextId("R", ids);
    }

    public StorageInfo getStorageInfo() {
        return store.getStorageInfo();
    }

    public void saveAll() throws IOException {
        store.save(state.copy());
    }

    @Override
    public void close() throws IOException {
        store.close();
    }

    private void commit(ArrayList<Donor> donors, ArrayList<BloodUnit> units,
                        ArrayList<BloodRequest> requests,
                        ArrayList<FulfilmentRecord> fulfilments) throws IOException {
        LifeFlowState candidate = new LifeFlowState(state.getRevision() + 1,
                donors, units, requests, fulfilments);
        DataValidator.validate(candidate);
        store.save(candidate);
        state = candidate.copy();
    }

    private MatchingService matchingService(ArrayList<BloodUnit> units) {
        return new MatchingService(inventoryFrom(units));
    }

    private BloodInventory inventoryFrom(List<BloodUnit> units) {
        BloodInventory inventory = new BloodInventory();
        units.forEach(inventory::addUnit);
        return inventory;
    }

    private Donor findDonor(List<Donor> donors, String id) {
        return donors.stream().filter(donor -> donor.getId().equalsIgnoreCase(id))
                .findFirst().orElse(null);
    }

    private BloodUnit findUnit(List<BloodUnit> units, String id) {
        return units.stream().filter(unit -> unit.getId().equalsIgnoreCase(id))
                .findFirst().orElse(null);
    }

    private BloodRequest findRequest(List<BloodRequest> requests, String id) {
        return requests.stream().filter(request -> request.getId().equalsIgnoreCase(id))
                .findFirst().orElse(null);
    }

    private boolean hasUnitsForDonor(List<BloodUnit> units, String donorId) {
        return units.stream().anyMatch(unit -> unit.getDonorId().equalsIgnoreCase(donorId));
    }

    private String nextId(String prefix, List<String> ids) {
        long highest = 0;
        for (String id : ids) {
            if (id == null || id.length() < 2
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
                // An unusually large custom ID does not affect normal numbering.
            }
        }
        if (highest == Long.MAX_VALUE) {
            throw new IllegalStateException("No more automatic IDs are available.");
        }
        return String.format(Locale.ROOT, "%s%03d", prefix, highest + 1);
    }

    private void validatePersonDetails(String name, int age, double weight) {
        safeText(name, "Name");
        if (age <= 0 || weight <= 0 || !Double.isFinite(weight)) {
            throw new IllegalArgumentException("Age and weight must be positive numbers.");
        }
    }

    private void requireDates(LocalDate donationDate, LocalDate expiryDate) {
        if (donationDate == null || expiryDate == null) {
            throw new IllegalArgumentException("Donation and expiry dates are required.");
        }
    }

    private void requireType(BloodType type) {
        if (type == null) {
            throw new IllegalArgumentException("Blood type is required.");
        }
    }

    private String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    private String safeText(String value, String fieldName) {
        String text = required(value, fieldName);
        if (text.contains("|") || text.contains("\n") || text.contains("\r")) {
            throw new IllegalArgumentException(
                    fieldName + " cannot contain | or line breaks.");
        }
        return text;
    }
}
