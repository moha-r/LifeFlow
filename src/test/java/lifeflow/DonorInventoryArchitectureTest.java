package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.InventoryState;
import lifeflow.model.LifeFlowState;
import lifeflow.model.UnitStatus;
import lifeflow.model.exception.EligibilityException;
import lifeflow.model.exception.ValidationException;
import lifeflow.persistence.LifeFlowStore;
import lifeflow.persistence.StorageInfo;
import lifeflow.service.DataValidator;
import lifeflow.service.LifeFlowController;
import org.junit.jupiter.api.Test;

final class DonorInventoryArchitectureTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-19T04:00:00Z"), ZoneOffset.UTC);

    @Test
    void firstTimeDonationCreatesOneUnitWithoutDuplicatingDateInDonor() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = controller(store);
        controller.addDonor("D000001", "Aisha", 25, 55.0, BloodType.A_POS, null);

        controller.addBloodUnit("U000001", "D000001", TODAY);

        Donor savedDonor = store.state.getDonors().get(0);
        BloodUnit savedUnit = store.state.getUnits().get(0);
        assertNull(savedDonor.getExternalLastDonationDate());
        assertEquals(TODAY, controller.getEffectiveLastDonationDate("D000001"));
        assertEquals(TODAY.plusDays(35), savedUnit.getExpiryDate());
    }

    @Test
    void secondDonationInsideThreeMonthsIsRejectedWithoutRevisionChange() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = controller(store);
        controller.addDonor("D000001", "Aisha", 25, 55.0, BloodType.A_POS, null);
        controller.addBloodUnit("U000001", "D000001", TODAY.minusMonths(1));
        long revision = controller.getRevision();

        assertThrows(EligibilityException.class,
                () -> controller.addBloodUnit("U000002", "D000001", TODAY));
        assertEquals(revision, controller.getRevision());
        assertEquals(1, controller.getUnits().size());
    }

    @Test
    void correctingUnusedUnitRecalculatesExpiryAndAllowsExpiredUnitCorrection()
            throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = controller(store);
        controller.addDonor("D000001", "Aisha", 25, 55.0, BloodType.A_POS, null);
        controller.addBloodUnit("U000001", "D000001", TODAY.minusDays(50));

        controller.updateUnusedBloodUnitDonationDate("U000001", TODAY.minusDays(40));

        BloodUnit corrected = controller.getUnits().get(0);
        assertEquals(TODAY.minusDays(40), corrected.getDonationDate());
        assertEquals(TODAY.minusDays(5), corrected.getExpiryDate());
        assertEquals(InventoryState.EXPIRED, corrected.getInventoryState(TODAY));
    }

    @Test
    void derivedInventoryStateDistinguishesScheduledAvailableExpiredAndUsed() {
        BloodUnit scheduled = unit("U1", TODAY.plusDays(1), UnitStatus.AVAILABLE);
        BloodUnit available = unit("U2", TODAY.minusDays(35), UnitStatus.AVAILABLE);
        BloodUnit expired = unit("U3", TODAY.minusDays(36), UnitStatus.AVAILABLE);
        BloodUnit used = unit("U4", TODAY.minusDays(1), UnitStatus.USED);

        assertEquals(InventoryState.SCHEDULED, scheduled.getInventoryState(TODAY));
        assertEquals(InventoryState.AVAILABLE, available.getInventoryState(TODAY));
        assertEquals(InventoryState.EXPIRED, expired.getInventoryState(TODAY));
        assertEquals(InventoryState.USED, used.getInventoryState(TODAY));
    }

    @Test
    void validatorRejectsDuplicateExternalAndInternalDonationEvents() {
        Donor donor = new Donor("D000001", "Aisha", 25, 55.0,
                BloodType.A_POS, TODAY.minusMonths(4));
        BloodUnit duplicate = new BloodUnit("U000001", donor.getId(),
                donor.getBloodType(), TODAY.minusMonths(4),
                TODAY.minusMonths(4).plusDays(35), UnitStatus.AVAILABLE);
        LifeFlowState invalid = new LifeFlowState(0,
                new ArrayList<>(java.util.List.of(donor)),
                new ArrayList<>(java.util.List.of(duplicate)), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>());

        assertThrows(ValidationException.class,
                () -> DataValidator.validate(invalid, TODAY));
    }

    @Test
    void generatedIdsSupportLegacyValuesAndIgnoreCustomIds() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = controller(store);
        controller.addDonor("D009", "Aisha", 25, 55.0, BloodType.A_POS, null);
        controller.addDonor("DONOR-ALPHA", "Maya", 28, 60.0,
                BloodType.O_NEG, null);

        assertEquals("D000010", controller.getNextDonorId());
    }

    @Test
    void generatedIdsUseSixDigitsAndExpandWithoutAnUpperLimit() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = controller(store);
        assertEquals("D000001", controller.getNextDonorId());
        assertEquals("U000001", controller.getNextUnitId());
        assertEquals("R000001", controller.getNextRequestId());

        controller.addDonor("D999999", "Aisha", 25, 55.0, BloodType.A_POS, null);
        assertEquals("D1000000", controller.getNextDonorId());
    }

    private static LifeFlowController controller(RecordingStore store) {
        return new LifeFlowController(new LifeFlowState(), store, CLOCK);
    }

    private static BloodUnit unit(String id, LocalDate donation, UnitStatus status) {
        return new BloodUnit(id, "D000001", BloodType.A_POS, donation,
                donation.plusDays(35), status);
    }

    private static final class RecordingStore implements LifeFlowStore {
        private LifeFlowState state = new LifeFlowState();

        @Override
        public LifeFlowState load() {
            return state.copy();
        }

        @Override
        public void save(LifeFlowState candidate) throws IOException {
            state = candidate.copy();
        }

        @Override
        public StorageInfo getStorageInfo() {
            return new StorageInfo(Path.of("test.json"), true, false, "ready");
        }

        @Override
        public void close() {
        }
    }
}
