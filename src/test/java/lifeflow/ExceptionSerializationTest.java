package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDate;
import lifeflow.model.EligibilityReason;
import lifeflow.model.EligibilityResult;
import lifeflow.model.exception.EligibilityException;
import org.junit.jupiter.api.Test;

class ExceptionSerializationTest {

    @Test
    void eligibilityExceptionPreservesItsResultWhenSerialized() throws Exception {
        EligibilityResult result = new EligibilityResult(false,
                EligibilityReason.WAITING_PERIOD,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 10, 1),
                "The donor must complete the waiting period.");
        EligibilityException original = new EligibilityException(result);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        EligibilityException restored;
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (EligibilityException) input.readObject();
        }

        assertEquals(result, restored.getResult());
        assertEquals(original.getMessage(), restored.getMessage());
    }
}
