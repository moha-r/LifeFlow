package lifeflow.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.UIManager;

/** Shared colors and typography for the LifeFlow desktop interface. */
public final class UiTheme {
    public static final Color BACKGROUND = new Color(0xF2F5FA);
    public static final Color SURFACE = Color.WHITE;
    public static final Color CORAL = new Color(0xEF476F);
    public static final Color CORAL_DARK = new Color(0xD9365E);
    public static final Color CORAL_LIGHT = new Color(0xFFF0F4);
    public static final Color NAVY = new Color(0x182033);
    public static final Color MUTED = new Color(0x64708A);
    public static final Color BORDER = new Color(0xE3E9F2);
    public static final Color SHADOW = new Color(18, 24, 51, 18);
    public static final Color FOCUS = new Color(0x7C9AE8);
    public static final Color SUCCESS = new Color(0x26A97B);
    public static final Color SUCCESS_LIGHT = new Color(0xEAF9F3);
    public static final Color WARNING = new Color(0xE89618);
    public static final Color WARNING_LIGHT = new Color(0xFFF6E5);
    public static final Color DANGER = new Color(0xC9364F);
    public static final Color DANGER_LIGHT = new Color(0xFDECEF);
    public static final Color SIDEBAR = new Color(0x141C2C);
    public static final Color SIDEBAR_HOVER = new Color(0x232E44);
    public static final Color SIDEBAR_SELECTED = new Color(0x29344A);
    public static final Color SIDEBAR_TEXT = new Color(0xD8DFEB);
    public static final Color SIDEBAR_MUTED = new Color(0x8D98AD);
    public static final Color ROW_ALT = new Color(0xFBFCFD);
    public static final Color HEADER_FILL = new Color(0xF8FAFD);

    public static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 26);
    public static final Font HEADING = new Font(Font.SANS_SERIF, Font.BOLD, 17);
    public static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    public static final Font BODY_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    public static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    public static final int SPACE_XS = 8;
    public static final int SPACE_SM = 12;
    public static final int SPACE_MD = 18;
    public static final int SPACE_LG = 22;
    public static final int SIDEBAR_WIDTH = 232;
    public static final int UTILITY_HEIGHT = 56;
    public static final int CONTENT_MAX_WIDTH = 1320;

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
