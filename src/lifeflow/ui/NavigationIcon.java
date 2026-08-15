package lifeflow.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;

/** Paints consistent navigation symbols without font-dependent glyphs. */
public final class NavigationIcon implements Icon {
    private final String page;
    private final Color color;

    public NavigationIcon(String page, Color color) {
        this.page = page;
        this.color = color;
    }

    @Override
    public int getIconWidth() {
        return 16;
    }

    @Override
    public int getIconHeight() {
        return 16;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D copy = (Graphics2D) graphics.create();
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        copy.setColor(color);
        copy.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));

        if (page.equals("Dashboard")) {
            paintGrid(copy, x, y);
        } else if (page.equals("Donors")) {
            paintPerson(copy, x, y);
        } else if (page.equals("Blood Inventory")) {
            paintInventory(copy, x, y);
        } else if (page.equals("Blood Requests")) {
            paintList(copy, x, y);
        } else {
            paintMatch(copy, x, y);
        }
        copy.dispose();
    }

    private static void paintGrid(Graphics2D graphics, int x, int y) {
        graphics.drawRoundRect(x + 1, y + 1, 6, 6, 2, 2);
        graphics.drawRoundRect(x + 9, y + 1, 6, 6, 2, 2);
        graphics.drawRoundRect(x + 1, y + 9, 6, 6, 2, 2);
        graphics.drawRoundRect(x + 9, y + 9, 6, 6, 2, 2);
    }

    private static void paintPerson(Graphics2D graphics, int x, int y) {
        graphics.drawOval(x + 5, y + 1, 6, 6);
        graphics.drawArc(x + 2, y + 8, 12, 8, 0, 180);
    }

    private static void paintInventory(Graphics2D graphics, int x, int y) {
        graphics.drawRoundRect(x + 2, y + 2, 12, 12, 2, 2);
        graphics.drawLine(x + 2, y + 6, x + 14, y + 6);
        graphics.drawLine(x + 7, y + 2, x + 7, y + 14);
    }

    private static void paintList(Graphics2D graphics, int x, int y) {
        for (int row = 0; row < 3; row++) {
            int lineY = y + 3 + row * 5;
            graphics.fillOval(x + 1, lineY - 1, 2, 2);
            graphics.drawLine(x + 6, lineY, x + 15, lineY);
        }
    }

    private static void paintMatch(Graphics2D graphics, int x, int y) {
        graphics.drawLine(x + 2, y + 5, x + 13, y + 5);
        graphics.drawLine(x + 10, y + 2, x + 13, y + 5);
        graphics.drawLine(x + 10, y + 8, x + 13, y + 5);
        graphics.drawLine(x + 14, y + 11, x + 3, y + 11);
        graphics.drawLine(x + 6, y + 8, x + 3, y + 11);
        graphics.drawLine(x + 6, y + 14, x + 3, y + 11);
    }
}
