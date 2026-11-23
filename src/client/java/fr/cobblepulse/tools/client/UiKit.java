package fr.cobblepulse.tools.client;

import net.minecraft.client.gui.GuiGraphics;

/** Palette et aides de dessin (memes couleurs que les ecrans de CobblePulse Utils). */
final class UiKit {
    static final int PANEL = 0xF0141922;
    static final int CARD = 0xFF1A202A;
    static final int CARD_ON = 0xFF263347;
    static final int BORDER = 0xFF2E3A4C;
    static final int ACCENT = 0xFF5FD3F3;
    static final int GOLD = 0xFFFFD24A;
    static final int TEXT = 0xFFE8ECF2;
    static final int MUTED = 0xFF8E9AAB;
    static final int OK = 0xFF6BE07A;
    static final int WARN = 0xFFFFA94D;
    static final int ERROR = 0xFFFF6B6B;

    private UiKit() {}

    static void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    static String norm(String s) {
        String n = java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(java.util.Locale.ROOT);
    }
}
