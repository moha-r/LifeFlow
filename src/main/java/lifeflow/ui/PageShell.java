package lifeflow.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Shared dense page structure used by every LifeFlow workspace. */
@SuppressWarnings("serial")
public final class PageShell extends JPanel {
    private final JPanel header = new JPanel(new BorderLayout(UiTheme.SPACE_MD, 0));
    private final JPanel actions = new JPanel(new BorderLayout());
    private final JPanel toolbar = new JPanel(new BorderLayout());
    private final JPanel body = new JPanel(new BorderLayout());
    private final JPanel footer = new JPanel(new BorderLayout());

    public PageShell(String title, String subtitle) {
        super(new BorderLayout(0, UiTheme.SPACE_SM));
        setBackground(UiTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(UiTheme.SPACE_MD,
                UiTheme.SPACE_LG, UiTheme.SPACE_SM, UiTheme.SPACE_LG));

        header.setName("pageHeader");
        toolbar.setName("pageToolbar");
        body.setName("pageBody");
        footer.setName("pageFooter");
        header.setOpaque(false);
        actions.setOpaque(false);
        toolbar.setOpaque(false);
        body.setOpaque(false);
        footer.setOpaque(false);

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.add(UiComponents.title(title));
        copy.add(Box.createVerticalStrut(4));
        copy.add(UiComponents.muted(subtitle));
        header.add(copy, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(header);
        top.add(Box.createVerticalStrut(UiTheme.SPACE_SM));
        top.add(toolbar);
        add(top, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        toolbar.setVisible(false);
        footer.setVisible(false);
    }

    public void setActions(JComponent actions) {
        replace(this.actions, BorderLayout.CENTER, actions);
    }

    public void setToolbar(JComponent component) {
        replace(toolbar, BorderLayout.CENTER, component);
        toolbar.setVisible(true);
    }

    public void setBody(JComponent component) {
        replace(body, BorderLayout.CENTER, component);
    }

    public void setFooter(JComponent component) {
        replace(footer, BorderLayout.CENTER, component);
        footer.setVisible(true);
    }

    public JPanel getBodyPanel() {
        return body;
    }

    private static void replace(JPanel panel, String constraint, Component component) {
        panel.removeAll();
        panel.add(component, constraint);
        panel.revalidate();
        panel.repaint();
    }
}
