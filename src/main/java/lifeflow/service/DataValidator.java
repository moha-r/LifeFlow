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
import lifeflow.model.AppointmentStatus;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.DonationAppointment;
import lifeflow.model.FulfilmentRecord;
import lifeflow.model.LifeFlowState;
import lifeflow.model.RequestStatus;
import lifeflow.model.UnitStatus;
import lifeflow.model.exception.ValidationException;
import lifeflow.model.exception.DuplicateIdException;
import lifeflow.model.exception.EntityNotFoundException;

/** Validates one complete snapshot before it is accepted or persisted. */
public final class DataValidator {
    private DataValidator() {
    }

    public static void validate(LifeFlowState state) {
        validate(state, LocalDate.now());
    }

    public static void validate(LifeFlowState state, LocalDate today) {
        if (state == null || state.getRevision() < 0 || today == null) {
            throw new ValidationException("Invalid LifeFlow state revision.", "state");
        }

        ArrayList<Donor> donors = state.getDonors();
        ArrayList<BloodUnit> units = state.getUnits();
        ArrayList<BloodRequest> requests = state.getRequests();
        ArrayList<FulfilmentRecord> fulfilments = state.getFulfilments();
        ArrayList<DonationAppointment> appointments = state.getAppointments();

        Map<String, Donor> donorsById = validateDonors(donors, today);
        Map<String, ArrayList<LocalDate>> donationsByDonor = new HashMap<>();
        Map<String, BloodUnit> unitsById = validateUnits(
                units, donorsById, donationsByDonor, today);
        validateDonationHistory(donors, donationsByDonor);
        Map<String, BloodRequest> requestsById = validateRequests(requests, today);
        validateFulfilments(requests, fulfilments, requestsById, units, unitsById,
                today);
        validateAppointments(appointments, donorsById, requestsById, today);
    }

