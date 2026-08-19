package lifeflow.model;

import java.util.ArrayList;

/** One complete, copyable snapshot of all persistent LifeFlow data. */
public final class LifeFlowState {
    private final long revision;
    private final ArrayList<Donor> donors;
    private final ArrayList<BloodUnit> units;
    private final ArrayList<BloodRequest> requests;
    private final ArrayList<FulfilmentRecord> fulfilments;
    private final ArrayList<String> logs;

    public LifeFlowState() {
        this(0, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>());
    }

    public LifeFlowState(long revision, ArrayList<Donor> donors,
                         ArrayList<BloodUnit> units,
                         ArrayList<BloodRequest> requests,
                         ArrayList<FulfilmentRecord> fulfilments, ArrayList<String> logs) {
        this.revision = revision;
        this.donors = copyDonors(donors);
        this.units = copyUnits(units);
        this.requests = copyRequests(requests);
        this.fulfilments = copyFulfilments(fulfilments);
        this.logs = logs == null ? new ArrayList<>() : new ArrayList<>(logs);
    }

    public long getRevision() {
        return revision;
    }

    public ArrayList<Donor> getDonors() {
        return copyDonors(donors);
    }

    public ArrayList<BloodUnit> getUnits() {
        return copyUnits(units);
    }

    public ArrayList<BloodRequest> getRequests() {
        return copyRequests(requests);
    }

    public ArrayList<FulfilmentRecord> getFulfilments() {
        return copyFulfilments(fulfilments);
    }

    public ArrayList<String> getLogs() { return new ArrayList<>(logs); }

    public LifeFlowState copy() {
        return new LifeFlowState(revision, donors, units, requests, fulfilments, logs);
    }

    private static ArrayList<Donor> copyDonors(Iterable<Donor> source) {
        ArrayList<Donor> copies = new ArrayList<>();
        for (Donor donor : source) {
            copies.add(new Donor(donor.getId(), donor.getName(), donor.getAge(),
                    donor.getWeightKg(), donor.getBloodType(),
                    donor.getExternalLastDonationDate()));
        }
        return copies;
    }

    private static ArrayList<BloodUnit> copyUnits(Iterable<BloodUnit> source) {
        ArrayList<BloodUnit> copies = new ArrayList<>();
        for (BloodUnit unit : source) {
            copies.add(new BloodUnit(unit.getId(), unit.getDonorId(),
                    unit.getBloodType(), unit.getDonationDate(), unit.getExpiryDate(),
                    unit.getStatus()));
        }
        return copies;
    }

    private static ArrayList<BloodRequest> copyRequests(
            Iterable<BloodRequest> source) {
        ArrayList<BloodRequest> copies = new ArrayList<>();
        for (BloodRequest request : source) {
            BloodRequest copy;
            if (request instanceof EmergencyRequest) {
                copy = new EmergencyRequest(request.getId(), request.getRequesterName(),
                        request.getBloodType(), request.getQuantity(),
                        request.getRequestDate(), request.getStatus());
            } else {
                copy = new RegularRequest(request.getId(), request.getRequesterName(),
                        request.getBloodType(), request.getQuantity(),
                        request.getRequestDate(), request.getStatus());
            }
            copies.add(copy);
        }
        return copies;
    }

    private static ArrayList<FulfilmentRecord> copyFulfilments(
            Iterable<FulfilmentRecord> source) {
        ArrayList<FulfilmentRecord> copies = new ArrayList<>();
        for (FulfilmentRecord record : source) {
            copies.add(new FulfilmentRecord(record.requestId(),
                    record.processedDate(), record.unitIds()));
        }
        return copies;
    }
}
