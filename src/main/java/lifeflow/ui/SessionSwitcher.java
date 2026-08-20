package lifeflow.ui;

import lifeflow.model.DonorAccount;
import lifeflow.model.Hospital;

/** Session hand-off between the admin workspace and the self-service portals. */
public interface SessionSwitcher {
    /** Ends the application after the current frame closes. */
    void exitApplication();

    /** Opens the hospital portal; the current frame must be closed first. */
    void openHospitalPortal(Hospital hospital);

    /** Opens the donor portal; the current frame must be closed first. */
    void openDonorPortal(DonorAccount donor);

    /** Opens the admin workspace; the current frame must be closed first. */
    void openAdminWorkspace();

    /** Shows the sign-in dialog again and routes to the chosen session. */
    void showLogin();
}