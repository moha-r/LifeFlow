package lifeflow.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodUnit;
import lifeflow.model.RequestStatus;

/** Selects pending requests and performs exact blood-group matching. */
public class MatchingService {
    /** Highest priority first, then oldest request date, then case-insensitive ID. */
    public static final Comparator<BloodRequest> ORDER =
            Comparator.comparingInt(BloodRequest::getPriority).reversed()
                    .thenComparing(BloodRequest::getRequestDate)
                    .thenComparing(BloodRequest::getId, String.CASE_INSENSITIVE_ORDER);

    private final BloodInventory inventory;

    public MatchingService(BloodInventory inventory) {
        this.inventory = inventory;
    }

    public BloodRequest findNextPending(List<BloodRequest> requests) {
        return orderedPending(requests).stream().findFirst().orElse(null);
    }

    /** Returns the highest-priority request that has its full exact-match stock. */
    public BloodRequest findNextFulfillable(List<BloodRequest> requests,
                                            LocalDate date) {
        for (BloodRequest request : orderedPending(requests)) {
            if (inventory.getAvailableUnits(request.getBloodType(), date).size()
                    >= request.getQuantity()) {
                return request;
            }
        }
        return null;
    }

    public ArrayList<BloodUnit> match(BloodRequest request, LocalDate date) {
        ArrayList<BloodUnit> available = inventory.getAvailableUnits(
                request.getBloodType(), date);
        if (available.size() < request.getQuantity()) {
            return new ArrayList<>();
        }

        ArrayList<BloodUnit> matched = new ArrayList<>();
        for (int index = 0; index < request.getQuantity(); index++) {
            BloodUnit unit = available.get(index);
            unit.markUsed();
            matched.add(unit);
        }
        request.markFulfilled();
        return matched;
    }

    private static List<BloodRequest> orderedPending(List<BloodRequest> requests) {
        return requests.stream()
                .filter(request -> request.getStatus() == RequestStatus.PENDING)
                .sorted(ORDER)
                .toList();
    }
}
