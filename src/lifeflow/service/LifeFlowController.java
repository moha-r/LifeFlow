package lifeflow.service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import lifeflow.model.BloodRequest;
import lifeflow.model.BloodType;
import lifeflow.model.BloodUnit;
import lifeflow.model.Donor;
import lifeflow.model.EmergencyRequest;
import lifeflow.model.RegularRequest;
import lifeflow.model.RequestStatus;
import lifeflow.persistence.FileManager;

/** Coordinates UI operations, validation, services, and persistence. */
public class LifeFlowController {
    private final ArrayList<Donor> donors;
    private final ArrayList<BloodRequest> requests;
    private final BloodInventory inventory = new BloodInventory();
    private final MatchingService matchingService;
    private final FileManager fileManager;

    public LifeFlowController(ArrayList<Donor> donors, ArrayList<BloodUnit> units,
                              ArrayList<BloodRequest> requests,
                              FileManager fileManager) {
        this.donors = donors;
        this.requests = requests;
        this.fileManager = fileManager;
        for (BloodUnit unit : units) {
            inventory.addUnit(unit);
        }
        matchingService = new MatchingService(inventory);
    }

    public void addDonor(String id, String name, int age, double weight,
                         BloodType type, LocalDate lastDonation) throws IOException {
        String donorId = required(id, "Donor ID");
        if (findDonor(donorId) != null) {
            throw new IllegalArgumentException("Donor ID already exists.");
        }
        validatePersonDetails(name, age, weight);
        donors.add(new Donor(donorId, safeText(name, "Name"), age, weight,
                type, lastDonation));
        fileManager.saveDonors(donors);
    }

    public void addBloodUnit(String id, String donorId, LocalDate donationDate,
                             LocalDate expiryDate) throws IOException {
        String unitId = required(id, "Unit ID");
        if (inventory.containsId(unitId)) {
            throw new IllegalArgumentException("Unit ID already exists.");
        }
        Donor donor = findDonor(required(donorId, "Donor"));
        if (donor == null) {
            throw new IllegalArgumentException("Select a registered donor.");
        }
        if (donationDate == null || expiryDate == null) {
            throw new IllegalArgumentException("Donation and expiry dates are required.");
        }
        if (!donor.isEligible(donationDate)) {
            throw new IllegalArgumentException("Donor is not eligible for this donation date.");
        }
        if (expiryDate.isBefore(donationDate)) {
            throw new IllegalArgumentException("Expiry date cannot be before donation date.");
        }
        inventory.addUnit(new BloodUnit(unitId, donor.getId(), donor.getBloodType(),
                donationDate, expiryDate, lifeflow.model.UnitStatus.AVAILABLE));
        donor.recordDonation(donationDate);
        fileManager.saveDonors(donors);
        fileManager.saveUnits(inventory.getUnits());
    }

    public void updateDonor(String id, String name, int age, double weight,
                            BloodType type, LocalDate lastDonation) throws IOException {
        Donor donor = findDonor(required(id, "Donor ID"));
        if (donor == null) {
            throw new IllegalArgumentException("Donor was not found.");
        }
        validatePersonDetails(name, age, weight);
        if (hasUnitsForDonor(donor.getId())
                && donor.getBloodType() != type) {
            throw new IllegalArgumentException(
                    "Donor blood type cannot change after blood units are recorded.");
        }
        if (hasUnitsForDonor(donor.getId())
                && !Objects.equals(donor.getLastDonationDate(), lastDonation)) {
            throw new IllegalArgumentException(
                    "Last donation cannot change after blood units are recorded.");
        }
        donor.updateDetails(safeText(name, "Name"), age, weight, type, lastDonation);
        fileManager.saveDonors(donors);
    }

    public void updateBloodUnitExpiry(String id, LocalDate expiryDate) throws IOException {
        BloodUnit unit = findUnit(required(id, "Unit ID"));
        if (unit == null) {
            throw new IllegalArgumentException("Blood unit was not found.");
        }
        if (unit.getStatus() == lifeflow.model.UnitStatus.USED) {
            throw new IllegalArgumentException("A used blood unit cannot be edited.");
        }
        if (expiryDate == null || expiryDate.isBefore(unit.getDonationDate())) {
            throw new IllegalArgumentException(
                    "Expiry date cannot be before donation date.");
        }
        unit.updateExpiryDate(expiryDate);
        fileManager.saveUnits(inventory.getUnits());
    }

