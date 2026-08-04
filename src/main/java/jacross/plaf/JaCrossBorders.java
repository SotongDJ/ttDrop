package jacross.plaf;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.border.AbstractBorder;

import jacross.ColorRole;
import jacross.Tokens;

/** Shared borders: rounded field outline (focus-aware) and a hairline. */
public final class JaCrossBorders {
    private JaCrossBorders() {
    }

    public static final class Field extends AbstractBorder {
        private final Tokens tokens;

        public Field(Tokens tokens) {
            this.tokens = tokens;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                boolean focused = c.hasFocus()
                        || (c instanceof javax.swing.JComboBox<?> combo
                            && combo.getEditor() != null
                            && combo.getEditor().getEditorComponent().hasFocus());
                int arc = 2 * tokens.radius(h, false);
                float sw = focused ? 2f : 1f;
                g2.setStroke(new java.awt.BasicStroke(sw));
                g2.setColor(tokens.color(focused ? ColorRole.FOCUS : ColorRole.OUTLINE_VARIANT));
                g2.draw(new RoundRectangle2D.Float(
                        x + sw / 2, y + sw / 2, w - sw, h - sw, arc, arc));
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(5, 9, 5, 9);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(5, 9, 5, 9);
            return insets;
        }
    }

    public static final class Line extends AbstractBorder {
        private final Tokens tokens;
        private final ColorRole role;

        public Line(Tokens tokens, ColorRole role) {
            this.tokens = tokens;
            this.role = role;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            g.setColor(tokens.color(role));
            g.drawRect(x, y, w - 1, h - 1);
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(4, 8, 4, 8);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(4, 8, 4, 8);
            return insets;
        }
    }
}
