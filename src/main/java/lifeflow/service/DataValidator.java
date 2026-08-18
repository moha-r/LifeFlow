package lifeflow.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.FulfilmentRecord;
import lifeflow.model.LifeFlowState;
import lifeflow.model.RequestStatus;
import lifeflow.model.UnitStatus;

/** Validates one complete snapshot before it is accepted or persisted. */
public final class DataValidator {
    private DataValidator() {
    }

    public static void validate(LifeFlowState state) {
        validate(state, LocalDate.now());
    }

    public static void validate(LifeFlowState state, LocalDate today) {
        if (state == null || state.getRevision() < 0) {
            throw new IllegalArgumentException("Invalid LifeFlow state revision.");
        }

        ArrayList<Donor> donors = state.getDonors();
        ArrayList<BloodUnit> units = state.getUnits();
        ArrayList<BloodRequest> requests = state.getRequests();
        ArrayList<FulfilmentRecord> fulfilments = state.getFulfilments();

        Map<String, Donor> donorsById = new HashMap<>();
        Map<String, LocalDate> latestUnitDate = new HashMap<>();
        for (Donor donor : donors) {
            validateText(donor.getId(), "Donor ID");
            validateText(donor.getName(), "Donor name");
            String key = key(donor.getId());
            if (donorsById.put(key, donor) != null) {
                throw new IllegalArgumentException("Duplicate donor ID: " + donor.getId());
            }
            if (donor.getAge() <= 0 || donor.getWeightKg() <= 0
                    || !Double.isFinite(donor.getWeightKg())
                    || donor.getBloodType() == null) {
                throw new IllegalArgumentException("Invalid donor details: " + donor.getId());
            }
            if (donor.getLastDonationDate() != null
                    && donor.getLastDonationDate().isAfter(today)) {
                throw new IllegalArgumentException(
                        "Last donation cannot be in the future: " + donor.getId());
            }
        }

        Map<String, BloodUnit> unitsById = new HashMap<>();
        for (BloodUnit unit : units) {
            validateText(unit.getId(), "Unit ID");
            validateText(unit.getDonorId(), "Unit donor ID");
            if (unitsById.put(key(unit.getId()), unit) != null) {
                throw new IllegalArgumentException("Duplicate unit ID: " + unit.getId());
            }
            Donor donor = donorsById.get(key(unit.getDonorId()));
            if (donor == null) {
                throw new IllegalArgumentException(
                        "Unit references a missing donor: " + unit.getId());
            }
            if (unit.getBloodType() == null || unit.getStatus() == null
                    || unit.getDonationDate() == null || unit.getExpiryDate() == null) {
                throw new IllegalArgumentException("Incomplete blood unit: " + unit.getId());
            }
            if (unit.getBloodType() != donor.getBloodType()) {
                throw new IllegalArgumentException(
                        "Unit blood type does not match donor: " + unit.getId());
            }
            if (unit.getDonationDate().isAfter(today)
                    || unit.getExpiryDate().isBefore(unit.getDonationDate())) {
                throw new IllegalArgumentException("Invalid unit dates: " + unit.getId());
            }
            latestUnitDate.merge(key(unit.getDonorId()), unit.getDonationDate(),
                    (first, second) -> first.isAfter(second) ? first : second);
        }

        for (Donor donor : donors) {
            LocalDate latest = latestUnitDate.get(key(donor.getId()));
            if (latest != null && (donor.getLastDonationDate() == null
                    || donor.getLastDonationDate().isBefore(latest))) {
                throw new IllegalArgumentException(
                        "Donor last donation is older than recorded units: "
                                + donor.getId());
            }
        }

        Map<String, BloodRequest> requestsById = new HashMap<>();
        for (BloodRequest request : requests) {
            validateText(request.getId(), "Request ID");
            validateText(request.getRequesterName(), "Requester");
            if (requestsById.put(key(request.getId()), request) != null) {
                throw new IllegalArgumentException(
                        "Duplicate request ID: " + request.getId());
            }
            if (request.getBloodType() == null || request.getStatus() == null
                    || request.getRequestDate() == null || request.getQuantity() <= 0
                    || request.getRequestDate().isAfter(today)) {
                throw new IllegalArgumentException(
                        "Invalid blood request: " + request.getId());
            }
        }

        Set<String> fulfilledRequestIds = new HashSet<>();
        Set<String> auditedUnitIds = new HashSet<>();
        for (FulfilmentRecord record : fulfilments) {
            validateText(record.requestId(), "Fulfilment request ID");
            BloodRequest request = requestsById.get(key(record.requestId()));
            if (request == null || request.getStatus() != RequestStatus.FULFILLED) {
                throw new IllegalArgumentException(
                        "Fulfilment references a missing or pending request: "
                                + record.requestId());
            }
            if (!fulfilledRequestIds.add(key(record.requestId()))) {
                throw new IllegalArgumentException(
                        "Request has more than one fulfilment: " + record.requestId());
            }
            if (record.processedDate() == null || record.processedDate().isAfter(today)
                    || record.processedDate().isBefore(request.getRequestDate())
                    || record.unitIds().size() != request.getQuantity()) {
                throw new IllegalArgumentException(
                        "Invalid fulfilment details: " + record.requestId());
            }
            for (String unitId : record.unitIds()) {
                BloodUnit unit = unitsById.get(key(unitId));
                if (unit == null || unit.getStatus() != UnitStatus.USED
                        || unit.getBloodType() != request.getBloodType()
                        || record.processedDate().isBefore(unit.getDonationDate())
                        || record.processedDate().isAfter(unit.getExpiryDate())) {
                    throw new IllegalArgumentException(
                            "Fulfilment contains an invalid unit: " + unitId);
                }
                if (!auditedUnitIds.add(key(unitId))) {
                    throw new IllegalArgumentException(
                            "Unit is used by more than one request: " + unitId);
                }
            }
        }

        for (BloodRequest request : requests) {
            boolean audited = fulfilledRequestIds.contains(key(request.getId()));
            if ((request.getStatus() == RequestStatus.FULFILLED) != audited) {
                throw new IllegalArgumentException(
                        "Request status and fulfilment history disagree: "
                                + request.getId());
            }
        }
        for (BloodUnit unit : units) {
            boolean audited = auditedUnitIds.contains(key(unit.getId()));
            if ((unit.getStatus() == UnitStatus.USED) != audited) {
                throw new IllegalArgumentException(
                        "Unit status and fulfilment history disagree: " + unit.getId());
            }
        }
    }

    private static void validateText(String value, String field) {
        if (value == null || value.trim().isEmpty() || value.contains("|")
                || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException(field + " contains invalid text.");
        }
    }

    private static String key(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
