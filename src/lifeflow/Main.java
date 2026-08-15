package lifeflow;

import java.io.IOException;
import java.nio.file.Path;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import lifeflow.persistence.FileManager;
import lifeflow.ui.LifeFlowFrame;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            useNimbusLookAndFeel();
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

    private static void useNimbusLookAndFeel() {
        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
                try {
                    UIManager.setLookAndFeel(info.getClassName());
                } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ignored) {
                    // The platform default remains available when Nimbus cannot load.
                }
                break;
            }
        }
    }
}
