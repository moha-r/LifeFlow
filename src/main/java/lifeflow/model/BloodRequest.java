package lifeflow.model;

import java.time.LocalDate;

/** Common state and behavior shared by all blood-request types. */
public abstract class BloodRequest {
    private String id;
    private String requesterName;
    private BloodType bloodType;
    private int quantity;
    private LocalDate requestDate;
    private RequestStatus status;

    protected BloodRequest(String id, String requesterName, BloodType bloodType,
                           int quantity, LocalDate requestDate,
                           RequestStatus status) {
        this.id = id;
        this.requesterName = requesterName;
        this.bloodType = bloodType;
        this.quantity = quantity;
        this.requestDate = requestDate;
        this.status = status;
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

    public void updatePendingDetails(String requesterName, BloodType bloodType,
                                     int quantity) {
        this.requesterName = requesterName;
        this.bloodType = bloodType;
        this.quantity = quantity;
    }
}