    private static Map<String, Donor> validateDonors(ArrayList<Donor> donors,
                                                      LocalDate today) {
        Map<String, Donor> donorsById = new HashMap<>();
        for (Donor donor : donors) {
            validateText(donor.getId(), "Donor ID");
            validateText(donor.getName(), "Donor name");
            if (donorsById.put(key(donor.getId()), donor) != null) {
                throw new DuplicateIdException("Donor", donor.getId());
            }
            if (donor.getAge() < 1 || donor.getAge() > 120
                    || donor.getWeightKg() <= 0 || donor.getWeightKg() > 500
                    || !Double.isFinite(donor.getWeightKg())
                    || donor.getBloodType() == null) {
                throw new ValidationException("Invalid donor details: " + donor.getId(), "donor");
            }
            if (donor.getExternalLastDonationDate() != null
                    && donor.getExternalLastDonationDate().isAfter(today)) {
                throw new ValidationException(
                        "External donation cannot be in the future: " + donor.getId(), "externalLastDonationDate");
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
                throw new DuplicateIdException("Blood unit", unit.getId());
            }
            Donor donor = donorsById.get(key(unit.getDonorId()));
            if (donor == null) {
                throw new EntityNotFoundException("Donor", unit.getDonorId());
            }
            if (unit.getBloodType() == null || unit.getStatus() == null
                    || unit.getDonationDate() == null || unit.getExpiryDate() == null) {
                throw new ValidationException("Incomplete blood unit: " + unit.getId(), "unit");
            }
            if (unit.getBloodType() != donor.getBloodType()) {
                throw new ValidationException(
                        "Unit blood type does not match donor: " + unit.getId(), "bloodType");
            }
            long shelfLife = ChronoUnit.DAYS.between(
                    unit.getDonationDate(), unit.getExpiryDate());
            if (unit.getDonationDate().isAfter(today) || shelfLife < 0
                    || shelfLife > DonationPolicy.UNIT_SHELF_LIFE_DAYS) {
                throw new ValidationException("Invalid unit dates: " + unit.getId(), "donationDate");
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
                throw new ValidationException(
                        "External donation duplicates a recorded unit: " + donor.getId(), "externalLastDonationDate");
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
                    throw new ValidationException(
                            "Donations are less than three months apart for donor: "
                                    + donor.getId(), "donationDate");
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
            if (request.getHospitalId() != null) {
                validateText(request.getHospitalId(), "Hospital ID");
            }
            if (requestsById.put(key(request.getId()), request) != null) {
                throw new DuplicateIdException("Request", request.getId());
            }
            if (request.getBloodType() == null || request.getStatus() == null
                    || request.getRequestDate() == null || request.getQuantity() <= 0
                    || request.getRequestDate().isAfter(today)) {
                throw new ValidationException(
                        "Invalid blood request: " + request.getId(), "request");
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
        Set<String> recordRequestIds = new HashSet<>();
        Map<String, String> unitToRequestStatus = new HashMap<>();
        for (FulfilmentRecord record : fulfilments) {
            validateText(record.requestId(), "Fulfilment request ID");
            BloodRequest request = requestsById.get(key(record.requestId()));
            if (request == null || request.getStatus() == RequestStatus.CANCELLED) {
                throw new ValidationException(
                        "Fulfilment references a missing or cancelled request: "
                                + record.requestId(), "fulfilment");
            }
            if (!recordRequestIds.add(key(record.requestId()))) {
                throw new ValidationException(
                        "Request has more than one fulfilment: " + record.requestId(), "fulfilment");
            }
            boolean partial = request.getStatus() == RequestStatus.PENDING;
            if (record.processedDate() == null || record.processedDate().isAfter(today)
                    || record.processedDate().isBefore(request.getRequestDate())
                    || (partial && record.unitIds().size() >= request.getQuantity())
                    || (!partial && record.unitIds().size() != request.getQuantity())) {
                throw new ValidationException(
                        "Invalid fulfilment details: " + record.requestId(), "fulfilment");
            }
            for (String unitId : record.unitIds()) {
                BloodUnit unit = unitsById.get(key(unitId));
                UnitStatus expected = partial ? UnitStatus.RESERVED : UnitStatus.USED;
                if (unit == null || unit.getStatus() != expected
                        || !request.getBloodType().canReceiveFrom(unit.getBloodType())
                        || record.processedDate().isBefore(unit.getDonationDate())
                        || record.processedDate().isAfter(unit.getExpiryDate())) {
                    throw new ValidationException(
                            "Fulfilment contains an invalid unit: " + unitId, "fulfilment");
                }
                String previous = unitToRequestStatus.put(key(unitId),
                        request.getStatus().name());
                if (previous != null) {
                    throw new ValidationException(
                            "Unit is used by more than one request: " + unitId, "fulfilment");
                }
            }
        }
        for (BloodRequest request : requests) {
            boolean hasRecord = recordRequestIds.contains(key(request.getId()));
            if (request.getStatus() == RequestStatus.FULFILLED && !hasRecord) {
                throw new ValidationException(
                        "Request status and fulfilment history disagree: "
                                + request.getId(), "state");
            }
            if (request.getStatus() == RequestStatus.CANCELLED && hasRecord) {
                throw new ValidationException(
                        "Request status and fulfilment history disagree: "
                                + request.getId(), "state");
            }
        }
        for (BloodUnit unit : units) {
            String recordStatus = unitToRequestStatus.get(key(unit.getId()));
            if (unit.getStatus() == UnitStatus.USED) {
                if (!"FULFILLED".equals(recordStatus)) {
                    throw new ValidationException(
                            "Unit status and fulfilment history disagree: " + unit.getId(), "state");
                }
            } else if (unit.getStatus() == UnitStatus.RESERVED) {
                if (!"PENDING".equals(recordStatus)) {
                    throw new ValidationException(
                            "Reserved unit is not committed to a pending request: "
                                    + unit.getId(), "state");
                }
            } else if (recordStatus != null) {
                throw new ValidationException(
                        "Unit status and fulfilment history disagree: " + unit.getId(), "state");
            }
        }
    }

    private static void validateAppointments(
            ArrayList<DonationAppointment> appointments,
            Map<String, Donor> donorsById,
            Map<String, BloodRequest> requestsById, LocalDate today) {
        Map<String, DonationAppointment> appointmentsById = new HashMap<>();
        Map<String, Integer> activeBookingsPerDonor = new HashMap<>();
        for (DonationAppointment appointment : appointments) {
            validateText(appointment.getId(), "Appointment ID");
            validateText(appointment.getDonorId(), "Appointment donor ID");
            validateText(appointment.getHospitalId(), "Appointment hospital ID");
            if (appointmentsById.put(key(appointment.getId()), appointment) != null) {
                throw new DuplicateIdException("Appointment", appointment.getId());
            }
            if (donorsById.get(key(appointment.getDonorId())) == null) {
                throw new EntityNotFoundException("Donor",
                        appointment.getDonorId());
            }
            if (appointment.getAppointmentDate() == null
                    || appointment.getStatus() == null) {
                throw new ValidationException(
                        "Incomplete appointment: " + appointment.getId(),
                        "appointment");
            }
            if (appointment.getLinkedRequestId() != null
                    && !appointment.getLinkedRequestId().isBlank()
                    && requestsById.get(key(appointment.getLinkedRequestId())) == null) {
                throw new ValidationException(
                        "Appointment references a missing request: "
                                + appointment.getId(), "appointment");
            }
            if (appointment.isBooked()
                    && appointment.getLinkedRequestId() != null
                    && !appointment.getLinkedRequestId().isBlank()) {
                BloodRequest linked = requestsById.get(
                        key(appointment.getLinkedRequestId()));
                if (linked == null
                        || linked.getStatus() != RequestStatus.PENDING) {
                    throw new ValidationException(
                            "A booked appointment can only be linked to a "
                                    + "pending request: " + appointment.getId(),
                            "appointment");
                }
            }
            if (appointment.isBooked()
                    && !appointment.getAppointmentDate().isBefore(today)) {
                activeBookingsPerDonor.merge(key(appointment.getDonorId()), 1,
                        Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> entry : activeBookingsPerDonor.entrySet()) {
            if (entry.getValue() > 1) {
                throw new ValidationException(
                        "Donor has more than one active appointment: "
                                + entry.getKey(), "appointment");
            }
        }
    }

    private static void validateText(String value, String field) {
        if (value == null || value.trim().isEmpty() || value.contains("|")
                || value.contains("\n") || value.contains("\r")) {
            throw new ValidationException(field + " contains invalid text.", field);
        }
    }

    private static String key(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
