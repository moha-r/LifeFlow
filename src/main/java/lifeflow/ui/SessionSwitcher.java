package lifeflow.ui;

import lifeflow.model.Hospital;

/** Session hand-off between the admin workspace and the hospital portal. */
public interface SessionSwitcher {
    /** Ends the application after the current frame closes. */
    void exitApplication();

    /** Opens the hospital portal; the current frame must be closed first. */
    void openHospitalPortal(Hospital hospital);

    /** Opens the admin workspace; the current frame must be closed first. */
    void openAdminWorkspace();
}