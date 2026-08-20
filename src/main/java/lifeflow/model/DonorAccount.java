package lifeflow.model;

import java.time.LocalDate;
import java.util.Locale;

/** Self-service donor login separate from the medical donor profile. */
public final class DonorAccount implements Identifiable {
    private String id;
    private String donorId;
    private String username;
    private String password;
    private LocalDate registrationDate;

    public DonorAccount(String id, String donorId, String username,
                        String password, LocalDate registrationDate) {
        this.id = id;
        this.donorId = donorId;
        this.username = username;
        this.password = password;
        this.registrationDate = registrationDate;
    }

    public String getId() {
        return id;
    }

    public String getDonorId() {
        return donorId;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    @Override
    public String toString() {
        return String.format("DonorAccount{id='%s', donorId='%s', username='%s'}",
                id, donorId, username);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DonorAccount other)) return false;
        return id.equalsIgnoreCase(other.id);
    }

    @Override
    public int hashCode() {
        return id.toLowerCase(Locale.ROOT).hashCode();
    }
}
