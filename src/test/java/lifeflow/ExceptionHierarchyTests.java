package lifeflow;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lifeflow.model.BloodType;
import lifeflow.model.EligibilityResult;
import lifeflow.model.LifeFlowState;
import lifeflow.model.exception.DuplicateIdException;
import lifeflow.model.exception.EligibilityException;
import lifeflow.model.exception.EntityNotFoundException;
import lifeflow.model.exception.ImmutableRecordException;
import lifeflow.model.exception.InsufficientStockException;
import lifeflow.model.exception.LifeFlowException;
import lifeflow.model.exception.ValidationException;
import lifeflow.persistence.JsonLifeFlowStore;
import lifeflow.service.LifeFlowController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the custom exception hierarchy: inheritance, polymorphism,
 * and correct exception types thrown by the controller.
 */
class ExceptionHierarchyTests {

    @TempDir Path tempDir;
    private LifeFlowController controller;

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault());

    @BeforeEach
    void setUp() throws IOException {
        JsonLifeFlowStore store = new JsonLifeFlowStore(tempDir, FIXED_CLOCK);
        controller = new LifeFlowController(new LifeFlowState(), store, FIXED_CLOCK);
    }

    // ── Hierarchy structure ──────────────────────────────────────

    @Test
    void allExceptionsExtendLifeFlowException() {
        assertInstanceOf(LifeFlowException.class, new DuplicateIdException("X", "1", new java.util.ArrayList<>()));
        assertInstanceOf(LifeFlowException.class, new EntityNotFoundException("X", "1"));
        assertInstanceOf(LifeFlowException.class, new ValidationException("msg", "f"));
        assertInstanceOf(LifeFlowException.class,
                new ImmutableRecordException("X", "1", "reason"));
        assertInstanceOf(LifeFlowException.class,
                new InsufficientStockException("R1", BloodType.A_POS, 5, 2));

        EligibilityResult result = new EligibilityResult(false,
                lifeflow.model.EligibilityReason.AGE_OUT_OF_RANGE,
                null, null, "Too young");
        assertInstanceOf(LifeFlowException.class, new EligibilityException(result));
    }

    @Test
    void lifeFlowExceptionExtendsRuntimeException() {
        LifeFlowException ex = new DuplicateIdException("Donor", "D001");
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void catchLifeFlowExceptionCatchesAllSubtypes() {
        // Polymorphic catch — single catch block handles any domain error
        LifeFlowException[] exceptions = {
            new DuplicateIdException("Donor", "D001"),
            new EntityNotFoundException("Unit", "U001"),
            new ValidationException("Bad input", "name"),
            new ImmutableRecordException("Request", "R001", "fulfilled"),
            new InsufficientStockException("R002", BloodType.O_NEG, 3, 1)
        };
        for (LifeFlowException ex : exceptions) {
            try {
                throw ex;
            } catch (LifeFlowException caught) {
                assertNotNull(caught.getMessage());
            }
        }
    }

    // ── Exception data accessors ─────────────────────────────────

    @Test
    void duplicateIdExceptionCarriesEntityId() {
        DuplicateIdException ex = new DuplicateIdException("Donor", "D001");
        assertEquals("D001", ex.getEntityId());
        assertTrue(ex.getMessage().contains("D001"));
        assertTrue(ex.getMessage().contains("Donor"));
    }

    @Test
    void entityNotFoundExceptionCarriesTypeAndId() {
        EntityNotFoundException ex = new EntityNotFoundException("Blood unit", "U099");
        assertEquals("U099", ex.getEntityId());
        assertEquals("Blood unit", ex.getEntityType());
        assertTrue(ex.getMessage().contains("U099"));
    }

    @Test
    void validationExceptionCarriesFieldName() {
        ValidationException ex = new ValidationException("Age out of range", "age");
        assertEquals("age", ex.getFieldName());
        assertNull(ex.getEntityId());
    }

    @Test
    void immutableRecordExceptionCarriesIdAndReason() {
        ImmutableRecordException ex = new ImmutableRecordException(
                "Request", "R001", "fulfilled requests are read-only.");
        assertEquals("R001", ex.getEntityId());
        assertTrue(ex.getMessage().contains("Cannot modify"));
        assertTrue(ex.getMessage().contains("R001"));
    }

    @Test
    void insufficientStockExceptionCarriesDetails() {
        InsufficientStockException ex = new InsufficientStockException(
                "R003", BloodType.B_NEG, 5, 2);
        assertEquals("R003", ex.getEntityId());
        assertEquals(BloodType.B_NEG, ex.getBloodType());
        assertEquals(5, ex.getRequested());
        assertEquals(2, ex.getAvailable());
    }

    @Test
    void eligibilityExceptionCarriesResult() {
        EligibilityResult result = new EligibilityResult(false,
                lifeflow.model.EligibilityReason.UNDERWEIGHT,
                null, null, "Donor weight must be at least 45 kg.");
        EligibilityException ex = new EligibilityException(result);
        assertSame(result, ex.getResult());
        assertEquals("Donor weight must be at least 45 kg.", ex.getMessage());
    }

    // ── Controller throws correct exception types ────────────────

    @Test
    void addDuplicateDonorThrowsDuplicateIdException() throws IOException {
        controller.addDonor("D000001", "Aisha", 25, 60, BloodType.O_NEG, null);
        assertThrows(DuplicateIdException.class, () ->
                controller.addDonor("D000001", "Other", 30, 70, BloodType.A_POS, null));
    }

    @Test
    void updateMissingDonorThrowsEntityNotFoundException() {
        assertThrows(EntityNotFoundException.class, () ->
                controller.updateDonor("D999", "X", 25, 60, BloodType.A_POS, null));
    }

    @Test
    void addDonorWithInvalidAgeThrowsValidationException() {
        assertThrows(ValidationException.class, () ->
                controller.addDonor("D000001", "Test", -1, 60, BloodType.A_POS, null));
    }

    @Test
    void addDonorWithMissingNameThrowsValidationException() {
        assertThrows(ValidationException.class, () ->
                controller.addDonor("D000001", "", 25, 60, BloodType.A_POS, null));
    }

    @Test
    void addDuplicateUnitThrowsDuplicateIdException() throws IOException {
        controller.addDonor("D000001", "Aisha", 25, 60, BloodType.O_NEG, null);
        controller.addBloodUnit("U000001", "D000001", TODAY);
        assertThrows(DuplicateIdException.class, () ->
                controller.addBloodUnit("U000001", "D000001",
                        TODAY.plusMonths(4)));
    }

    @Test
    void addUnitForMissingDonorThrowsEntityNotFoundException() {
        assertThrows(EntityNotFoundException.class, () ->
                controller.addBloodUnit("U000001", "D999", TODAY));
    }

    @Test
    void addUnitForIneligibleDonorThrowsEligibilityException() throws IOException {
        controller.addDonor("D000001", "Young", 15, 60, BloodType.A_POS, null);
        assertThrows(EligibilityException.class, () ->
                controller.addBloodUnit("U000001", "D000001", TODAY));
    }

    @Test
    void editUsedUnitThrowsImmutableRecordException() throws IOException {
        controller.addDonor("D000001", "Aisha", 25, 60, BloodType.O_NEG, null);
        controller.addBloodUnit("U000001", "D000001", TODAY);
        controller.addRequest("R000001", "Hospital", BloodType.O_NEG, 1, true);
        controller.processNextRequest(TODAY);
        assertThrows(ImmutableRecordException.class, () ->
                controller.updateUnusedBloodUnitDonationDate("U000001",
                        TODAY.minusDays(1)));
    }

    @Test
    void editFulfilledRequestThrowsImmutableRecordException() throws IOException {
        controller.addDonor("D000001", "Aisha", 25, 60, BloodType.O_NEG, null);
        controller.addBloodUnit("U000001", "D000001", TODAY);
        controller.addRequest("R000001", "Hospital", BloodType.O_NEG, 1, false);
        controller.processNextRequest(TODAY);
        assertThrows(ImmutableRecordException.class, () ->
                controller.updatePendingRequest("R000001", "X", BloodType.A_POS, 1));
    }

    @Test
    void addDuplicateRequestThrowsDuplicateIdException() throws IOException {
        controller.addRequest("R000001", "Clinic", BloodType.A_POS, 1, false);
        assertThrows(DuplicateIdException.class, () ->
                controller.addRequest("R000001", "Other", BloodType.B_POS, 2, true));
    }

    @Test
    void changeDonorBloodTypeWithUnitsThrowsImmutableRecordException()
            throws IOException {
        controller.addDonor("D000001", "Aisha", 25, 60, BloodType.O_NEG, null);
        controller.addBloodUnit("U000001", "D000001", TODAY);
        assertThrows(ImmutableRecordException.class, () ->
                controller.updateDonor("D000001", "Aisha", 25, 60,
                        BloodType.A_POS, null));
    }
}
