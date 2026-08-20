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
    private final lifeflow.model.MatchMode matchMode;

    public MatchingService(BloodInventory inventory) {
        this(inventory, lifeflow.model.MatchMode.EXACT);
    }

    public MatchingService(BloodInventory inventory, lifeflow.model.MatchMode matchMode) {
        this.inventory = inventory;
        this.matchMode = matchMode != null ? matchMode : lifeflow.model.MatchMode.EXACT;
    }

    public BloodRequest findNextPending(List<BloodRequest> requests) {
        return orderedPending(requests).stream().findFirst().orElse(null);
    }

    /** Returns the highest-priority request that has its full stock. */
    public BloodRequest findNextFulfillable(List<BloodRequest> requests,
                                            LocalDate date) {
        return findNextFulfillable(requests, date, new java.util.HashMap<>());
    }

    /** Same as {@link #findNextFulfillable(List, LocalDate)} but counts units
     * already reserved for each request toward its quantity. */
    public BloodRequest findNextFulfillable(List<BloodRequest> requests,
                                            LocalDate date,
                                            java.util.Map<String, Integer> committedByRequest) {
        for (BloodRequest request : orderedPending(requests)) {
            int committed = committedByRequest.getOrDefault(
                    request.getId().toLowerCase(java.util.Locale.ROOT), 0);
            if (inventory.getCompatibleUnits(request.getBloodType(), matchMode, date).size()
                    + committed >= request.getQuantity()) {
                return request;
            }
        }
        return null;
    }

    public ArrayList<BloodUnit> match(BloodRequest request, LocalDate date) {
        return match(request, date, request.getQuantity());
    }

    /** Matches exactly {@code count} available units; the request is marked
     * fulfilled only when {@code count} reaches its full quantity. */
    public ArrayList<BloodUnit> match(BloodRequest request, LocalDate date,
                                      int count) {
        ArrayList<BloodUnit> available = inventory.getCompatibleUnits(
                request.getBloodType(), matchMode, date);
        if (available.size() < count) {
            return new ArrayList<>();
        }

        ArrayList<BloodUnit> matched = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            BloodUnit unit = available.get(index);
            unit.markUsed();
            matched.add(unit);
        }
        if (count >= request.getQuantity()) {
            request.markFulfilled();
        }
        return matched;
    }

    private static List<BloodRequest> orderedPending(List<BloodRequest> requests) {
        return requests.stream()
                .filter(request -> request.getStatus() == RequestStatus.PENDING)
                .sorted(ORDER)
                .toList();
    }
}
