package lifeflow;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.UnitStatus;
import lifeflow.persistence.FileManager;
import lifeflow.service.LifeFlowController;

final class LifeFlowControllerTests {
    private LifeFlowControllerTests() {
    }

    static void run() throws IOException {
        addsAndPersistsDonor();
        rejectsDuplicateDonorIds();
        addsEligibleUnitAndUpdatesCounts();
        rejectsUnitForIneligibleDonor();
        updatesDonorAndProtectsLinkedBloodData();
        updatesOnlyAvailableUnitExpiry();
        managesRequestsDashboardCountsAndMatching();
        protectsFulfilledRequestsFromEditing();
    }

    private static void addsAndPersistsDonor() throws IOException {
        FileManager files = new FileManager(Files.createTempDirectory("lifeflow-controller-"));
        LifeFlowController controller = new LifeFlowController(
                new ArrayList<Donor>(), new ArrayList<BloodUnit>(),
                new ArrayList<BloodRequest>(), files);

        controller.addDonor("D1", "Aisha Noor", 24, 55.0,
                BloodType.A_POS, LocalDate.of(2026, 1, 1));

        assert controller.getDonors().size() == 1;
        assert controller.getDonors().get(0).getName().equals("Aisha Noor");
        assert files.loadDonors().size() == 1 : "Successful changes must be saved";
    }

    private static void rejectsDuplicateDonorIds() throws IOException {
        LifeFlowController controller = emptyController();
        controller.addDonor("D1", "First Donor", 24, 55.0,
                BloodType.A_POS, null);

        boolean rejected = false;
        try {
            controller.addDonor("d1", "Duplicate", 30, 60.0,
                    BloodType.O_POS, null);
        } catch (IllegalArgumentException exception) {
            rejected = exception.getMessage().contains("already exists");
        }
        assert rejected : "Donor IDs must be unique ignoring case";
    }

    private static void addsEligibleUnitAndUpdatesCounts() throws IOException {
        FileManager files = new FileManager(Files.createTempDirectory("lifeflow-unit-"));
        LifeFlowController controller = new LifeFlowController(
                new ArrayList<Donor>(), new ArrayList<BloodUnit>(),
                new ArrayList<BloodRequest>(), files);
        LocalDate donationDate = LocalDate.now().minusDays(1);
        controller.addDonor("D1", "Eligible Donor", 28, 60.0,
                BloodType.O_NEG, null);

        controller.addBloodUnit("U1", "D1", donationDate,
                donationDate.plusDays(35));

        assert controller.getUnits().size() == 1;
        assert controller.getAvailableUnitCount(LocalDate.now()) == 1;
        assert controller.getStockCounts(LocalDate.now()).get(BloodType.O_NEG) == 1;
        assert files.loadUnits().size() == 1;
    }

    private static void rejectsUnitForIneligibleDonor() throws IOException {
        LifeFlowController controller = emptyController();
        controller.addDonor("D1", "Young Donor", 17, 55.0,
                BloodType.B_POS, null);

        boolean rejected = false;
        try {
            controller.addBloodUnit("U1", "D1", LocalDate.now(),
                    LocalDate.now().plusDays(30));
        } catch (IllegalArgumentException exception) {
            rejected = exception.getMessage().contains("not eligible");
        }
        assert rejected : "Ineligible donors cannot create blood units";
        assert controller.getUnits().isEmpty();
    }

