package jacross.plaf;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.AbstractButton;
import javax.swing.Icon;

import jacross.ColorRole;
import jacross.Tokens;

/** Path2D-drawn icons — never images, so they re-theme and scale. */
public final class JaCrossIcons {
    private JaCrossIcons() {
    }

    /** Check box: rounded square, ACCENT fill + tick when selected. */
    public static final class Check implements Icon {
        private static final int SIZE = 16;
        private final Tokens tokens;

        public Check(Tokens tokens) {
            this.tokens = tokens;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            boolean selected = c instanceof AbstractButton b && b.getModel().isSelected();
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                var box = new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, SIZE - 1, SIZE - 1, 8, 8);
                if (selected) {
                    g2.setColor(tokens.color(ColorRole.ACCENT));
                    g2.fill(box);
                    g2.setColor(tokens.color(ColorRole.ON_ACCENT));
                    g2.setStroke(new java.awt.BasicStroke(1.8f,
                            java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                    Path2D.Double tick = new Path2D.Double();
                    tick.moveTo(x + 3.8, y + 8.2);
                    tick.lineTo(x + 6.8, y + 11.2);
                    tick.lineTo(x + 12.2, y + 4.8);
                    g2.draw(tick);
                } else {
                    g2.setColor(c.isEnabled()
                            ? tokens.color(ColorRole.OUTLINE)
                            : tokens.color(ColorRole.OUTLINE_VARIANT));
                    g2.setStroke(new java.awt.BasicStroke(1.4f));
                    g2.draw(box);
                }
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
