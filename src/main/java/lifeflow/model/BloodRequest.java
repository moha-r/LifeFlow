package lifeflow.model;

import java.time.LocalDate;
import java.util.Locale;

/** Common state and behavior shared by all blood-request types. */
public abstract class BloodRequest implements Identifiable {
    private String id;
    private String requesterName;
    private String hospitalId;
    private BloodType bloodType;
    private int quantity;
    private LocalDate requestDate;
    private RequestStatus status;

    protected BloodRequest(String id, String requesterName, BloodType bloodType,
                           int quantity, LocalDate requestDate,
                           RequestStatus status) {
        this(id, requesterName, bloodType, quantity, requestDate, status, null);
    }

    protected BloodRequest(String id, String requesterName, BloodType bloodType,
                           int quantity, LocalDate requestDate,
                           RequestStatus status, String hospitalId) {
        this.id = id;
        this.requesterName = requesterName;
        this.bloodType = bloodType;
        this.quantity = quantity;
        this.requestDate = requestDate;
        this.status = status;
        this.hospitalId = hospitalId;
    }

    /** Returns a higher number for requests that should be processed first. */
    public abstract int getPriority();

    public abstract String getKind();

    public String getId() {
        return id;
    }

    public String getRequesterName() {
        return requesterName;
    }

    /** Returns the hospital that placed this request, or null for admin-created ones. */
    public String getHospitalId() {
        return hospitalId;
    }

    public BloodType getBloodType() {
        return bloodType;
    }

    public int getQuantity() {
        return quantity;
    }

    public LocalDate getRequestDate() {
        return requestDate;
    }

    public RequestStatus getStatus() {
        return status;
    }

    /** Marks this request as completely fulfilled by matched units. */
    public void markFulfilled() {
        status = RequestStatus.FULFILLED;
    }

    /** Marks this request as cancelled before it could be fulfilled. */
    public void markCancelled() {
        status = RequestStatus.CANCELLED;
    }

    public void updatePendingDetails(String requesterName, BloodType bloodType,
                                     int quantity) {
        this.requesterName = requesterName;
        this.bloodType = bloodType;
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return String.format("%s{id='%s', requester='%s', bloodType=%s, "
                + "quantity=%d, status=%s}", getKind(), id, requesterName,
                bloodType, quantity, status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BloodRequest other)) return false;
        return id.equalsIgnoreCase(other.id);
    }

    @Override
    public int hashCode() {
        return id.toLowerCase(Locale.ROOT).hashCode();
    }
}
