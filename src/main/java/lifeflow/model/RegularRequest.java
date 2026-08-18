package lifeflow.model;

import java.time.LocalDate;

/** A normal blood request with the base simulation priority. */
public class RegularRequest extends BloodRequest {
    public RegularRequest(String id, String requesterName, BloodType bloodType,
                          int quantity, LocalDate requestDate,
                          RequestStatus status) {
        super(id, requesterName, bloodType, quantity, requestDate, status);
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public String getKind() {
        return "REGULAR";
    }
}
