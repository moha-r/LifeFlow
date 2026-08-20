package lifeflow;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.ArrayList;
import lifeflow.model.AppointmentStatus;
import lifeflow.model.BloodType;
import lifeflow.model.Donor;
import lifeflow.model.DonationAppointment;
import lifeflow.model.LifeFlowState;
import lifeflow.model.exception.DuplicateIdException;
import lifeflow.model.exception.EntityNotFoundException;
import lifeflow.model.exception.ValidationException;
import lifeflow.service.DataValidator;
import org.junit.jupiter.api.Test;

final class AppointmentValidationTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    @Test
    void validStatePasses() {
        assertValid(state(booked("A000001", "D1", TODAY)));
    }

    @Test
    void duplicateIdsAreRejected() {
        assertThrows(DuplicateIdException.class, () -> validate(
                state(booked("A000001", "D1", TODAY),
                        booked("A000001", "D1", TODAY))));
    }

    @Test
    void missingDonorIsRejected() {
        assertThrows(EntityNotFoundException.class, () -> validate(
                state(booked("A000001", "MISSING", TODAY))));
    }

    @Test
    void incompleteAppointmentIsRejected() {
        ArrayList<DonationAppointment> appointments = new ArrayList<>();
        appointments.add(new DonationAppointment("A000001", "D1",
                "H1", null, null, AppointmentStatus.BOOKED));
        assertThrows(ValidationException.class, () -> validate(
                state(appointments.toArray(new DonationAppointment[0]))));
    }

    @Test
    void twoActiveBookingsForSameDonorAreRejected() {
        assertThrows(ValidationException.class, () -> validate(
                state(booked("A000001", "D1", TODAY),
                        booked("A000002", "D1", TODAY.plusDays(2)))));
    }

    @Test
    void activeBookingAndStaleBookingAreAllowed() {
        assertValid(state(booked("A000001", "D1", TODAY),
                booked("A000002", "D1", TODAY.minusDays(1))));
    }

    @Test
    void activeBookingAndCompletedAppointmentAreAllowed() {
        assertValid(state(booked("A000001", "D1", TODAY),
                new DonationAppointment("A000002", "D1", "H1",
                        TODAY.plusDays(2), null, AppointmentStatus.COMPLETED)));
    }

    @Test
    void missingLinkedRequestIsRejected() {
        assertThrows(ValidationException.class, () -> validate(
                state(booked("A000001", "D1", TODAY, "R-MISSING"))));
    }

    @Test
    void fulfilledLinkedRequestIsAllowedInStorage() {
        ArrayList<lifeflow.model.BloodRequest> requests = new ArrayList<>();
        requests.add(new lifeflow.model.RegularRequest("R1",
                "City Hospital", BloodType.O_POS, 1, TODAY,
                lifeflow.model.RequestStatus.CANCELLED));
        DonationAppointment cancelled = new DonationAppointment("A000001",
                "D1", "H1", TODAY, "R1", AppointmentStatus.CANCELLED);
        assertValid(state(requests, cancelled));
    }

    @Test
    void bookedAppointmentCannotLinkToNonPendingRequest() {
        ArrayList<lifeflow.model.BloodRequest> requests = new ArrayList<>();
        requests.add(new lifeflow.model.RegularRequest("R1",
                "City Hospital", BloodType.O_POS, 1, TODAY,
                lifeflow.model.RequestStatus.CANCELLED));
        assertThrows(ValidationException.class, () -> validate(
                state(requests, booked("A000001", "D1", TODAY, "R1"))));
    }

    private static DonationAppointment booked(String id, String donorId,
                                              LocalDate date) {
        return booked(id, donorId, date, null);
    }

    private static DonationAppointment booked(String id, String donorId,
                                              LocalDate date,
                                              String linkedRequestId) {
        return new DonationAppointment(id, donorId, "H1", date,
                linkedRequestId, AppointmentStatus.BOOKED);
    }

    private static LifeFlowState state(DonationAppointment... appointments) {
        return state(new ArrayList<>(), appointments);
    }

    private static LifeFlowState state(
            ArrayList<lifeflow.model.BloodRequest> requests,
            DonationAppointment... appointments) {
        ArrayList<Donor> donors = new ArrayList<>();
        donors.add(new Donor("D1", "Sara Ali", 25, 62, BloodType.O_POS, null));
        return new LifeFlowState(1, donors, new ArrayList<>(),
                requests, new ArrayList<>(),
                new ArrayList<>(java.util.Arrays.asList(appointments)),
                new ArrayList<>());
    }

    private static LifeFlowState state() {
        return state(new ArrayList<>());
    }

    private static void assertValid(LifeFlowState state) {
        validate(state);
    }

    private static void validate(LifeFlowState state) {
        DataValidator.validate(state, TODAY);
    }
}