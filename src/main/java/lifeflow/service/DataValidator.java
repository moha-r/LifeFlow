package lifeflow.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
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
        if (state == null || state.getRevision() < 0 || today == null) {
            throw new IllegalArgumentException("Invalid LifeFlow state revision.");
        }

        ArrayList<Donor> donors = state.getDonors();
        ArrayList<BloodUnit> units = state.getUnits();
        ArrayList<BloodRequest> requests = state.getRequests();
        ArrayList<FulfilmentRecord> fulfilments = state.getFulfilments();

        Map<String, Donor> donorsById = validateDonors(donors, today);
        Map<String, ArrayList<LocalDate>> donationsByDonor = new HashMap<>();
        Map<String, BloodUnit> unitsById = validateUnits(
                units, donorsById, donationsByDonor, today);
        validateDonationHistory(donors, donationsByDonor);
        Map<String, BloodRequest> requestsById = validateRequests(requests, today);
        validateFulfilments(requests, fulfilments, requestsById, units, unitsById,
                today);
    }

    private static Map<String, Donor> validateDonors(ArrayList<Donor> donors,
                                                      LocalDate today) {
        Map<String, Donor> donorsById = new HashMap<>();
        for (Donor donor : donors) {
            validateText(donor.getId(), "Donor ID");
            validateText(donor.getName(), "Donor name");
            if (donorsById.put(key(donor.getId()), donor) != null) {
                throw new IllegalArgumentException("Duplicate donor ID: " + donor.getId());
            }
            if (donor.getAge() < 1 || donor.getAge() > 120
                    || donor.getWeightKg() <= 0 || donor.getWeightKg() > 500
                    || !Double.isFinite(donor.getWeightKg())
                    || donor.getBloodType() == null) {
                throw new IllegalArgumentException("Invalid donor details: " + donor.getId());
            }
            if (donor.getExternalLastDonationDate() != null
                    && donor.getExternalLastDonationDate().isAfter(today)) {
                throw new IllegalArgumentException(
                        "External donation cannot be in the future: " + donor.getId());
            }
        }
        return donorsById;
    }

    private static Map<String, BloodUnit> validateUnits(
            ArrayList<BloodUnit> units, Map<String, Donor> donorsById,
            Map<String, ArrayList<LocalDate>> donationsByDonor, LocalDate today) {
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
            long shelfLife = ChronoUnit.DAYS.between(
                    unit.getDonationDate(), unit.getExpiryDate());
            if (unit.getDonationDate().isAfter(today) || shelfLife < 0
                    || shelfLife > DonationPolicy.UNIT_SHELF_LIFE_DAYS) {
                throw new IllegalArgumentException("Invalid unit dates: " + unit.getId());
            }
            donationsByDonor.computeIfAbsent(key(unit.getDonorId()), ignored ->
                    new ArrayList<>()).add(unit.getDonationDate());
        }
        return unitsById;
    }

    private static void validateDonationHistory(
            ArrayList<Donor> donors,
            Map<String, ArrayList<LocalDate>> donationsByDonor) {
        for (Donor donor : donors) {
            ArrayList<LocalDate> internalDates = donationsByDonor.getOrDefault(
                    key(donor.getId()), new ArrayList<>());
            LocalDate external = donor.getExternalLastDonationDate();
            if (external != null && internalDates.contains(external)) {
                throw new IllegalArgumentException(
                        "External donation duplicates a recorded unit: " + donor.getId());
            }
            ArrayList<LocalDate> allDates = new ArrayList<>(internalDates);
            if (external != null) {
                allDates.add(external);
            }
            allDates.sort(Comparator.naturalOrder());
            for (int index = 1; index < allDates.size(); index++) {
                LocalDate previous = allDates.get(index - 1);
                LocalDate current = allDates.get(index);
                if (current.isBefore(previous.plusMonths(
                        DonationPolicy.WAITING_MONTHS))) {
                    throw new IllegalArgumentException(
                            "Donations are less than three months apart for donor: "
                                    + donor.getId());
                }
            }
        }
    }

    private static Map<String, BloodRequest> validateRequests(
            ArrayList<BloodRequest> requests, LocalDate today) {
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
        return requestsById;
    }

    private static void validateFulfilments(
            ArrayList<BloodRequest> requests,
            ArrayList<FulfilmentRecord> fulfilments,
            Map<String, BloodRequest> requestsById,
            ArrayList<BloodUnit> units,
            Map<String, BloodUnit> unitsById,
            LocalDate today) {
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
