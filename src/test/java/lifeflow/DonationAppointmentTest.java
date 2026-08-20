package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import lifeflow.model.AppointmentStatus;
import lifeflow.model.DonationAppointment;
import org.junit.jupiter.api.Test;

final class DonationAppointmentTest {
    private static final LocalDate DATE = LocalDate.of(2026, 8, 20);

    @Test
    void fieldsAreExposed() {
        DonationAppointment appointment = new DonationAppointment("A000001",
                "D1", "H1", DATE, "R1", AppointmentStatus.BOOKED);

        assertEquals("A000001", appointment.getId());
        assertEquals("D1", appointment.getDonorId());
        assertEquals("H1", appointment.getHospitalId());
        assertEquals(DATE, appointment.getAppointmentDate());
        assertEquals("R1", appointment.getLinkedRequestId());
        assertEquals(AppointmentStatus.BOOKED, appointment.getStatus());
        assertTrue(appointment.isBooked());
    }

    @Test
    void linkedRequestMayBeAbsent() {
        DonationAppointment appointment = new DonationAppointment("A000001",
                "D1", "H1", DATE, null, AppointmentStatus.BOOKED);
        assertEquals(null, appointment.getLinkedRequestId());
    }

    @Test
    void statusTransitions() {
        DonationAppointment appointment = new DonationAppointment("A000001",
                "D1", "H1", DATE, null, AppointmentStatus.BOOKED);
        appointment.markCompleted();
        assertEquals(AppointmentStatus.COMPLETED, appointment.getStatus());
        assertFalse(appointment.isBooked());

        DonationAppointment cancelled = new DonationAppointment("A000002",
                "D1", "H1", DATE, null, AppointmentStatus.BOOKED);
        cancelled.markCancelled();
        assertEquals(AppointmentStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void stalenessDependsOnToday() {
        DonationAppointment appointment = new DonationAppointment("A000001",
                "D1", "H1", DATE, null, AppointmentStatus.BOOKED);
        assertFalse(appointment.isStale(DATE));
        assertTrue(appointment.isStale(DATE.plusDays(1)));
        assertFalse(appointment.isStale(DATE.minusDays(1)));

        appointment.markCompleted();
        assertFalse(appointment.isStale(DATE.plusDays(5)));
    }

    @Test
    void equalityUsesIdIgnoreCase() {
        DonationAppointment first = new DonationAppointment("A000001",
                "D1", "H1", DATE, null, AppointmentStatus.BOOKED);
        DonationAppointment same = new DonationAppointment("a000001",
                "D2", "H2", DATE, null, AppointmentStatus.CANCELLED);
        DonationAppointment other = new DonationAppointment("A000002",
                "D1", "H1", DATE, null, AppointmentStatus.BOOKED);

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, other);
    }
}