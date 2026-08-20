package lifeflow.ui;

import lifeflow.model.DonorAccount;
import lifeflow.model.Hospital;

/** Outcome of a completed sign-in flow: admin, hospital, or donor session. */
public record LoginResult(boolean admin, Hospital hospital, DonorAccount donor) {
    public static LoginResult adminSession() {
        return new LoginResult(true, null, null);
    }

    public static LoginResult hospitalSession(Hospital hospital) {
        return new LoginResult(false, hospital, null);
    }

    public static LoginResult donorSession(DonorAccount donor) {
        return new LoginResult(false, null, donor);
    }
}