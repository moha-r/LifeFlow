package lifeflow;

import java.io.IOException;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import lifeflow.model.LifeFlowState;
import lifeflow.persistence.JsonLifeFlowStore;
import lifeflow.persistence.StoragePaths;
import lifeflow.ui.LifeFlowFrame;
import lifeflow.ui.UiTheme;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            installLookAndFeel();
            JsonLifeFlowStore store = null;
            try {
                store = new JsonLifeFlowStore(StoragePaths.resolve());
                LifeFlowState state;
                try {
                    state = store.load();
                } catch (IOException loadFailure) {
                    int choice = JOptionPane.showConfirmDialog(null,
                            "LifeFlow found damaged local data.\n"
                                    + "Restore the latest verified JSON backup?\n\n"
                                    + loadFailure.getMessage(),
                            "Recovery Required", JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE);
                    if (choice != JOptionPane.YES_OPTION) {
                        store.close();
                        return;
                    }
                    state = store.restoreLatestBackup();
                }
                LifeFlowFrame frame = new LifeFlowFrame(state, store);
                frame.setVisible(true);
            } catch (IOException exception) {
                if (store != null) {
                    try {
                        store.close();
                    } catch (IOException ignored) {
                        // The original startup error is the useful message.
                    }
                }
                JOptionPane.showMessageDialog(null,
                        "LifeFlow could not load its data.\n" + exception.getMessage(),
                        "Startup Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (ReflectiveOperationException
                 | javax.swing.UnsupportedLookAndFeelException ignored) {
            // The platform default remains available when the cross-platform theme fails.
        }
        UiTheme.install();
    }
}
