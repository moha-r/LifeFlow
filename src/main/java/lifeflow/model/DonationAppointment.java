package lifeflow.model;

import java.time.LocalDate;
import java.util.Objects;

/** A scheduled donation by a donor at a specific hospital. */
public final class DonationAppointment implements Identifiable {
    private final String id;
    private final String donorId;
    private final String hospitalId;
    private final LocalDate appointmentDate;
    private final String linkedRequestId;
    private AppointmentStatus status;

    public DonationAppointment(String id, String donorId, String hospitalId,
                               LocalDate appointmentDate, String linkedRequestId,
                               AppointmentStatus status) {
        this.id = id;
        this.donorId = donorId;
        this.hospitalId = hospitalId;
        this.appointmentDate = appointmentDate;
        this.linkedRequestId = linkedRequestId;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getDonorId() {
        return donorId;
    }

    public String getHospitalId() {
        return hospitalId;
    }

    public LocalDate getAppointmentDate() {
        return appointmentDate;
    }

    public String getLinkedRequestId() {
        return linkedRequestId;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public boolean isBooked() {
        return status == AppointmentStatus.BOOKED;
    }

    /** A booked appointment whose date has passed without being completed. */
    public boolean isStale(LocalDate today) {
        return isBooked() && appointmentDate.isBefore(today);
    }

    public void markCompleted() {
        status = AppointmentStatus.COMPLETED;
    }

    public void markCancelled() {
        status = AppointmentStatus.CANCELLED;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DonationAppointment appointment)) {
            return false;
        }
        return id.equalsIgnoreCase(appointment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id.toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public String toString() {
        return "DonationAppointment{" + id + ", donor=" + donorId
                + ", hospital=" + hospitalId + ", date=" + appointmentDate
                + ", status=" + status + '}';
    }
}