    private static void updatesDonorAndProtectsLinkedBloodData() throws IOException {
        LifeFlowController controller = emptyController();
        controller.addDonor("D1", "Original Name", 25, 55.0,
                BloodType.A_POS, null);
        controller.updateDonor("D1", "Updated Name", 26, 58.0,
                BloodType.B_POS, LocalDate.of(2025, 1, 1));
        Donor updated = controller.getDonors().get(0);
        assert updated.getName().equals("Updated Name");
        assert updated.getBloodType() == BloodType.B_POS;

        LocalDate donation = LocalDate.now().minusDays(1);
        controller.updateDonor("D1", "Updated Name", 26, 58.0,
                BloodType.B_POS, null);
        controller.addBloodUnit("U1", "D1", donation, donation.plusDays(30));

        boolean rejected = false;
        try {
            controller.updateDonor("D1", "Still Valid", 27, 59.0,
                    BloodType.O_NEG, donation);
        } catch (IllegalArgumentException exception) {
            rejected = exception.getMessage().contains("blood type");
        }
        assert rejected : "A donor blood type cannot change after units exist";
    }

    private static void updatesOnlyAvailableUnitExpiry() throws IOException {
        LifeFlowController controller = emptyController();
        LocalDate donation = LocalDate.now().minusDays(1);
        controller.addDonor("D1", "Unit Donor", 30, 65.0, BloodType.A_NEG, null);
        controller.addBloodUnit("U1", "D1", donation, donation.plusDays(20));

        LocalDate newExpiry = donation.plusDays(35);
        controller.updateBloodUnitExpiry("U1", newExpiry);
        assert controller.getUnits().get(0).getExpiryDate().equals(newExpiry);

        controller.getUnits().get(0).setStatus(UnitStatus.USED);
        boolean rejected = false;
        try {
            controller.updateBloodUnitExpiry("U1", donation.plusDays(40));
        } catch (IllegalArgumentException exception) {
            rejected = exception.getMessage().contains("used");
        }
        assert rejected : "Used blood units must be read-only";
    }

    private static void managesRequestsDashboardCountsAndMatching() throws IOException {
        LifeFlowController controller = emptyController();
        controller.addRequest("R1", "Clinic A", BloodType.A_POS, 2, false);
        controller.addRequest("R2", "Emergency Ward", BloodType.O_NEG, 1, true);

        assert controller.getRequests().size() == 2;
        assert controller.getPendingRequestCount() == 2;
        assert controller.getPendingEmergencyCount() == 1;
        assert controller.getNextPendingRequest().getId().equals("R2");

        controller.updatePendingRequest("R1", "Clinic B", BloodType.B_POS, 3);
        assert controller.getRequests().get(0).getRequesterName().equals("Clinic B");
        assert controller.getRequests().get(0).getBloodType() == BloodType.B_POS;

        LocalDate donation = LocalDate.now().minusDays(1);
        controller.addDonor("D1", "Emergency Donor", 30, 60.0,
                BloodType.O_NEG, null);
        controller.addBloodUnit("U1", "D1", donation, donation.plusDays(30));

        ArrayList<BloodUnit> matched = controller.processNextRequest(LocalDate.now());
        assert matched.size() == 1;
        assert controller.getRequests().get(1).getStatus()
                == lifeflow.model.RequestStatus.FULFILLED;
        assert controller.getPendingRequestCount() == 1;
        assert controller.getAvailableUnitCount(LocalDate.now()) == 0;
    }

    private static void protectsFulfilledRequestsFromEditing() throws IOException {
        LifeFlowController controller = emptyController();
        controller.addRequest("R1", "Ward", BloodType.A_POS, 1, false);
        controller.getRequests().get(0).setStatus(lifeflow.model.RequestStatus.FULFILLED);

        boolean rejected = false;
        try {
            controller.updatePendingRequest("R1", "Changed", BloodType.B_POS, 2);
        } catch (IllegalArgumentException exception) {
            rejected = exception.getMessage().contains("fulfilled");
        }
        assert rejected : "Fulfilled requests must be read-only";
    }

    private static LifeFlowController emptyController() throws IOException {
        return new LifeFlowController(new ArrayList<Donor>(),
                new ArrayList<BloodUnit>(), new ArrayList<BloodRequest>(),
                new FileManager(Files.createTempDirectory("lifeflow-controller-empty-")));
    }
}
