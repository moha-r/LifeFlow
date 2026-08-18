package lifeflow.ui;

import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Centers one page and prevents it from stretching beyond the design width. */
@SuppressWarnings("serial")
public final class BoundedContentPanel extends JPanel {
    private final JComponent content;

    public BoundedContentPanel(JComponent content) {
        this.content = content;
        setLayout(null);
        setBackground(UiTheme.BACKGROUND);
        add(content);
    }

    @Override
    public void doLayout() {
        int width = Math.min(getWidth(), UiTheme.CONTENT_MAX_WIDTH);
        int x = Math.max(0, (getWidth() - width) / 2);
        content.setBounds(x, 0, width, getHeight());
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension preferred = content.getPreferredSize();
        return new Dimension(Math.min(UiTheme.CONTENT_MAX_WIDTH, preferred.width),
                preferred.height);
    }

    public JComponent getContent() {
        return content;
    }
}
