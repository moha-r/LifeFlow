package lifeflow;

import java.io.IOException;
import java.nio.file.Path;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import lifeflow.persistence.FileManager;
import lifeflow.ui.LifeFlowFrame;
import lifeflow.ui.UiTheme;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            installLookAndFeel();
            FileManager fileManager = new FileManager(Path.of("data"));
            try {
                LifeFlowFrame frame = new LifeFlowFrame(
                        fileManager.loadDonors(),
                        fileManager.loadUnits(),
                        fileManager.loadRequests(),
                        fileManager);
                frame.setVisible(true);
            } catch (IOException exception) {
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
