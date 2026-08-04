package jacross;

import java.awt.Color;
import java.awt.Font;
import java.util.EnumMap;
import java.util.Map;

import static jacross.ColorRole.*;

/**
 * Builds a {@link Tokens} set from a seed colour, scheme, and design
 * language. Material tones follow the M3 role/tone table; the Fluent
 * side ships the published neutral ramp as constants and generates
 * only the brand colours from the seed.
 */
public final class Themes {
    private Themes() {
    }

    public static Tokens build(int seedArgb, boolean dark, DesignLanguage language, Font baseFont) {
        Map<ColorRole, Color> m = language == DesignLanguage.MATERIAL
                ? materialScheme(seedArgb, dark)
                : fluentScheme(seedArgb, dark);
        return new Tokens(m, language, dark, baseFont);
    }

    private static Map<ColorRole, Color> materialScheme(int seed, boolean dark) {
        TonalPalette p = TonalPalette.fromSeed(seed);
        TonalPalette n = p.withChroma(0.01);
        TonalPalette nv = p.withChroma(0.03);
        Map<ColorRole, Color> m = new EnumMap<>(ColorRole.class);
        if (!dark) {
            m.put(ACCENT, p.tone(40));
            m.put(ON_ACCENT, p.tone(100));
            m.put(ACCENT_CONTAINER, p.tone(90));
            m.put(ON_ACCENT_CONTAINER, p.tone(10));
            m.put(SURFACE, n.tone(98));
            m.put(SURFACE_CONTAINER_LOW, n.tone(96));
            m.put(SURFACE_CONTAINER, n.tone(94));
            m.put(SURFACE_CONTAINER_HIGH, n.tone(92));
            m.put(ON_SURFACE, n.tone(10));
            m.put(ON_SURFACE_VARIANT, nv.tone(30));
            m.put(OUTLINE, nv.tone(50));
            m.put(OUTLINE_VARIANT, nv.tone(80));
        } else {
            m.put(ACCENT, p.tone(80));
            m.put(ON_ACCENT, p.tone(20));
            m.put(ACCENT_CONTAINER, p.tone(30));
            m.put(ON_ACCENT_CONTAINER, p.tone(90));
            m.put(SURFACE, n.tone(6));
            m.put(SURFACE_CONTAINER_LOW, n.tone(10));
            m.put(SURFACE_CONTAINER, n.tone(12));
            m.put(SURFACE_CONTAINER_HIGH, n.tone(17));
            m.put(ON_SURFACE, n.tone(90));
            m.put(ON_SURFACE_VARIANT, nv.tone(80));
            m.put(OUTLINE, nv.tone(60));
            m.put(OUTLINE_VARIANT, nv.tone(30));
        }
        m.put(DANGER, dark ? new Color(0xF2B8B5) : new Color(0xB3261E));
        m.put(FOCUS, m.get(ACCENT));
        return m;
    }

    private static Map<ColorRole, Color> fluentScheme(int seed, boolean dark) {
        TonalPalette brand = TonalPalette.fromSeed(seed);
        Map<ColorRole, Color> m = new EnumMap<>(ColorRole.class);
        if (!dark) {
            m.put(SURFACE, new Color(0xFAFAFA));
            m.put(SURFACE_CONTAINER_LOW, new Color(0xF5F5F5));
            m.put(SURFACE_CONTAINER, new Color(0xF0F0F0));
            m.put(SURFACE_CONTAINER_HIGH, new Color(0xEBEBEB));
            m.put(ON_SURFACE, new Color(0x242424));
            m.put(ON_SURFACE_VARIANT, new Color(0x616161));
            m.put(OUTLINE, new Color(0x8A8A8A));
            m.put(OUTLINE_VARIANT, new Color(0xD1D1D1));
            m.put(ACCENT, brand.tone(45));
            m.put(ON_ACCENT, Color.WHITE);
            m.put(ACCENT_CONTAINER, brand.tone(90));
            m.put(ON_ACCENT_CONTAINER, brand.tone(20));
        } else {
            m.put(SURFACE, new Color(0x1F1F1F));
            m.put(SURFACE_CONTAINER_LOW, new Color(0x262626));
            m.put(SURFACE_CONTAINER, new Color(0x2B2B2B));
            m.put(SURFACE_CONTAINER_HIGH, new Color(0x333333));
            m.put(ON_SURFACE, new Color(0xF5F5F5));
            m.put(ON_SURFACE_VARIANT, new Color(0xADADAD));
            m.put(OUTLINE, new Color(0x757575));
            m.put(OUTLINE_VARIANT, new Color(0x474747));
            m.put(ACCENT, brand.tone(75));
            m.put(ON_ACCENT, brand.tone(15));
            m.put(ACCENT_CONTAINER, brand.tone(30));
            m.put(ON_ACCENT_CONTAINER, brand.tone(90));
        }
        m.put(DANGER, dark ? new Color(0xF1707B) : new Color(0xC42B1C));
        m.put(FOCUS, m.get(ACCENT));
        return m;
    }
}
