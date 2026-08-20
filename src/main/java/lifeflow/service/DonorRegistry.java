package lifeflow.service;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lifeflow.model.DonorAccount;
import lifeflow.model.exception.DuplicateIdException;
import lifeflow.model.exception.ValidationException;
import lifeflow.persistence.JsonDonorStore;

/** Self-service donor accounts used by the donor portal. */
public final class DonorRegistry implements AutoCloseable {
    public static final int MIN_PASSWORD_LENGTH = 4;

    private final List<DonorAccount> accounts;
    private final JsonDonorStore store;
    private final Clock clock;

    public DonorRegistry(List<DonorAccount> accounts, JsonDonorStore store) {
        this(accounts, store, Clock.systemDefaultZone());
    }

    public DonorRegistry(List<DonorAccount> accounts, JsonDonorStore store,
                         Clock clock) {
        this.accounts = new java.util.ArrayList<>(
                Objects.requireNonNull(accounts, "accounts"));
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Registers a new donor account linked to an existing donor profile. */
    public DonorAccount register(String donorId, String username, String password)
            throws IOException {
        String donorRef = required(donorId, "Donor ID");
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
        for (DonorAccount existing : accounts) {
            if (existing.getUsername().equalsIgnoreCase(account)) {
                throw new DuplicateIdException("Donor username", account);
            }
        }
        if (findByDonorId(donorRef) != null) {
            throw new ValidationException(
                    "This donor already has an account.", "donorId");
        }
        String id = nextAccountId();
        DonorAccount accountEntity = new DonorAccount(id, donorRef, account,
                password, LocalDate.now(clock));
        accounts.add(accountEntity);
        store.save(accounts);
        return accountEntity;
    }

    /** Returns the matching account or null when the credentials are wrong. */
    public DonorAccount authenticate(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        for (DonorAccount account : accounts) {
            if (account.getUsername().equalsIgnoreCase(username.trim())
                    && account.getPassword().equals(password)) {
                return account;
            }
        }
        return null;
    }

    /** Removes an account; used to roll back a failed self-registration. */
    public void remove(String username) throws IOException {
        for (java.util.Iterator<DonorAccount> iterator = accounts.iterator();
             iterator.hasNext();) {
            DonorAccount account = iterator.next();
            if (account.getUsername().equalsIgnoreCase(username)) {
                iterator.remove();
                store.save(accounts);
                return;
            }
        }
    }

    /** Verifies the current password and replaces it when it matches. */
    public void changePassword(String username, String current, String replacement)
            throws IOException {
        String account = required(username, "Username");
        DonorAccount found = authenticate(account, current);
        if (found == null) {
            throw new ValidationException(
                    "The current password is incorrect.", "currentPassword");
        }
        if (replacement == null || replacement.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException(
                    "New password must be at least " + MIN_PASSWORD_LENGTH
                            + " characters.", "newPassword");
        }
        accounts.remove(found);
        accounts.add(new DonorAccount(found.getId(), found.getDonorId(),
                found.getUsername(), replacement, found.getRegistrationDate()));
        store.save(accounts);
    }

    public DonorAccount findById(String id) {
        for (DonorAccount account : accounts) {
            if (account.getId().equalsIgnoreCase(id)) {
                return account;
            }
        }
        return null;
    }

    /** Returns the next free account id, always above every existing one. */
    private String nextAccountId() {
        long highest = 0;
        for (DonorAccount account : accounts) {
            String existing = account.getId();
            if (existing == null || existing.length() <= 2
                    || !existing.regionMatches(true, 0, "DA", 0, 2)) {
                continue;
            }
            String number = existing.substring(2);
            if (number.chars().allMatch(Character::isDigit)) {
                try {
                    highest = Math.max(highest, Long.parseLong(number));
                } catch (NumberFormatException ignored) {
                    // Custom identifiers outside the long range do not affect numbering.
                }
            }
        }
        return "DA" + (highest + 1);
    }

    public DonorAccount findByDonorId(String donorId) {
        for (DonorAccount account : accounts) {
            if (account.getDonorId().equalsIgnoreCase(donorId)) {
                return account;
            }
        }
        return null;
    }

    public List<DonorAccount> findAll() {
        return new java.util.ArrayList<>(accounts);
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(fieldName + " is required.", fieldName);
        }
        return value.trim();
    }

    @Override
    public void close() {
        store.close();
    }
}