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

        controller.addBloodUnit("U1", "D1", donation);

        assertEquals(1, store.state.getUnits().size());
        assertEquals(null,
                store.state.getDonors().get(0).getExternalLastDonationDate());
        assertEquals(donation, controller.getEffectiveLastDonationDate("D1"));
        assertEquals(2, store.state.getRevision());
    }

    @Test
    void matchingUsesFefoAndPersistsTheAuditInOneSnapshot() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = new LifeFlowController(new LifeFlowState(), store);
        controller.addDonor("D1", "First", 25, 55.0, BloodType.O_NEG, null);
        controller.addBloodUnit("LATE", "D1", TODAY.minusDays(1));
        controller.addDonor("D2", "Second", 25, 55.0, BloodType.O_NEG, null);
        controller.addBloodUnit("EARLY", "D2", TODAY.minusDays(10));
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
    void matchingSkipsAnUnavailableBloodTypeAndFulfilsTheNextCompatibleRequest()
            throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = new LifeFlowController(new LifeFlowState(), store);
        controller.addDonor("D1", "Aisha", 25, 55.0, BloodType.O_NEG, null);
        controller.addBloodUnit("U1", "D1", TODAY.minusDays(1));
        controller.addRequest("R000001", "First ward", BloodType.O_POS, 1, false);
        controller.addRequest("R000002", "Second ward", BloodType.O_NEG, 1, false);

        var result = controller.processNextRequest(TODAY);

        assertEquals(MatchOutcome.FULFILLED, result.outcome());
        assertEquals("R000002", result.request().getId());
        assertEquals(lifeflow.model.RequestStatus.PENDING,
                controller.getRequests().get(0).getStatus());
        assertEquals(lifeflow.model.RequestStatus.FULFILLED,
                controller.getRequests().get(1).getStatus());
        assertEquals(UnitStatus.USED, controller.getUnits().get(0).getStatus());
    }

    @Test
    void unavailableEmergencyDoesNotBlockACompatibleRegularRequest()
            throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = new LifeFlowController(new LifeFlowState(), store);
        controller.addDonor("D1", "Aisha", 25, 55.0, BloodType.A_POS, null);
        controller.addBloodUnit("U1", "D1", TODAY.minusDays(1));
        controller.addRequest("R000001", "Emergency ward", BloodType.O_POS,
                1, true);
        controller.addRequest("R000002", "Regular ward", BloodType.A_POS,
                1, false);

        var result = controller.processNextRequest(TODAY);

        assertEquals(MatchOutcome.FULFILLED, result.outcome());
        assertEquals("R000002", result.request().getId());
        assertEquals(lifeflow.model.RequestStatus.PENDING,
                controller.getRequests().get(0).getStatus());
        assertEquals(lifeflow.model.RequestStatus.FULFILLED,
                controller.getRequests().get(1).getStatus());
    }

    @Test
    void failedMatchingSaveRollsBackUnitsRequestAndAuditTogether() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = new LifeFlowController(new LifeFlowState(), store);
        LocalDate donation = TODAY.minusDays(1);
        controller.addDonor("D1", "Aisha", 25, 55.0, BloodType.A_POS, null);
        controller.addBloodUnit("U1", "D1", donation);
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
                        LocalDate.now().plusDays(1)));
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
    void unusedExpiredUnitDatesCanBeCorrected() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = new LifeFlowController(new LifeFlowState(), store);
        LocalDate donation = TODAY.minusDays(10);
        controller.addDonor("D1", "Aisha", 25, 55.0, BloodType.A_POS, null);
        controller.addBloodUnit("U1", "D1", TODAY.minusDays(50));

        controller.updateUnusedBloodUnitDonationDate("U1", TODAY.minusDays(45));
        assertEquals(TODAY.minusDays(10),
                controller.getUnits().get(0).getExpiryDate());
    }

    @Test
    void generatesTheNextDonorUnitAndRequestIdsFromSavedState() throws Exception {
        RecordingStore store = new RecordingStore();
        LifeFlowController controller = new LifeFlowController(new LifeFlowState(), store);
        assertEquals("D000001", controller.getNextDonorId());
        assertEquals("U000001", controller.getNextUnitId());
        assertEquals("R000001", controller.getNextRequestId());

        LocalDate donation = TODAY.minusDays(1);
        controller.addDonor("D1", "Aisha", 25, 55.0, BloodType.A_POS, null);
        controller.addBloodUnit("u009", "D1", donation);
        controller.addRequest("r012", "Ward", BloodType.A_POS, 1, false);

        assertEquals("D000002", controller.getNextDonorId());
        assertEquals("U000010", controller.getNextUnitId());
        assertEquals("R000013", controller.getNextRequestId());
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
