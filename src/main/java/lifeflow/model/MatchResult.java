package lifeflow.model;

import java.util.ArrayList;
import java.util.List;

/** A clear, non-ambiguous result returned by the matching operation. */
public record MatchResult(MatchOutcome outcome, BloodRequest request,
                          List<BloodUnit> matchedUnits, int availableCount,
                          String message) {
    public MatchResult {
        matchedUnits = List.copyOf(new ArrayList<>(matchedUnits));
    }
}
