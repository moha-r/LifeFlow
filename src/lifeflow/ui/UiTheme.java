package lifeflow.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.UIManager;

/** Shared colors and typography for the LifeFlow desktop interface. */
public final class UiTheme {
    public static final Color BACKGROUND = new Color(0xF5F7FB);
    public static final Color SURFACE = Color.WHITE;
    public static final Color CORAL = new Color(0xEF476F);
    public static final Color CORAL_DARK = new Color(0xD9365E);
    public static final Color CORAL_LIGHT = new Color(0xFFF0F4);
    public static final Color NAVY = new Color(0x182033);
    public static final Color MUTED = new Color(0x6B7280);
    public static final Color BORDER = new Color(0xE5EAF2);
    public static final Color SUCCESS = new Color(0x26A97B);
    public static final Color SUCCESS_LIGHT = new Color(0xEAF9F3);
    public static final Color WARNING = new Color(0xE89618);
    public static final Color WARNING_LIGHT = new Color(0xFFF6E5);
    public static final Color DANGER = new Color(0xC9364F);
    public static final Color DANGER_LIGHT = new Color(0xFDECEF);

    public static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 26);
    public static final Font HEADING = new Font(Font.SANS_SERIF, Font.BOLD, 17);
    public static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    public static final Font BODY_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    public static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    private UiTheme() {
    }

    public static void install() {
        UIManager.put("Label.font", BODY);
        UIManager.put("Button.font", BODY_BOLD);
        UIManager.put("TextField.font", BODY);
        UIManager.put("ComboBox.font", BODY);
        UIManager.put("Table.font", BODY);
        UIManager.put("TableHeader.font", BODY_BOLD);
        UIManager.put("OptionPane.messageFont", BODY);
        UIManager.put("OptionPane.buttonFont", BODY_BOLD);
    }
}
