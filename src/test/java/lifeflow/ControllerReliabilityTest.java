package lifeflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import lifeflow.model.BloodType;
import lifeflow.model.EligibilityReason;
import lifeflow.model.LifeFlowState;
import lifeflow.model.MatchOutcome;
import lifeflow.model.UnitStatus;
import lifeflow.persistence.LifeFlowStore;
import lifeflow.persistence.StorageInfo;
import lifeflow.service.LifeFlowController;
import org.junit.jupiter.api.Test;

final class ControllerReliabilityTest {
    private static final LocalDate TODAY = LocalDate.now();

    @Test
    void failedSaveLeavesThePublishedStateUnchanged() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = new LifeFlowController(new LifeFlowState(), store);
        controller.addDonor("D1", "Aisha", 25, 55.0, BloodType.A_POS, null);
        store.failNextSave = true;

        assertThrows(IOException.class,
                () -> controller.addDonor("D2", "Maya", 28, 60.0,
                        BloodType.O_NEG, null));
        assertEquals(1, controller.getDonors().size());
        assertEquals(1, store.state.getDonors().size());
    }

    @Test
    void unitCreationPersistsTheUnitAndDonationHistoryTogether() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = new LifeFlowController(new LifeFlowState(), store);
        controller.addDonor("D1", "Aisha", 25, 55.0, BloodType.A_NEG, null);
        LocalDate donation = TODAY.minusDays(1);

        controller.addBloodUnit("U1", "D1", donation, donation.plusDays(30));

        assertEquals(1, store.state.getUnits().size());
        assertEquals(donation, store.state.getDonors().get(0).getLastDonationDate());
        assertEquals(2, store.state.getRevision());
    }

    @Test
    void matchingUsesFefoAndPersistsTheAuditInOneSnapshot() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = new LifeFlowController(new LifeFlowState(), store);
        LocalDate donation = TODAY.minusDays(1);
        controller.addDonor("D1", "First", 25, 55.0, BloodType.O_NEG, null);
        controller.addBloodUnit("LATE", "D1", donation, TODAY.plusDays(30));
        controller.addDonor("D2", "Second", 25, 55.0, BloodType.O_NEG, null);
        controller.addBloodUnit("EARLY", "D2", donation, TODAY.plusDays(5));
        controller.addRequest("R1", "Ward", BloodType.O_NEG, 1, true);

        var result = controller.processNextRequest(TODAY);

        assertEquals(MatchOutcome.FULFILLED, result.outcome());
        assertEquals(List.of("EARLY"), result.matchedUnits().stream()
                .map(unit -> unit.getId()).toList());
        assertEquals(List.of("EARLY"),
                store.state.getFulfilments().get(0).unitIds());
        assertEquals(UnitStatus.AVAILABLE, store.state.getUnits().stream()
                .filter(unit -> unit.getId().equals("LATE")).findFirst().orElseThrow()
                .getStatus());
    }

    @Test
    void insufficientStockDoesNotSaveOrChangeRevision() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = new LifeFlowController(new LifeFlowState(), store);
        controller.addRequest("R1", "Ward", BloodType.B_POS, 2, false);
        long revision = store.state.getRevision();
        int saves = store.saveCount;

        var result = controller.processNextRequest(TODAY);

        assertEquals(MatchOutcome.INSUFFICIENT_STOCK, result.outcome());
        assertEquals(revision, controller.getRevision());
        assertEquals(saves, store.saveCount);
    }

    @Test
    void failedMatchingSaveRollsBackUnitsRequestAndAuditTogether() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = new LifeFlowController(new LifeFlowState(), store);
        LocalDate donation = TODAY.minusDays(1);
        controller.addDonor("D1", "Aisha", 25, 55.0, BloodType.A_POS, null);
        controller.addBloodUnit("U1", "D1", donation, TODAY.plusDays(20));
        controller.addRequest("R1", "Ward", BloodType.A_POS, 1, true);
        long revision = controller.getRevision();
        store.failNextSave = true;

        assertThrows(IOException.class, () -> controller.processNextRequest(TODAY));

        assertEquals(revision, controller.getRevision());
        assertEquals(UnitStatus.AVAILABLE, controller.getUnits().get(0).getStatus());
        assertEquals(lifeflow.model.RequestStatus.PENDING,
                controller.getRequests().get(0).getStatus());
        assertTrue(controller.getFulfilments().isEmpty());
    }

    @Test
    void futureDonationDateIsRejectedWithoutSaving() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = new LifeFlowController(new LifeFlowState(), store);
        controller.addDonor("D1", "Aisha", 25, 55.0, BloodType.A_POS, null);
        int saves = store.saveCount;

        assertThrows(IllegalArgumentException.class,
                () -> controller.addBloodUnit("U1", "D1",
                        LocalDate.now().plusDays(1), LocalDate.now().plusDays(20)));
        assertEquals(saves, store.saveCount);
        assertTrue(controller.getUnits().isEmpty());
    }

    @Test
    void matchingCannotBeBackdatedBeforeTheRequest() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = new LifeFlowController(new LifeFlowState(), store);
        controller.addRequest("R1", "Ward", BloodType.A_POS, 1, false);
        int saves = store.saveCount;

        assertThrows(IllegalArgumentException.class,
                () -> controller.processNextRequest(LocalDate.now().minusDays(1)));
        assertEquals(saves, store.saveCount);
    }

    @Test
    void nonFiniteWeightIsRejected() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = new LifeFlowController(new LifeFlowState(), store);

        assertThrows(IllegalArgumentException.class,
                () -> controller.addDonor("D1", "Aisha", 25,
                        Double.NaN, BloodType.A_POS, null));
        assertThrows(IllegalArgumentException.class,
                () -> controller.addDonor("D2", "Maya", 25,
                        Double.POSITIVE_INFINITY, BloodType.A_POS, null));
    }

    @Test
    void controllerReturnsTheDetailedEligibilityReason() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = new LifeFlowController(new LifeFlowState(), store);
        controller.addDonor("D1", "Aisha", 25, 55.0, BloodType.A_POS,
                TODAY.minusDays(12));

        var result = controller.checkDonorEligibility("D1", TODAY);

        assertFalse(result.eligible());
        assertEquals(EligibilityReason.WAITING_PERIOD, result.reason());
    }

    @Test
    void expiredUnitsCannotBeEdited() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = new LifeFlowController(new LifeFlowState(), store);
        LocalDate donation = TODAY.minusDays(10);
        controller.addDonor("D1", "Aisha", 25, 55.0, BloodType.A_POS, null);
        controller.addBloodUnit("U1", "D1", donation, TODAY.minusDays(1));

        assertThrows(IllegalArgumentException.class,
                () -> controller.updateBloodUnitExpiry("U1", TODAY.plusDays(5)));
    }

    @Test
    void generatesTheNextUnitAndRequestIdsFromSavedState() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = new LifeFlowController(new LifeFlowState(), store);
        assertEquals("U001", controller.getNextUnitId());
        assertEquals("R001", controller.getNextRequestId());

        LocalDate donation = TODAY.minusDays(1);
        controller.addDonor("D1", "Aisha", 25, 55.0, BloodType.A_POS, null);
        controller.addBloodUnit("u009", "D1", donation, TODAY.plusDays(20));
        controller.addRequest("r012", "Ward", BloodType.A_POS, 1, false);

        assertEquals("U010", controller.getNextUnitId());
        assertEquals("R013", controller.getNextRequestId());
    }

    private static final class RecordingStore implements LifeFlowStore {
        private LifeFlowState state = new LifeFlowState();
        private int saveCount;
        private boolean failNextSave;

        @Override
        public LifeFlowState load() {
            return state.copy();
        }

        @Override
        public void save(LifeFlowState candidate) throws IOException {
            if (failNextSave) {
                failNextSave = false;
                throw new IOException("intentional failure");
            }
            state = candidate.copy();
            saveCount++;
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
