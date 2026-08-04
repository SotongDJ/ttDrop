package jacross;

import java.awt.Color;
import java.awt.Font;
import java.util.EnumMap;
import java.util.Map;

/**
 * The resolved token set for one theme: colours, radii, and the base
 * font. Immutable; a theme change builds a new instance (Themes).
 * Painting code reads tokens at paint time and never caches colours.
 */
public final class Tokens {
    private final Map<ColorRole, Color> colors;
    private final DesignLanguage language;
    private final boolean dark;
    private final Font baseFont;

    Tokens(Map<ColorRole, Color> colors, DesignLanguage language, boolean dark, Font baseFont) {
        this.colors = new EnumMap<>(colors);
        this.language = language;
        this.dark = dark;
        this.baseFont = baseFont;
    }

    public Color color(ColorRole role) {
        return colors.get(role);
    }

    public DesignLanguage language() {
        return language;
    }

    public boolean isDark() {
        return dark;
    }

    public Font font() {
        return baseFont;
    }

    /**
     * Corner radius in logical px for a control of the given height.
     * Fluent: small fixed radii. Material: pill for interactive
     * controls, moderate for fields.
     */
    public int radius(int height, boolean pillEligible) {
        if (language == DesignLanguage.FLUENT) {
            return 6;
        }
        return pillEligible ? height / 2 : 8;
    }

    /**
     * Interaction-state colour over a container. Material composes a
     * translucent layer of the "on" colour (hover 8%, pressed 10%);
     * Fluent replaces with a discretely darkened/lightened token.
     */
    public Color stateful(Color base, boolean hover, boolean pressed) {
        if (!hover && !pressed) {
            return base;
        }
        if (language == DesignLanguage.MATERIAL) {
            return blend(base, color(ColorRole.ON_SURFACE), pressed ? 0.10f : 0.08f);
        }
        int amount = pressed ? 14 : 7;
        return dark ? lighten(base, amount) : lighten(base, -amount);
    }

    static Color blend(Color base, Color over, float opacity) {
        float inverse = 1 - opacity;
        return new Color(
                Math.round(base.getRed() * inverse + over.getRed() * opacity),
                Math.round(base.getGreen() * inverse + over.getGreen() * opacity),
                Math.round(base.getBlue() * inverse + over.getBlue() * opacity));
    }

    static Color lighten(Color c, int amount) {
        return new Color(
                clamp(c.getRed() + amount), clamp(c.getGreen() + amount), clamp(c.getBlue() + amount));
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : v > 255 ? 255 : v;
    }

    /** WCAG contrast ratio between two colours (for tests). */
    public static double contrast(Color a, Color b) {
        double la = relLuminance(a);
        double lb = relLuminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double relLuminance(Color c) {
        double r = Ok.srgbToLinear(c.getRed() / 255.0);
        double g = Ok.srgbToLinear(c.getGreen() / 255.0);
        double b = Ok.srgbToLinear(c.getBlue() / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }
}
