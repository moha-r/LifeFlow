package lifeflow.ui;

import java.awt.Color;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import lifeflow.service.HospitalRegistry;

/** Undecorated modal dialog hosting the role-aware sign-in screen. */
@SuppressWarnings("serial")
public final class LoginDialog extends JDialog {
    private LoginResult result;

    private LoginDialog(HospitalRegistry registry) {
        super((Frame) null, "LifeFlow — Admin Sign In");
        setModal(true);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));

        LoginPanel panel = new LoginPanel(registry, outcome -> {
            result = outcome;
            dispose();
        });
        panel.setRoleChangeListener(role -> setTitle("LifeFlow — " + role + " Sign In"));
        setContentPane(panel);
        pack();
        setLocationRelativeTo(null);
        getRootPane().setDefaultButton(panel.getSignInButton());
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "cancel-login");
        getRootPane().getActionMap().put("cancel-login", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                dispose();
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                SwingUtilities.invokeLater(panel.getUsernameField()::requestFocusInWindow);
            }
        });
    }

    /**
     * Shows the dialog and blocks until it closes.
     * Returns the signed-in session, or null when the user cancelled.
     */
    public static LoginResult showAndAuthenticate(HospitalRegistry registry) {
        LoginDialog dialog = new LoginDialog(registry);
        dialog.setVisible(true);
        return dialog.result;
    }
}