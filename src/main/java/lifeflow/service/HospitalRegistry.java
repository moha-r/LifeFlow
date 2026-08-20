package lifeflow.service;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lifeflow.model.Hospital;
import lifeflow.model.exception.DuplicateIdException;
import lifeflow.model.exception.ValidationException;
import lifeflow.persistence.JsonHospitalStore;

/** Self-service hospital accounts used by the hospital portal. */
public final class HospitalRegistry implements AutoCloseable {
    public static final int MIN_PASSWORD_LENGTH = 4;

    private final List<Hospital> hospitals;
    private final JsonHospitalStore store;
    private final Clock clock;

    public HospitalRegistry(List<Hospital> hospitals, JsonHospitalStore store) {
        this(hospitals, store, Clock.systemDefaultZone());
    }

    public HospitalRegistry(List<Hospital> hospitals, JsonHospitalStore store,
                            Clock clock) {
        this.hospitals = new java.util.ArrayList<>(
                Objects.requireNonNull(hospitals, "hospitals"));
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Registers a new hospital; the account is saved immediately. */
    public Hospital register(String name, String username, String password)
            throws IOException {
        String hospitalName = safeName(name, "Hospital name");
        String account = required(username, "Username");
        if (account.contains(" ") || account.contains("|")
                || account.contains("\n")) {
            throw new ValidationException(
                    "Username cannot contain spaces or special characters.", "username");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH
                            + " characters.", "password");
        }
        for (Hospital existing : hospitals) {
            if (existing.getUsername().equalsIgnoreCase(account)) {
                throw new DuplicateIdException("Hospital username", account);
            }
        }
        String id = "H" + (hospitals.size() + 1);
        while (findById(id) != null) {
            id = "H" + (hospitals.size() + 2);
        }
        Hospital hospital = new Hospital(id, hospitalName, account, password,
                LocalDate.now(clock));
        hospitals.add(hospital);
        store.save(hospitals);
        return hospital;
    }

    /** Returns the matching hospital or null when the credentials are wrong. */
    public Hospital authenticate(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        for (Hospital hospital : hospitals) {
            if (hospital.getUsername().equalsIgnoreCase(username.trim())
                    && hospital.getPassword().equals(password)) {
                return hospital;
            }
        }
        return null;
    }

    public Hospital findById(String id) {
        for (Hospital hospital : hospitals) {
            if (hospital.getId().equalsIgnoreCase(id)) {
                return hospital;
            }
        }
        return null;
    }

    public List<Hospital> findAll() {
        return new java.util.ArrayList<>(hospitals);
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(fieldName + " is required.", fieldName);
        }
        return value.trim();
    }

    private static String safeName(String value, String fieldName) {
        String text = required(value, fieldName);
        if (text.contains("|") || text.contains("\n") || text.contains("\r")) {
            throw new ValidationException(
                    fieldName + " cannot contain | or line breaks.", fieldName);
        }
        return text;
    }

    @Override
    public void close() {
        store.close();
    }
}