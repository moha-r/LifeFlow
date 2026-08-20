package lifeflow.ui;

import lifeflow.model.Hospital;

/** Outcome of a completed sign-in flow: an admin session or a hospital account. */
public record LoginResult(boolean admin, Hospital hospital) {
    public static LoginResult adminSession() {
        return new LoginResult(true, null);
    }

    public static LoginResult hospitalSession(Hospital hospital) {
        return new LoginResult(false, hospital);
    }
}