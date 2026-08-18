package lifeflow.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodUnit;
import lifeflow.model.RequestStatus;
import lifeflow.model.UnitStatus;

/** Selects pending requests and performs exact blood-group matching. */
public class MatchingService {
    private final BloodInventory inventory;

    public MatchingService(BloodInventory inventory) {
        this.inventory = inventory;
    }

    public BloodRequest findNextPending(List<BloodRequest> requests) {
        BloodRequest next = null;
        for (BloodRequest request : requests) {
            if (request.getStatus() != RequestStatus.PENDING) {
                continue;
            }
            if (next == null
                    || request.getPriority() > next.getPriority()
                    || (request.getPriority() == next.getPriority()
                    && (request.getRequestDate().isBefore(next.getRequestDate())
                    || (request.getRequestDate().equals(next.getRequestDate())
                    && request.getId().compareToIgnoreCase(next.getId()) < 0)))) {
                next = request;
            }
        }
        return next;
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
            unit.setStatus(UnitStatus.USED);
            matched.add(unit);
        }
        request.setStatus(RequestStatus.FULFILLED);
        return matched;
    }
}
