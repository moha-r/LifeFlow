package lifeflow.service;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lifeflow.model.AppointmentStatus;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.DonationAppointment;
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
    public static final int REGULAR_STALE_DAYS = 7;
    public static final int EMERGENCY_STALE_DAYS = 2;

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
            throw new lifeflow.model.exception.DuplicateIdException("Donor", id);
        }
        validatePersonDetails(name, age, weight);
        requireType(type);
        validateExternalDate(externalLastDonation);
        donors.add(new Donor(donorId, safeText(name, "Name"), age, weight,
                type, externalLastDonation));
        ArrayList<String> logs = addLog(state.getLogs(), "Added donor " + donorId + " (" + type + ")");
        commit(donors, state.getUnits(), state.getRequests(), state.getFulfilments(), logs);
    }

    public void updateDonor(String id, String name, int age, double weight,
                            BloodType type, LocalDate externalLastDonation)
            throws IOException {
        ArrayList<Donor> donors = state.getDonors();
        ArrayList<BloodUnit> units = state.getUnits();
        Donor donor = findDonor(donors, required(id, "Donor ID"));
        if (donor == null) {
            throw new lifeflow.model.exception.EntityNotFoundException("Donor", id);
        }
        validatePersonDetails(name, age, weight);
        requireType(type);
        validateExternalDate(externalLastDonation);
        if (hasUnitsForDonor(units, donor.getId()) && donor.getBloodType() != type) {
            throw new lifeflow.model.exception.ImmutableRecordException("Donor",
                    donor.getId(),
                    "blood type cannot change after blood units are recorded.");
        }
        if (donor.getBloodType() != type && donorHasActiveAppointment(donor.getId())) {
            throw new lifeflow.model.exception.ImmutableRecordException("Donor",
                    donor.getId(),
                    "blood type cannot change while the donor has a booked appointment.");
        }
        donor.updateDetails(safeText(name, "Name"), age, weight, type,
                externalLastDonation);
        ArrayList<String> logs = addLog(state.getLogs(), "Updated donor " + donor.getId());
        commit(donors, units, state.getRequests(), state.getFulfilments(), logs);
    }

    public EligibilityResult checkDonorEligibility(String donorId,
                                                    LocalDate donationDate) {
        Donor donor = findDonor(state.getDonors(), required(donorId, "Donor"));
        if (donor == null) {
            throw new lifeflow.model.exception.ValidationException("Select a registered donor.", "donorId");
        }
        return donationPolicy.evaluate(donor, donationDate,
                getEffectiveLastDonationDate(donor.getId()));
    }

    public LocalDate getEffectiveLastDonationDate(String donorId) {
        String requiredId = required(donorId, "Donor ID");
        Donor donor = findDonor(state.getDonors(), requiredId);
        if (donor == null) {
            throw new lifeflow.model.exception.EntityNotFoundException("Donor", requiredId);
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
            throw new lifeflow.model.exception.DuplicateIdException("Blood unit", id);
        }
        Donor donor = findDonor(donors, required(donorId, "Donor"));
        if (donor == null) {
            throw new lifeflow.model.exception.EntityNotFoundException("Donor", donorId);
        }
        EligibilityResult eligibility = donationPolicy.evaluate(donor, donationDate,
                getEffectiveLastDonationDate(donor.getId()));
        if (!eligibility.eligible()) {
            throw new lifeflow.model.exception.EligibilityException(eligibility);
        }
        LocalDate expiryDate = donationPolicy.calculateExpiry(donationDate);
        units.add(new BloodUnit(unitId, donor.getId(), donor.getBloodType(),
                donationDate, expiryDate, UnitStatus.AVAILABLE));
        ArrayList<String> logs = addLog(state.getLogs(), "Added blood unit " + unitId + " from donor " + donor.getId());
        commit(donors, units, state.getRequests(), state.getFulfilments(), logs);
    }

    public void updateUnusedBloodUnitDonationDate(String id,
                                                   LocalDate correctedDonationDate)
            throws IOException {
        ArrayList<BloodUnit> units = state.getUnits();
        BloodUnit unit = findUnit(units, required(id, "Unit ID"));
        if (unit == null) {
            throw new lifeflow.model.exception.EntityNotFoundException("Blood unit", id);
        }
        if (unit.getStatus() == UnitStatus.USED) {
            throw new lifeflow.model.exception.ImmutableRecordException("Blood unit", id, "cannot be edited because it is used");
        }
        if (unit.getStatus() == UnitStatus.RESERVED) {
            throw new lifeflow.model.exception.ImmutableRecordException("Blood unit", id, "cannot be edited because it is reserved for a request");
        }
        if (unit.getStatus() == UnitStatus.DISCARDED) {
            throw new lifeflow.model.exception.ImmutableRecordException("Blood unit", id, "cannot be edited because it is discarded");
        }
        if (correctedDonationDate == null) {
            throw new lifeflow.model.exception.ValidationException("Donation date is required.", "donationDate");
        }
        if (correctedDonationDate.isAfter(today())) {
            throw new lifeflow.model.exception.ValidationException("Donation date cannot be in the future.", "donationDate");
        }
        unit.correctDates(correctedDonationDate,
                donationPolicy.calculateExpiry(correctedDonationDate));
        ArrayList<String> logs = addLog(state.getLogs(),
                "Corrected donation date of unit " + id);
        commit(state.getDonors(), units, state.getRequests(), state.getFulfilments(), logs);
    }

