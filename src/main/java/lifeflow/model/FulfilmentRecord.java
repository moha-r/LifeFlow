package lifeflow.model;

import java.time.LocalDate;
import java.util.List;

/** Records exactly which units fulfilled one completed blood request. */
public record FulfilmentRecord(String requestId, LocalDate processedDate,
                               List<String> unitIds) {
    public FulfilmentRecord {
        unitIds = List.copyOf(unitIds);
    }
}
