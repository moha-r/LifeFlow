package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Dense sidebar with one unambiguous active destination. */
@SuppressWarnings("serial")
public final class SidebarPanel extends JPanel {
    private static final String[] PAGES = {
        "Dashboard", "Donors", "Blood Inventory", "Blood Requests", "Matching", "Reports"
    };
    private static final String[] LABELS = {
        "Overview", "Donors", "Inventory", "Requests", "Matching", "Reports"
    };

    private final Map<String, JButton> navigation = new LinkedHashMap<>();

    public SidebarPanel(Consumer<String> navigationHandler) {
        super(new BorderLayout());
        setName("sidebar");
        setBackground(UiTheme.SIDEBAR);
        setPreferredSize(new Dimension(UiTheme.SIDEBAR_WIDTH, 0));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1,
                new java.awt.Color(0x2D374B)));
        add(buildNavigation(navigationHandler), BorderLayout.NORTH);
        add(buildNotice(), BorderLayout.SOUTH);
        showActive("Dashboard");
    }

    private JPanel buildNavigation(Consumer<String> handler) {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(24, 14, 12, 14));

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.setBorder(BorderFactory.createEmptyBorder(0, 10, 19, 10));
        JLabel name = new JLabel("LifeFlow");
        name.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 20));
        name.setForeground(java.awt.Color.WHITE);
        JLabel subtitle = new JLabel("Blood operations console");
        subtitle.setFont(UiTheme.SMALL);
        subtitle.setForeground(new java.awt.Color(0xFF6E90));
        brand.add(name);
        brand.add(Box.createVerticalStrut(3));
        brand.add(subtitle);
        content.add(brand);

        JLabel section = new JLabel("WORKSPACE");
        section.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.BOLD, 10));
        section.setForeground(UiTheme.SIDEBAR_MUTED);
        section.setBorder(BorderFactory.createEmptyBorder(15, 11, 8, 0));
        content.add(section);

        for (int index = 0; index < PAGES.length; index++) {
            String page = PAGES[index];
            JButton button = UiComponents.navButton(LABELS[index]);
            button.setIcon(new NavigationIcon(page, UiTheme.SIDEBAR_MUTED));
            button.setIconTextGap(11);
            button.addActionListener(event -> handler.accept(page));
            navigation.put(page, button);
            content.add(button);
            content.add(Box.createVerticalStrut(4));
        }
        return content;
    }

    private JPanel buildNotice() {
        JPanel notice = new JPanel();
        notice.setOpaque(false);
        notice.setLayout(new BoxLayout(notice, BoxLayout.Y_AXIS));
        notice.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0,
                        new java.awt.Color(0x2D374B)),
                BorderFactory.createEmptyBorder(14, 24, 16, 20)));
        JLabel title = new JLabel("Educational simulation");
        title.setFont(UiTheme.SMALL);
        title.setForeground(new java.awt.Color(0xFF819D));
        JLabel detail = new JLabel("Local data · non-clinical");
        detail.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
                java.awt.Font.PLAIN, 10));
        detail.setForeground(UiTheme.SIDEBAR_MUTED);
        notice.add(title);
        notice.add(Box.createVerticalStrut(4));
        notice.add(detail);
        return notice;
    }

    public void showActive(String page) {
        for (Map.Entry<String, JButton> entry : navigation.entrySet()) {
            boolean active = entry.getKey().equals(page);
            JButton button = entry.getValue();
            UiComponents.setNavActive(button, active);
            button.setIcon(new NavigationIcon(entry.getKey(), active
                    ? java.awt.Color.WHITE : UiTheme.SIDEBAR_MUTED));
        }
    }

    public int getActiveCount() {
        int count = 0;
        for (JButton button : navigation.values()) {
            if (Boolean.TRUE.equals(button.getClientProperty("navActive"))) {
                count++;
            }
        }
        return count;
    }

    public boolean isActive(String page) {
        JButton button = navigation.get(page);
        return button != null
                && Boolean.TRUE.equals(button.getClientProperty("navActive"));
    }
}
