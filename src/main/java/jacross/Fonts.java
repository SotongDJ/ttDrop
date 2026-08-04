package jacross;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;

/**
 * Loads the embedded Noto Sans TC (SIL OFL, subset OTF) — one family
 * with full Traditional Chinese and competent Latin coverage, so the
 * GUI renders identically on every OS with no fallback boxes. Java 2D
 * derives bold synthetically from the single Regular weight.
 */
final class Fonts {
    private static final String RESOURCE = "/jacross/NotoSansTC-Regular.otf";

    private Fonts() {
    }

    /** The UI font at the given size; logical Dialog if loading fails. */
    static Font ui(float size) {
        try (InputStream in = Fonts.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, in);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                return font.deriveFont(size);
            }
        } catch (Exception ignored) {
            // fall through to the logical font
        }
        return new Font(Font.DIALOG, Font.PLAIN, Math.round(size));
    }
}
