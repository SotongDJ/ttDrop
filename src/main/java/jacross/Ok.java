package jacross;

/**
 * OKLab colour math (Björn Ottosson's matrices) plus CIE L*: the
 * Tier 0 basis for tonal palettes. HCT's "tone" is exactly CIE L*, so
 * targeting L* with OKLab hue/chroma stability approximates Material's
 * HCT within 1–3 per 8-bit channel — invisible in a UI.
 */
final class Ok {
    private Ok() {
    }

    static double srgbToLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    static double linearToSrgb(double c) {
        return c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
    }

    /** Linear sRGB → OKLab {L, a, b}. */
    static double[] toOklab(double r, double g, double b) {
        double l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b;
        double m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b;
        double s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b;
        double l2 = Math.cbrt(l);
        double m2 = Math.cbrt(m);
        double s2 = Math.cbrt(s);
        return new double[] {
            0.2104542553 * l2 + 0.7936177850 * m2 - 0.0040720468 * s2,
            1.9779984951 * l2 - 2.4285922050 * m2 + 0.4505937099 * s2,
            0.0259040371 * l2 + 0.7827717662 * m2 - 0.8086757660 * s2 };
    }

    /** OKLab → linear sRGB {r, g, b} (may be out of gamut). */
    static double[] toLinearRgb(double lab, double a, double b) {
        double l2 = lab + 0.3963377774 * a + 0.2158037573 * b;
        double m2 = lab - 0.1055613458 * a - 0.0638541728 * b;
        double s2 = lab - 0.0894841775 * a - 1.2914855480 * b;
        double l = l2 * l2 * l2;
        double m = m2 * m2 * m2;
        double s = s2 * s2 * s2;
        return new double[] {
            +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s };
    }

    /** CIE L* (0..100) of a linear-sRGB colour — Material's "tone". */
    static double lstar(double r, double g, double b) {
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double e = Math.pow(6.0 / 29.0, 3);
        double f = y > e ? Math.cbrt(y) : y / (3 * Math.pow(6.0 / 29.0, 2)) + 4.0 / 29.0;
        return 116 * f - 16;
    }
}
