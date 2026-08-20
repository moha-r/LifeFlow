package lifeflow.model;

import java.time.LocalDate;

/** An urgent blood request that is processed before regular requests. */
public class EmergencyRequest extends BloodRequest {
    public EmergencyRequest(String id, String requesterName, BloodType bloodType,
                            int quantity, LocalDate requestDate,
                            RequestStatus status) {
        super(id, requesterName, bloodType, quantity, requestDate, status, null);
    }

    public EmergencyRequest(String id, String requesterName, BloodType bloodType,
                            int quantity, LocalDate requestDate,
                            RequestStatus status, String hospitalId) {
        super(id, requesterName, bloodType, quantity, requestDate, status,
                hospitalId);
    }

    @Override
    public int getPriority() {
        return 2;
    }

    @Override
    public String getKind() {
        return "EMERGENCY";
    }
}
