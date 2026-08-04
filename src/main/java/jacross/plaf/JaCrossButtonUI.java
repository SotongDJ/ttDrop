package jacross.plaf;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicButtonUI;

import jacross.ColorRole;
import jacross.DesignLanguage;
import jacross.Tokens;

/**
 * Tonal button: rounded container (pill under Material, 6px under
 * Fluent, which also strokes the outline — its stroke language is
 * load-bearing), hover/pressed state colours from the token layer, and
 * a focus ring painted inside space reserved by the border.
 */
public class JaCrossButtonUI extends BasicButtonUI {
    private static final int FOCUS_PAD = 3;

    public static ComponentUI createUI(JComponent c) {
        return new JaCrossButtonUI();
    }

    @Override
    public void installDefaults(AbstractButton b) {
        super.installDefaults(b);
        b.setOpaque(false);
        b.setRolloverEnabled(true);
        b.setBorder(javax.swing.BorderFactory.createEmptyBorder(
                FOCUS_PAD + 4, FOCUS_PAD + 12, FOCUS_PAD + 4, FOCUS_PAD + 12));
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        AbstractButton b = (AbstractButton) c;
        Tokens t = (Tokens) UIManager.get("jacross.tokens");
        if (t == null) {
            super.paint(g, c);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int w = b.getWidth();
            int h = b.getHeight();
            int inset = FOCUS_PAD;
            int arc = 2 * t.radius(h - 2 * inset, true);
            ButtonModel m = b.getModel();
            var shape = new RoundRectangle2D.Float(
                    inset, inset, w - 2 * inset, h - 2 * inset, arc, arc);

            java.awt.Color fill = t.stateful(t.color(ColorRole.SURFACE_CONTAINER_HIGH),
                    m.isRollover(), m.isArmed() && m.isPressed());
            g2.setColor(m.isEnabled() ? fill : t.color(ColorRole.SURFACE_CONTAINER));
            g2.fill(shape);
            if (t.language() == DesignLanguage.FLUENT) {
                g2.setColor(t.color(ColorRole.OUTLINE_VARIANT));
                g2.setStroke(new java.awt.BasicStroke(1f));
                g2.draw(shape);
            }
            if (b.hasFocus() && b.isFocusPainted()) {
                g2.setColor(t.color(ColorRole.FOCUS));
                g2.setStroke(new java.awt.BasicStroke(2f));
                int fp = 1;
                g2.draw(new RoundRectangle2D.Float(fp, fp, w - 2 * fp - 1, h - 2 * fp - 1,
                        arc + 4, arc + 4));
            }
        } finally {
            g2.dispose();
        }
        super.paint(g, c);
    }

    @Override
    protected void paintFocus(Graphics g, AbstractButton b,
            java.awt.Rectangle viewRect, java.awt.Rectangle textRect,
            java.awt.Rectangle iconRect) {
        // Focus is the ring painted in paint(); no dashed rectangle.
    }
}
