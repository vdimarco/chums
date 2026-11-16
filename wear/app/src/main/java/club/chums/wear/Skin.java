package club.chums.wear;

import android.graphics.Typeface;

/** One visual identity per provider build: colour, type and corner shape. */
final class Skin {
    static final int WARN = 0xfff2b84b, ON_WARN = 0xff241503, DANGER = 0xffff7a6b, ON_DANGER = 0xff2b0703;
    static final Skin NOW = of(BuildConfig.FIXED_AGENT);

    final int bg, surface, line, ink, muted, accent, onAccent, label, mine, onMine, ring;
    /** Corner radii in dp: full-width buttons and cards, and 48dp icon buttons. */
    final float radius, card, icon;
    /** Surfaces are drawn with a hairline edge instead of relying on fill alone. */
    final boolean outlined;
    final String display, body;

    private Skin(int bg, int surface, int line, int ink, int muted, int accent, int onAccent, int label,
                 int mine, int onMine, int ring, float radius, float card, float icon, boolean outlined,
                 String display, String body) {
        this.bg = bg; this.surface = surface; this.line = line; this.ink = ink; this.muted = muted;
        this.accent = accent; this.onAccent = onAccent; this.label = label; this.mine = mine; this.onMine = onMine;
        this.ring = ring; this.radius = radius; this.card = card; this.icon = icon; this.outlined = outlined;
        this.display = display; this.body = body;
    }

    static Skin of(String agent) {
        return switch (agent) {
            // Codex: graphite and ivory, monospace, squared corners.
            case "noodle" -> new Skin(0xff0f1011, 0xff1b1c1e, 0xff333436, 0xfff6f1e4, 0xffa9a79c, 0xfffff2d5,
                0xff171713, 0xfffff2d5, 0xff2f2d27, 0xfff6f1e4, 0xff28292b, 8, 8, 24, true, "monospace", "monospace");
            // Claude: clay orange on warm charcoal with ivory text, serif headings, soft shapes.
            case "squint" -> new Skin(0xff141413, 0xff262624, 0xff4a4a46, 0xfffaf9f5, 0xffb0aea5, 0xffd97757,
                0xff141413, 0xffd97757, 0xffd97757, 0xff141413, 0xff30302e, 24, 20, 24, false, "serif", "sans-serif");
            // Grok: white on black, heavy type, hairline edges.
            case "stretch" -> new Skin(0xff000000, 0xff0b0b0b, 0xff2e2e2e, 0xffffffff, 0xffa6a6a6, 0xffffffff,
                0xff000000, 0xffa6a6a6, 0xffffffff, 0xff000000, 0xff2e2e2e, 24, 20, 24, true, "sans-serif-black", "sans-serif");
            default -> new Skin(0xff000000, 0xff151c22, 0xff2c3a40, 0xffffffff, 0xffb5c3c5, 0xff80e1d6,
                0xff142327, 0xff80e1d6, 0xff1d292b, 0xffffffff, 0xff1d292b, 24, 16, 24, false, "sans-serif-medium", "sans-serif");
        };
    }

    Typeface displayFace() { return Typeface.create(display, Typeface.NORMAL); }
    Typeface bodyFace() { return Typeface.create(body, Typeface.NORMAL); }
    Typeface strongFace() {
        return "monospace".equals(body) ? Typeface.create("monospace", Typeface.BOLD) : Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }
}