public void addRequest(String id, String requester, BloodType type,
                       int quantity, boolean emergency) throws IOException {
        addRequest(id, requester, null, type, quantity, emergency);
    }

    /** Adds a request placed by a hospital, linked by its stable id. */
    public void addRequest(String id, String requester, String hospitalId,
                           BloodType type, int quantity, boolean emergency)
            throws IOException {
        ArrayList<BloodRequest> requests = state.getRequests();
        String requestId = required(id, "Request ID");
        if (findRequest(requests, requestId) != null) {
            throw new lifeflow.model.exception.DuplicateIdException("Blood request", id);
        }
        String requesterName = safeText(requester, "Requester");
        requireType(type);
        if (quantity <= 0) {
            throw new lifeflow.model.exception.ValidationException("Quantity must be greater than zero.", "quantity");
        }
        BloodRequest request = emergency
                ? new EmergencyRequest(requestId, requesterName, type, quantity,
                        today(), RequestStatus.PENDING, hospitalId)
                : new RegularRequest(requestId, requesterName, type, quantity,
                        today(), RequestStatus.PENDING, hospitalId);
        requests.add(request);
        ArrayList<String> logs = addLog(state.getLogs(), "Added " + (emergency ? "emergency" : "regular") + " request " + requestId);
        commit(state.getDonors(), state.getUnits(), requests, state.getFulfilments(), logs);
    }

    public void updatePendingRequest(String id, String requester, BloodType type,
                                     int quantity) throws IOException {
        ArrayList<BloodRequest> requests = state.getRequests();
        ArrayList<FulfilmentRecord> fulfilments = state.getFulfilments();
        ArrayList<BloodUnit> units = state.getUnits();
        BloodRequest request = findRequest(requests, required(id, "Request ID"));
        if (request == null) {
            throw new lifeflow.model.exception.EntityNotFoundException("Blood request", id);
        }
        if (request.getStatus() == RequestStatus.FULFILLED) {
            throw new lifeflow.model.exception.ImmutableRecordException("Blood request", id, "cannot be edited because it is fulfilled");
        }
        if (request.getStatus() == RequestStatus.CANCELLED) {
            throw new lifeflow.model.exception.ImmutableRecordException(
                    "Blood request", id, "is already cancelled");
        }
        requireType(type);
        if (quantity <= 0) {
            throw new lifeflow.model.exception.ValidationException("Quantity must be greater than zero.", "quantity");
        }
        int committed = committedUnitCount(fulfilments, request.getId());
        if (committed > 0 && quantity < committed) {
            throw new lifeflow.model.exception.ValidationException(
                    "Quantity cannot be reduced below the " + committed
                            + " unit(s) already committed to this request.",
                    "quantity");
        }
        if (committed > 0 && type != request.getBloodType()) {
            FulfilmentRecord record = findFulfilment(fulfilments, request.getId());
            for (String unitId : record.unitIds()) {
                BloodUnit unit = findUnit(units, unitId);
                if (unit != null && !type.canReceiveFrom(unit.getBloodType())) {
                    throw new lifeflow.model.exception.ValidationException(
                            "Blood type cannot change while a committed unit ("
                                    + unitId + ") does not support it.",
                            "bloodType");
                }
            }
        }
        request.updatePendingDetails(safeText(requester, "Requester"), type, quantity);
        ArrayList<String> logs = addLog(state.getLogs(), "Updated request " + request.getId());
        commit(state.getDonors(), units, requests, fulfilments, logs);
    }

    public BloodRequest getNextPendingRequest() {
        return matchingService(state.getUnits()).findNextPending(state.getRequests());
    }

    /** Cancels a pending request; the reason is recorded in the audit log. */
    public void declineRequest(String id, String reason) throws IOException {
        ArrayList<BloodRequest> requests = state.getRequests();
        BloodRequest request = findRequest(requests, required(id, "Request ID"));
        if (request == null) {
            throw new lifeflow.model.exception.EntityNotFoundException("Blood request", id);
        }
        if (request.getStatus() == RequestStatus.FULFILLED) {
            throw new lifeflow.model.exception.ImmutableRecordException(
                    "Blood request", id, "cannot be declined because it is fulfilled");
        }
        if (request.getStatus() == RequestStatus.CANCELLED) {
            throw new lifeflow.model.exception.ImmutableRecordException(
                    "Blood request", id, "is already cancelled");
        }
        String declineReason = safeText(reason, "Reason");
        request.markCancelled();
        ArrayList<DonationAppointment> appointments = state.getAppointments();
        ArrayList<BloodUnit> units = state.getUnits();
        ArrayList<FulfilmentRecord> fulfilments = state.getFulfilments();
        ArrayList<String> logs = addLog(state.getLogs(),
                "Declined request " + request.getId() + " (" + declineReason + ")");
        logs = cancelAppointmentsForRequest(appointments, request.getId(),
                logs, "request " + request.getId() + " was declined");
        logs = releaseReservedUnits(units, fulfilments, request.getId(),
                logs, "request " + request.getId() + " was declined");
        commit(state.getDonors(), units, requests,
                fulfilments, appointments, logs);
    }

    /** Auto-cancels pending requests that had no stock within their grace period
     * and booked appointments whose date has passed without being completed. */
    public void autoDeclineStaleRequests() throws IOException {
        LocalDate today = today();
        ArrayList<BloodRequest> requests = state.getRequests();
        ArrayList<FulfilmentRecord> fulfilments = state.getFulfilments();
        ArrayList<DonationAppointment> appointments = state.getAppointments();
        ArrayList<BloodRequest> stale = new ArrayList<>();
        for (BloodRequest request : requests) {
            if (request.getStatus() != RequestStatus.PENDING) {
                continue;
            }
            if (committedUnitCount(fulfilments, request.getId()) > 0) {
                continue;
            }
            long days = java.time.temporal.ChronoUnit.DAYS.between(
                    request.getRequestDate(), today);
            int limit = request instanceof EmergencyRequest
                    ? EMERGENCY_STALE_DAYS : REGULAR_STALE_DAYS;
            if (days > limit) {
                stale.add(request);
            }
        }
        boolean hasMissedAppointments = appointments.stream()
                .anyMatch(appointment -> appointment.isBooked()
                        && appointment.isStale(today));
        if (stale.isEmpty() && !hasMissedAppointments) {
            return;
        }
        ArrayList<String> logs = state.getLogs();
        for (BloodRequest request : stale) {
            int limit = request instanceof EmergencyRequest
                    ? EMERGENCY_STALE_DAYS : REGULAR_STALE_DAYS;
            request.markCancelled();
            logs = addLog(logs, "Auto-cancelled request " + request.getId()
                    + " (no stock within " + limit + " days)");
            logs = cancelAppointmentsForRequest(appointments, request.getId(),
                    logs, "request " + request.getId() + " was auto-cancelled");
        }
        for (DonationAppointment appointment : appointments) {
            if (appointment.isBooked() && appointment.isStale(today)) {
                appointment.markCancelled();
                logs = addLog(logs, "Cancelled missed appointment "
                        + appointment.getId());
            }
        }
        commit(state.getDonors(), state.getUnits(), requests,
                fulfilments, appointments, logs);
    }

    /** Returns the highest-priority pending request that can be fulfilled in full. */
    public BloodRequest getNextFulfillableRequest() {
        return matchingService(state.getUnits()).findNextFulfillable(
                state.getRequests(), today(), committedByRequest());
    }

    public MatchResult processNextRequest(LocalDate date) throws IOException {
        if (date == null) {
            throw new lifeflow.model.exception.ValidationException("Processing date is required.", "processingDate");
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
            throw new lifeflow.model.exception.ValidationException(
                    "Processing date cannot be in the future.", "processingDate");
        }
        if (date.isBefore(highestPriority.getRequestDate())) {
            throw new lifeflow.model.exception.ValidationException("Processing date must be between the request date and today.", "processingDate");
        }
        BloodRequest request = service.findNextFulfillable(requests, date,
                committedByRequest());
        if (request == null) {
            int available = availableFor(highestPriority, inventory, date);
            return new MatchResult(MatchOutcome.INSUFFICIENT_STOCK, highestPriority,
                    List.of(), available,
                    "No pending request can be fulfilled in full. "
                            + highestPriority.getId() + " needs "
                            + highestPriority.getQuantity() + " unit(s) of "
                            + highestPriority.getBloodType() + ", but only "
                            + available + " are available.");
        }
        if (date.isBefore(request.getRequestDate())) {
            throw new lifeflow.model.exception.ValidationException("Processing date must be between the request date and today.", "processingDate");
        }
        ArrayList<FulfilmentRecord> fulfilments = state.getFulfilments();
        int committed = committedUnitCount(fulfilments, request.getId());
        int remaining = request.getQuantity() - committed;
        int available = availableFor(request, inventory, date);
        if (available < remaining) {
            return new MatchResult(MatchOutcome.INSUFFICIENT_STOCK, request,
                    List.of(), available, request.getId() + " needs "
                            + remaining + " more unit(s) of "
                            + request.getBloodType() + " ("
                            + committed + " already reserved), but only "
                            + available + " are available.");
        }
        ArrayList<BloodUnit> matched = service.match(request, date, remaining);
        ArrayList<String> unitIds = new ArrayList<>();
        if (committed > 0) {
            unitIds.addAll(findFulfilment(fulfilments, request.getId()).unitIds());
            markReservedUnitsUsed(units, unitIds);
        }
        for (BloodUnit matchedUnit : matched) {
            unitIds.add(matchedUnit.getId());
        }
        request.markFulfilled();
        replaceFulfilment(fulfilments, new FulfilmentRecord(request.getId(),
                date, unitIds));
        ArrayList<DonationAppointment> appointments = state.getAppointments();
        ArrayList<String> logs = addLog(state.getLogs(), "Fulfilled request " + request.getId() + " (" + unitIds.size() + " units)");
        logs = cancelAppointmentsForRequest(appointments, request.getId(),
                logs, "request " + request.getId() + " was fulfilled");
        commit(state.getDonors(), units, requests, fulfilments, appointments, logs);
        return new MatchResult(MatchOutcome.FULFILLED, request, matched, available,
                "Request fulfilled using " + unitIds.size() + " unit(s).");
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


    private ArrayList<String> addLog(ArrayList<String> logs, String message) {
        String timestamp = java.time.LocalDateTime.now(clock)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        logs.add(0, "[" + timestamp + "] " + message);
        if (logs.size() > 500) logs.remove(logs.size() - 1);
        return logs;
    }

    public MatchResult processSpecificRequest(String requestId, LocalDate date,
                                               lifeflow.model.MatchMode mode)
            throws IOException {
        if (date == null) {
            throw new lifeflow.model.exception.ValidationException(
                    "Processing date is required.", "processingDate");
        }
        if (date.isAfter(today())) {
            throw new lifeflow.model.exception.ValidationException(
                    "Processing date cannot be in the future.", "processingDate");
        }
        ArrayList<BloodUnit> units = state.getUnits();
        ArrayList<BloodRequest> requests = state.getRequests();
        ArrayList<FulfilmentRecord> fulfilments = state.getFulfilments();
        BloodRequest req = findRequest(requests, required(requestId, "Request ID"));
        if (req == null) {
            throw new lifeflow.model.exception.EntityNotFoundException(
                    "Blood request", requestId);
        }
        if (req.getStatus() == RequestStatus.FULFILLED) {
            throw new lifeflow.model.exception.ImmutableRecordException(
                    "Blood request", requestId,
                    "cannot be processed because it is already fulfilled");
        }
        if (req.getStatus() == RequestStatus.CANCELLED) {
            throw new lifeflow.model.exception.ImmutableRecordException(
                    "Blood request", requestId,
                    "cannot be processed because it is cancelled");
        }
        if (date.isBefore(req.getRequestDate())) {
            throw new lifeflow.model.exception.ValidationException(
                    "Processing date must be between the request date and today.",
                    "processingDate");
        }
        int committed = committedUnitCount(fulfilments, req.getId());
        int remaining = req.getQuantity() - committed;
        if (remaining <= 0) {
            throw new lifeflow.model.exception.ImmutableRecordException(
                    "Blood request", requestId,
                    "has all its units reserved by volunteers");
        }
        MatchingService service = new MatchingService(
                BloodInventory.from(units), mode);
        ArrayList<BloodUnit> matched = service.match(req, date, remaining);
        if (matched.isEmpty()) {
            throw new lifeflow.model.exception.InsufficientStockException(
                    "Insufficient stock of compatible blood.",
                    req.getBloodType(), remaining, 0);
        }
        ArrayList<String> unitIds = new ArrayList<>();
        if (committed > 0) {
            unitIds.addAll(findFulfilment(fulfilments, req.getId()).unitIds());
            markReservedUnitsUsed(units, unitIds);
        }
        for (BloodUnit matchedUnit : matched) {
            unitIds.add(matchedUnit.getId());
        }
        req.markFulfilled();
        replaceFulfilment(fulfilments, new FulfilmentRecord(req.getId(), date,
                unitIds));
        ArrayList<DonationAppointment> appointments = state.getAppointments();
        ArrayList<String> logs = addLog(state.getLogs(),
                "Fulfilled request " + req.getId() + " ("
                        + req.getBloodType() + ", " + req.getQuantity()
                        + " units, mode=" + mode.name() + ")");
        logs = cancelAppointmentsForRequest(appointments, req.getId(),
                logs, "request " + req.getId() + " was fulfilled");
        commit(state.getDonors(), units, requests, fulfilments, appointments, logs);
        return new MatchResult(MatchOutcome.FULFILLED, req, matched,
                req.getQuantity(),
                "Request fulfilled using " + unitIds.size() + " unit(s).");
    }
    
    public void discardBloodUnit(String id) throws IOException {
        ArrayList<BloodUnit> units = state.getUnits();
        BloodUnit toDiscard = findUnit(units, required(id, "Unit ID"));
        if (toDiscard == null) {
            throw new lifeflow.model.exception.EntityNotFoundException(
                    "Blood unit", id);
        }
        if (toDiscard.getStatus() == UnitStatus.USED) {
            throw new lifeflow.model.exception.ImmutableRecordException(
                    "Blood unit", id,
                    "cannot be discarded because it was used for a fulfilled request");
        }
        if (toDiscard.getStatus() == UnitStatus.RESERVED) {
            throw new lifeflow.model.exception.ImmutableRecordException(
                    "Blood unit", id,
                    "cannot be discarded because it is reserved for a request");
        }
        if (toDiscard.getStatus() == UnitStatus.DISCARDED) {
            throw new lifeflow.model.exception.ImmutableRecordException(
                    "Blood unit", id, "has already been discarded");
        }
        toDiscard.markDiscarded();
        String reason = toDiscard.isExpired(today()) ? "expired" : "manually discarded";
        ArrayList<String> logs = addLog(state.getLogs(),
                "Discarded blood unit " + id + " (" + reason + ")");
        commit(state.getDonors(), units, state.getRequests(),
                state.getFulfilments(), logs);
    }

    public DonationAppointment bookDonationAppointment(
            String donorId, String hospitalId, LocalDate appointmentDate,
            String linkedRequestId) throws IOException {
        Donor donor = findDonor(state.getDonors(), required(donorId, "Donor ID"));
        if (donor == null) {
            throw new lifeflow.model.exception.EntityNotFoundException("Donor", donorId);
        }
        String hospital = safeText(hospitalId, "Hospital ID");
        if (appointmentDate == null) {
            throw new lifeflow.model.exception.ValidationException(
                    "Appointment date is required.", "appointmentDate");
        }
        if (appointmentDate.isBefore(today())) {
            throw new lifeflow.model.exception.ValidationException(
                    "Appointment date cannot be in the past.", "appointmentDate");
        }
        ArrayList<DonationAppointment> appointments = state.getAppointments();
        for (DonationAppointment existing : appointments) {
            if (existing.getDonorId().equalsIgnoreCase(donorId)
                    && existing.isBooked() && !existing.isStale(today())) {
                throw new lifeflow.model.exception.ValidationException(
                        "You already have a booked appointment.", "appointment");
            }
        }
        EligibilityResult eligibility = eligibilityOnDate(donor, appointmentDate);
        if (!eligibility.eligible()) {
            throw new lifeflow.model.exception.EligibilityException(eligibility);
        }
        String linked = linkedRequestId == null ? null
                : linkedRequestId.trim();
        if (linked != null && !linked.isEmpty()) {
            BloodRequest request = findRequest(state.getRequests(), linked);
            if (request == null) {
                throw new lifeflow.model.exception.EntityNotFoundException(
                        "Blood request", linked);
            }
            if (request.getStatus() != RequestStatus.PENDING) {
                throw new lifeflow.model.exception.ValidationException(
                        "That request is no longer open for volunteers.",
                        "request");
            }
            if (!request.getBloodType().canReceiveFrom(donor.getBloodType())) {
                throw new lifeflow.model.exception.ValidationException(
                        "Your blood type cannot support that request.",
                        "request");
            }
        }
        DonationAppointment appointment = new DonationAppointment(
                getNextAppointmentId(), donor.getId(), hospital,
                appointmentDate, linked, AppointmentStatus.BOOKED);
        appointments.add(appointment);
        ArrayList<String> logs = addLog(state.getLogs(),
                "Booked appointment " + appointment.getId() + " for donor "
                        + donor.getId() + " at " + hospital
                        + (linked == null ? "" : " (volunteer for " + linked + ")"));
        commit(state.getDonors(), state.getUnits(), state.getRequests(),
                state.getFulfilments(), appointments, logs);
        return appointment;
    }

    public void cancelDonationAppointment(String appointmentId, String donorId)
            throws IOException {
        ArrayList<DonationAppointment> appointments = state.getAppointments();
        DonationAppointment appointment = findAppointment(appointments,
                required(appointmentId, "Appointment ID"));
        if (appointment == null) {
            throw new lifeflow.model.exception.EntityNotFoundException(
                    "Appointment", appointmentId);
        }
        if (!appointment.getDonorId().equalsIgnoreCase(donorId)) {
            throw new lifeflow.model.exception.ValidationException(
                    "You can only cancel your own appointment.", "appointment");
        }
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new lifeflow.model.exception.ImmutableRecordException(
                    "Appointment", appointmentId,
                    "can only be cancelled while it is booked");
        }
        appointment.markCancelled();
        ArrayList<String> logs = addLog(state.getLogs(),
                "Cancelled appointment " + appointment.getId());
        commit(state.getDonors(), state.getUnits(), state.getRequests(),
                state.getFulfilments(), appointments, logs);
    }

    public void completeDonationAppointment(String appointmentId,
                                            String hospitalId,
                                            LocalDate donationDate)
            throws IOException {
        ArrayList<DonationAppointment> appointments = state.getAppointments();
        DonationAppointment appointment = findAppointment(appointments,
                required(appointmentId, "Appointment ID"));
        if (appointment == null) {
            throw new lifeflow.model.exception.EntityNotFoundException(
                    "Appointment", appointmentId);
        }
        if (!appointment.getHospitalId().equalsIgnoreCase(hospitalId)) {
            throw new lifeflow.model.exception.ValidationException(
                    "This appointment belongs to another hospital.",
                    "appointment");
        }
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new lifeflow.model.exception.ImmutableRecordException(
                    "Appointment", appointmentId,
                    "can only be completed while it is booked");
        }
        if (donationDate == null) {
            throw new lifeflow.model.exception.ValidationException(
                    "Donation date is required.", "donationDate");
        }
        if (donationDate.isAfter(today())) {
            throw new lifeflow.model.exception.ValidationException(
                    "Donation date cannot be in the future.", "donationDate");
        }
        if (donationDate.isBefore(appointment.getAppointmentDate())) {
            throw new lifeflow.model.exception.ValidationException(
                    "Donation cannot be recorded before the appointment date.",
                    "donationDate");
        }
        Donor donor = findDonor(state.getDonors(), appointment.getDonorId());
        if (donor == null) {
            throw new lifeflow.model.exception.EntityNotFoundException(
                    "Donor", appointment.getDonorId());
        }
        EligibilityResult eligibility = donationPolicy.evaluate(donor,
                donationDate, getEffectiveLastDonationDate(donor.getId()));
        if (!eligibility.eligible()) {
            throw new lifeflow.model.exception.EligibilityException(eligibility);
        }
        ArrayList<BloodUnit> units = state.getUnits();
        String unitId = getNextUnitId();
        BloodUnit unit = new BloodUnit(unitId, donor.getId(), donor.getBloodType(),
                donationDate, donationPolicy.calculateExpiry(donationDate),
                UnitStatus.AVAILABLE);
        units.add(unit);
        appointment.markCompleted();
        ArrayList<FulfilmentRecord> fulfilments = state.getFulfilments();
        ArrayList<BloodRequest> requests = state.getRequests();
        ArrayList<String> logs = addLog(state.getLogs(),
                "Completed appointment " + appointment.getId()
                        + " (unit " + unitId + " recorded)");
        String linked = appointment.getLinkedRequestId();
        if (linked != null && !linked.isBlank()) {
            BloodRequest request = findRequest(requests, linked);
            if (request != null && request.getStatus() == RequestStatus.PENDING) {
                logs = commitVolunteerUnit(units, fulfilments, unit,
                        request, donationDate, logs, appointments);
            }
        }
        commit(state.getDonors(), units, requests,
                fulfilments, appointments, logs);
    }

    public ArrayList<DonationAppointment> getAppointmentsForDonor(
            String donorId) {
        ArrayList<DonationAppointment> matches = new ArrayList<>();
        for (DonationAppointment appointment : state.getAppointments()) {
            if (appointment.getDonorId().equalsIgnoreCase(donorId)) {
                matches.add(appointment);
            }
        }
        return matches;
    }

    public ArrayList<DonationAppointment> getAppointmentsForHospital(
            String hospitalId) {
        ArrayList<DonationAppointment> matches = new ArrayList<>();
        for (DonationAppointment appointment : state.getAppointments()) {
            if (appointment.getHospitalId().equalsIgnoreCase(hospitalId)) {
                matches.add(appointment);
            }
        }
        return matches;
    }

    public ArrayList<BloodRequest> getUrgentNeedsForDonor(String donorId) {
        Donor donor = findDonor(state.getDonors(), required(donorId, "Donor ID"));
        if (donor == null) {
            throw new lifeflow.model.exception.EntityNotFoundException("Donor", donorId);
        }
        ArrayList<BloodRequest> needs = new ArrayList<>();
        for (BloodRequest request : state.getRequests()) {
            if (request.getStatus() != RequestStatus.PENDING
                    || !request.getBloodType().canReceiveFrom(
                            donor.getBloodType())) {
                continue;
            }
            long days = java.time.temporal.ChronoUnit.DAYS.between(
                    request.getRequestDate(), today());
            int limit = request instanceof EmergencyRequest
                    ? EMERGENCY_STALE_DAYS : REGULAR_STALE_DAYS;
            if (days > limit) {
                continue;
            }
            if (hasActiveVolunteer(donor.getId(), request.getId())) {
                continue;
            }
            needs.add(request);
        }
        needs.sort((first, second) -> {
            if (first instanceof EmergencyRequest
                    && !(second instanceof EmergencyRequest)) {
                return -1;
            }
            if (!(first instanceof EmergencyRequest)
                    && second instanceof EmergencyRequest) {
                return 1;
            }
            return first.getRequestDate().compareTo(second.getRequestDate());
        });
        return needs;
    }

    public boolean donorHasActiveAppointment(String donorId) {
        for (DonationAppointment appointment : state.getAppointments()) {
            if (appointment.getDonorId().equalsIgnoreCase(donorId)
                    && appointment.isBooked()
                    && !appointment.isStale(today())) {
                return true;
            }
        }
        return false;
    }

    public int getVolunteerCountForRequest(String requestId) {
        int count = 0;
        for (DonationAppointment appointment : state.getAppointments()) {
            if (appointment.getLinkedRequestId() != null
                    && appointment.getLinkedRequestId().equalsIgnoreCase(requestId)
                    && appointment.isBooked()
                    && !appointment.isStale(today())) {
                count++;
            }
        }
        return count;
    }

    public int getUpcomingAppointmentCount() {
        int count = 0;
        for (DonationAppointment appointment : state.getAppointments()) {
            if (appointment.isBooked() && !appointment.isStale(today())) {
                count++;
            }
        }
        return count;
    }

    private ArrayList<String> cancelAppointmentsForRequest(
            ArrayList<DonationAppointment> appointments, String requestId,
            ArrayList<String> logs, String reason) {
        for (DonationAppointment appointment : appointments) {
            if (appointment.getLinkedRequestId() != null
                    && appointment.getLinkedRequestId().equalsIgnoreCase(requestId)
                    && appointment.isBooked()) {
                appointment.markCancelled();
                logs = addLog(logs, "Cancelled appointment "
                        + appointment.getId() + " (" + reason + ")");
            }
        }
        return logs;
    }

    /** Reserves a freshly recorded volunteer unit for its linked request and
     * fulfils the request once its full quantity is covered. */
    private ArrayList<String> commitVolunteerUnit(ArrayList<BloodUnit> units,
            ArrayList<FulfilmentRecord> fulfilments, BloodUnit unit,
            BloodRequest request, LocalDate donationDate,
            ArrayList<String> logs,
            ArrayList<DonationAppointment> appointments) {
        unit.markReserved();
        ArrayList<String> unitIds = new ArrayList<>();
        if (findFulfilment(fulfilments, request.getId()) != null) {
            unitIds.addAll(findFulfilment(fulfilments, request.getId()).unitIds());
        }
        unitIds.add(unit.getId());
        replaceFulfilment(fulfilments, new FulfilmentRecord(request.getId(),
                donationDate, unitIds));
        logs = addLog(logs, "Reserved unit " + unit.getId() + " for request "
                + request.getId() + " (" + unitIds.size() + "/"
                + request.getQuantity() + " units)");
        if (unitIds.size() >= request.getQuantity()) {
            request.markFulfilled();
            markReservedUnitsUsed(units, unitIds);
            logs = addLog(logs, "Fulfilled request " + request.getId()
                    + " from volunteer donations");
            logs = cancelAppointmentsForRequest(appointments, request.getId(),
                    logs, "request " + request.getId() + " was fulfilled");
        }
        return logs;
    }

    /** Returns the number of units already committed to the given request. */
    private static int committedUnitCount(
            ArrayList<FulfilmentRecord> fulfilments, String requestId) {
        FulfilmentRecord record = findFulfilment(fulfilments, requestId);
        return record == null ? 0 : record.unitIds().size();
    }

    private static FulfilmentRecord findFulfilment(
            ArrayList<FulfilmentRecord> fulfilments, String requestId) {
        for (FulfilmentRecord record : fulfilments) {
            if (record.requestId().equalsIgnoreCase(requestId)) {
                return record;
            }
        }
        return null;
    }

    private static void replaceFulfilment(
            ArrayList<FulfilmentRecord> fulfilments, FulfilmentRecord record) {
        for (int index = 0; index < fulfilments.size(); index++) {
            if (fulfilments.get(index).requestId()
                    .equalsIgnoreCase(record.requestId())) {
                fulfilments.set(index, record);
                return;
            }
        }
        fulfilments.add(record);
    }

    /** Flips committed units of a request back to the general stock. */
    private ArrayList<String> releaseReservedUnits(ArrayList<BloodUnit> units,
            ArrayList<FulfilmentRecord> fulfilments, String requestId,
            ArrayList<String> logs, String reason) {
        FulfilmentRecord record = findFulfilment(fulfilments, requestId);
        if (record == null) {
            return logs;
        }
        int released = 0;
        for (String unitId : record.unitIds()) {
            BloodUnit unit = findUnit(units, unitId);
            if (unit != null && unit.getStatus() == UnitStatus.RESERVED) {
                unit.markAvailable();
                released++;
            }
        }
        fulfilments.remove(record);
        if (released > 0) {
            logs = addLog(logs, "Released " + released + " reserved unit(s) ("
                    + reason + ")");
        }
        return logs;
    }

    private static void markReservedUnitsUsed(ArrayList<BloodUnit> units,
                                              ArrayList<String> unitIds) {
        for (String unitId : unitIds) {
            BloodUnit unit = findUnit(units, unitId);
            if (unit != null) {
                unit.markUsed();
            }
        }
    }

    private java.util.HashMap<String, Integer> committedByRequest() {
        java.util.HashMap<String, Integer> counts = new java.util.HashMap<>();
        for (FulfilmentRecord record : state.getFulfilments()) {
            counts.put(record.requestId().toLowerCase(Locale.ROOT),
                    record.unitIds().size());
        }
        return counts;
    }

    public String getNextAppointmentId() {
        return nextId("A", state.getAppointments().stream()
                .map(DonationAppointment::getId).toList());
    }

    private boolean hasActiveVolunteer(String donorId, String requestId) {
        for (DonationAppointment appointment : state.getAppointments()) {
            if (appointment.getLinkedRequestId() != null
                    && appointment.getLinkedRequestId().equalsIgnoreCase(requestId)
                    && appointment.getDonorId().equalsIgnoreCase(donorId)
                    && !appointment.isStale(today())) {
                return true;
            }
        }
        return false;
    }

    private EligibilityResult eligibilityOnDate(Donor donor,
                                                LocalDate appointmentDate) {
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.Clock fixed = java.time.Clock.fixed(
                appointmentDate.atStartOfDay(zone).toInstant(), zone);
        return new DonationPolicy(fixed).evaluate(donor, appointmentDate,
                getEffectiveLastDonationDate(donor.getId()));
    }

    private static DonationAppointment findAppointment(
            List<DonationAppointment> appointments, String id) {
        return appointments.stream()
                .filter(appointment -> appointment.getId().equalsIgnoreCase(id))
                .findFirst().orElse(null);
    }

    private void commit(
ArrayList<Donor> donors, ArrayList<BloodUnit> units,
                        ArrayList<BloodRequest> requests,
                        ArrayList<FulfilmentRecord> fulfilments, java.util.ArrayList<String> logs) throws IOException {
        commit(donors, units, requests, fulfilments, state.getAppointments(), logs);
    }

    private void commit(ArrayList<Donor> donors, ArrayList<BloodUnit> units,
                        ArrayList<BloodRequest> requests,
                        ArrayList<FulfilmentRecord> fulfilments,
                        ArrayList<DonationAppointment> appointments,
                        java.util.ArrayList<String> logs) throws IOException {
        LifeFlowState candidate = new LifeFlowState(state.getRevision() + 1,
                donors, units, requests, fulfilments, appointments, logs);
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
            throw new lifeflow.model.exception.ValidationException("Age must be between 1 and 120.", "age");
        }
        if (!Double.isFinite(weight) || weight <= 0
                || weight > MAX_PROFILE_WEIGHT_KG) {
            throw new lifeflow.model.exception.ValidationException("Weight must be greater than 0 and no more than 500 kg.", "weight");
        }
    }

    private void validateExternalDate(LocalDate externalLastDonation) {
        if (externalLastDonation != null && externalLastDonation.isAfter(today())) {
            throw new lifeflow.model.exception.ValidationException("External donation date cannot be in the future.", "externalDonationDate");
        }
    }

    private static void requireType(BloodType type) {
        if (type == null) {
            throw new lifeflow.model.exception.ValidationException("Blood type is required.", "bloodType");
        }
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new lifeflow.model.exception.ValidationException(fieldName + " is required.", fieldName);
        }
        return value.trim();
    }

    private static String safeText(String value, String fieldName) {
        String text = required(value, fieldName);
        if (text.contains("|") || text.contains("\n") || text.contains("\r")) {
            throw new lifeflow.model.exception.ValidationException(fieldName + " cannot contain | or line breaks.", fieldName);
        }
        return text;
    }
}
