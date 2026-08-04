package jacross;

import java.awt.Color;

/**
 * A tonal palette: the hue and chroma of a seed colour, from which any
 * tone (CIE L* 0..100) can be produced in-gamut. Two nested bisections
 * per colour, sub-microsecond; palettes are built once per theme.
 */
public final class TonalPalette {
    private final double hue;
    private final double chroma;

    public static TonalPalette fromSeed(int argb) {
        double r = Ok.srgbToLinear(((argb >> 16) & 0xFF) / 255.0);
        double g = Ok.srgbToLinear(((argb >> 8) & 0xFF) / 255.0);
        double b = Ok.srgbToLinear((argb & 0xFF) / 255.0);
        double[] lab = Ok.toOklab(r, g, b);
        return new TonalPalette(Math.atan2(lab[2], lab[1]), Math.hypot(lab[1], lab[2]));
    }

    private TonalPalette(double hue, double chroma) {
        this.hue = hue;
        this.chroma = chroma;
    }

    /** Same hue, different chroma — for neutral/neutral-variant ramps. */
    public TonalPalette withChroma(double c) {
        return new TonalPalette(hue, c);
    }

    /** @param tone CIE L* target 0..100; returns an in-gamut sRGB colour. */
    public Color tone(double tone) {
        double lo = 0;
        double hi = 1;
        double lightness = 0.5;
        for (int i = 0; i < 24; i++) {
            lightness = (lo + hi) / 2;
            double[] rgb = clampChroma(lightness, chroma);
            if (Ok.lstar(rgb[0], rgb[1], rgb[2]) < tone) {
                lo = lightness;
            } else {
                hi = lightness;
            }
        }
        double[] lin = clampChroma(lightness, chroma);
        return new Color(
                (int) Math.round(255 * clamp01(Ok.linearToSrgb(lin[0]))),
                (int) Math.round(255 * clamp01(Ok.linearToSrgb(lin[1]))),
                (int) Math.round(255 * clamp01(Ok.linearToSrgb(lin[2]))));
    }

    private double[] clampChroma(double lightness, double c) {
        double lo = 0;
        double hi = c;
        double[] out = null;
        for (int i = 0; i < 16; i++) {
            double mid = (lo + hi) / 2;
            double[] rgb = Ok.toLinearRgb(lightness, Math.cos(hue) * mid, Math.sin(hue) * mid);
            if (inGamut(rgb)) {
                lo = mid;
                out = rgb;
            } else {
                hi = mid;
            }
        }
        return out != null ? out : Ok.toLinearRgb(lightness, 0, 0);
    }

    private static boolean inGamut(double[] c) {
        for (double v : c) {
            if (v < -1e-4 || v > 1 + 1e-4) {
                return false;
            }
        }
        return true;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