    public void addRequest(String id, String requester, BloodType type,
                           int quantity, boolean emergency) throws IOException {
        String requestId = required(id, "Request ID");
        if (findRequest(requestId) != null) {
            throw new IllegalArgumentException("Request ID already exists.");
        }
        String requesterName = safeText(requester, "Requester");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        BloodRequest request;
        if (emergency) {
            request = new EmergencyRequest(requestId, requesterName, type, quantity,
                    LocalDate.now(), RequestStatus.PENDING);
        } else {
            request = new RegularRequest(requestId, requesterName, type, quantity,
                    LocalDate.now(), RequestStatus.PENDING);
        }
        requests.add(request);
        fileManager.saveRequests(requests);
    }

    public void updatePendingRequest(String id, String requester, BloodType type,
                                     int quantity) throws IOException {
        BloodRequest request = findRequest(required(id, "Request ID"));
        if (request == null) {
            throw new IllegalArgumentException("Blood request was not found.");
        }
        if (request.getStatus() == RequestStatus.FULFILLED) {
            throw new IllegalArgumentException("A fulfilled request cannot be edited.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        request.updatePendingDetails(safeText(requester, "Requester"), type, quantity);
        fileManager.saveRequests(requests);
    }

    public BloodRequest getNextPendingRequest() {
        return matchingService.findNextPending(requests);
    }

    public ArrayList<BloodUnit> processNextRequest(LocalDate date) throws IOException {
        BloodRequest request = getNextPendingRequest();
        if (request == null) {
            return new ArrayList<>();
        }
        ArrayList<BloodUnit> matched = matchingService.match(request, date);
        if (!matched.isEmpty()) {
            fileManager.saveUnits(inventory.getUnits());
            fileManager.saveRequests(requests);
        }
        return matched;
    }

    public ArrayList<Donor> getDonors() {
        return new ArrayList<>(donors);
    }

    public ArrayList<BloodUnit> getUnits() {
        return inventory.getUnits();
    }

    public ArrayList<BloodRequest> getRequests() {
        return new ArrayList<>(requests);
    }

    public int getPendingRequestCount() {
        int count = 0;
        for (BloodRequest request : requests) {
            if (request.getStatus() == RequestStatus.PENDING) {
                count++;
            }
        }
        return count;
    }

    public int getPendingEmergencyCount() {
        int count = 0;
        for (BloodRequest request : requests) {
            if (request.getStatus() == RequestStatus.PENDING
                    && request instanceof EmergencyRequest) {
                count++;
            }
        }
        return count;
    }

    public int getAvailableUnitCount(LocalDate date) {
        int count = 0;
        for (BloodUnit unit : inventory.getUnits()) {
            if (unit.isAvailable(date)) {
                count++;
            }
        }
        return count;
    }

    public HashMap<BloodType, Integer> getStockCounts(LocalDate date) {
        return inventory.getStockCounts(date);
    }

    public void saveAll() throws IOException {
        fileManager.saveDonors(donors);
        fileManager.saveUnits(inventory.getUnits());
        fileManager.saveRequests(requests);
    }

    private Donor findDonor(String id) {
        for (Donor donor : donors) {
            if (donor.getId().equalsIgnoreCase(id)) {
                return donor;
            }
        }
        return null;
    }

    private BloodUnit findUnit(String id) {
        for (BloodUnit unit : inventory.getUnits()) {
            if (unit.getId().equalsIgnoreCase(id)) {
                return unit;
            }
        }
        return null;
    }

    private boolean hasUnitsForDonor(String donorId) {
        for (BloodUnit unit : inventory.getUnits()) {
            if (unit.getDonorId().equalsIgnoreCase(donorId)) {
                return true;
            }
        }
        return false;
    }

    private BloodRequest findRequest(String id) {
        for (BloodRequest request : requests) {
            if (request.getId().equalsIgnoreCase(id)) {
                return request;
            }
        }
        return null;
    }

    private void validatePersonDetails(String name, int age, double weight) {
        safeText(name, "Name");
        if (age <= 0 || weight <= 0) {
            throw new IllegalArgumentException("Age and weight must be positive numbers.");
        }
    }

    private String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    private String safeText(String value, String fieldName) {
        String text = required(value, fieldName);
        if (text.contains("|")) {
            throw new IllegalArgumentException(fieldName + " cannot contain the | character.");
        }
        return text;
    }
}
