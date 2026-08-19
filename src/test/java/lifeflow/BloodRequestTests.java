package lifeflow;

import java.time.LocalDate;
import java.util.ArrayList;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.EmergencyRequest;
import lifeflow.model.RegularRequest;
import lifeflow.model.RequestStatus;

final class BloodRequestTests {
    private BloodRequestTests() {
    }

    static void run() {
        demonstratesRuntimePolymorphism();
        exposesRequestState();
    }

    private static void demonstratesRuntimePolymorphism() {
        ArrayList<BloodRequest> requests = new ArrayList<>();
        requests.add(new RegularRequest("R001", "Clinic A", BloodType.O_POS,
                1, LocalDate.of(2026, 8, 1), RequestStatus.PENDING));
        requests.add(new EmergencyRequest("R002", "Hospital B", BloodType.O_POS,
                1, LocalDate.of(2026, 8, 2), RequestStatus.PENDING));

        assert requests.get(0).getPriority() == 1;
        assert requests.get(1).getPriority() == 2;
        assert requests.get(1) instanceof EmergencyRequest;
    }

    private static void exposesRequestState() {
        LocalDate date = LocalDate.of(2026, 8, 3);
        BloodRequest request = new RegularRequest("R010", "Hospital C",
                BloodType.AB_POS, 2, date, RequestStatus.PENDING);

        request.markFulfilled();

        assert request.getId().equals("R010");
        assert request.getRequesterName().equals("Hospital C");
        assert request.getBloodType() == BloodType.AB_POS;
        assert request.getQuantity() == 2;
        assert request.getRequestDate().equals(date);
        assert request.getStatus() == RequestStatus.FULFILLED;
        assert request.getKind().equals("REGULAR");
    }
}
