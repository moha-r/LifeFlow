package lifeflow.model;

import java.time.LocalDate;
import java.util.Locale;

/** A hospital or care entity that can place blood requests. */
public final class Hospital implements Identifiable {
    private String id;
    private String name;
    private String username;
    private String password;
    private LocalDate registrationDate;

    public Hospital(String id, String name, String username, String password,
                    LocalDate registrationDate) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.password = password;
        this.registrationDate = registrationDate;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
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
        return String.format("Hospital{id='%s', name='%s', username='%s'}",
                id, name, username);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Hospital other)) return false;
        return id.equalsIgnoreCase(other.id);
    }

    @Override
    public int hashCode() {
        return id.toLowerCase(Locale.ROOT).hashCode();
    }
}