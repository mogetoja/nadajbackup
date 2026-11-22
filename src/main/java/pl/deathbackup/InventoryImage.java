package pl.deathbackup;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rysuje ekwipunek (zbroja, ekwipunek, hotbar, druga reka) jako obrazek PNG.
 * Nie uzywa czcionek systemowych (wlasna czcionka pikselowa), wiec dziala tez na
 * serwerach bez zainstalowanych fontow. Czysta Java - bez zaleznosci od Bukkita.
 */
public final class InventoryImage {

    public record Slot(int slot, String id, int amount, boolean enchanted) {
    }

    public interface IconProvider {
        /** Ikona 16x16 (lub wieksza, kwadratowa) albo null, gdy brak tekstury. */
        BufferedImage icon(String id);
    }

    private static final int S = 3;            // skala pikseli
    private static final int CELL = 18 * S;    // jedno pole ekwipunku
    private static final int PAD = 12;
    private static final int GAP = 12;
    private static final int COLS = 9;

    private InventoryImage() {
    }

    public static byte[] render(List<Slot> slots, IconProvider icons) throws IOException {
        Map<Integer, Slot> bySlot = new HashMap<>();
        List<Slot> extras = new ArrayList<>();
        for (Slot s : slots) {
            if (s.slot() >= 0 && s.slot() <= 40) bySlot.put(s.slot(), s);
            else extras.add(s);
        }

        int extraRows = (extras.size() + COLS - 1) / COLS;
        int width = PAD * 2 + COLS * CELL;
        int yTop = PAD;
        int yMain = yTop + CELL + GAP;
        int yHot = yMain + 3 * CELL + GAP;
        int yExtra = yHot + CELL + GAP;
        int height = yHot + CELL + PAD + (extraRows > 0 ? GAP + extraRows * CELL : 0);

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // panel
        g.setColor(new Color(0xC6C6C6));
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, S);
        g.fillRect(0, 0, S, height);
        g.setColor(new Color(0x555555));
        g.fillRect(0, height - S, width, S);
        g.fillRect(width - S, 0, S, height);

        // gora: zbroja (helm, napiersnik, spodnie, buty) + druga reka
        int[] armorSlots = {39, 38, 37, 36};
        for (int i = 0; i < 4; i++) cell(g, bySlot.get(armorSlots[i]), PAD + i * CELL, yTop, icons);
        cell(g, bySlot.get(40), PAD + 8 * CELL, yTop, icons);

        // ekwipunek 3x9
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < COLS; c++) {
                cell(g, bySlot.get(9 + r * COLS + c), PAD + c * CELL, yMain + r * CELL, icons);
            }
        }
        // hotbar
        for (int c = 0; c < COLS; c++) {
            cell(g, bySlot.get(c), PAD + c * CELL, yHot, icons);
        }
        // nadmiarowe (gdy zapis pochodzi z listy dropow)
        for (int i = 0; i < extraRows * COLS; i++) {
            Slot s = i < extras.size() ? extras.get(i) : null;
            cell(g, s, PAD + (i % COLS) * CELL, yExtra + (i / COLS) * CELL, icons);
        }

        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ pole

    private static void cell(Graphics2D g, Slot s, int x, int y, IconProvider icons) {
        g.setColor(new Color(0x8B8B8B));
        g.fillRect(x, y, CELL, CELL);
        g.setColor(new Color(0x373737));
        g.fillRect(x, y, CELL, S);
        g.fillRect(x, y, S, CELL);
        g.setColor(Color.WHITE);
        g.fillRect(x, y + CELL - S, CELL, S);
        g.fillRect(x + CELL - S, y, S, CELL);

        if (s == null) return;

        BufferedImage icon = null;
        try {
            icon = icons == null ? null : icons.icon(s.id());
        } catch (RuntimeException ignored) {
        }

        int ix = x + S, iy = y + S, size = 16 * S;
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = scaled.createGraphics();
        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        if (icon != null) {
            sg.drawImage(icon, 0, 0, size, size, null);
        } else {
            fallbackIcon(sg, s.id(), size);
        }
        sg.dispose();

        if (s.enchanted()) tintEnchanted(scaled);
        g.drawImage(scaled, ix, iy, null);

        if (s.amount() > 1) {
            String t = String.valueOf(s.amount());
            int w = textWidth(t, S);
            text(g, t, x + CELL - S - w - 1, y + CELL - S - 5 * S - 1, S, Color.WHITE);
        }
    }

    /** Fioletowy odcien na nieprzezroczystych pikselach - oznacza enchantowany item. */
    private static void tintEnchanted(BufferedImage img) {
        for (int py = 0; py < img.getHeight(); py++) {
            for (int px = 0; px < img.getWidth(); px++) {
                int argb = img.getRGB(px, py);
                int a = argb >>> 24;
                if (a == 0) continue;
                int r = (argb >> 16) & 0xFF, gr = (argb >> 8) & 0xFF, b = argb & 0xFF;
                r = (int) (r * 0.65 + 150 * 0.35);
                gr = (int) (gr * 0.65 + 70 * 0.35);
                b = (int) (b * 0.65 + 255 * 0.35);
                img.setRGB(px, py, (a << 24) | (r << 16) | (gr << 8) | b);
            }
        }
    }

    /** Gdy nie ma tekstury: kolorowy kwadrat z inicjalami nazwy. */
    private static void fallbackIcon(Graphics2D g, String id, int size) {
        float hue = (Math.abs(id.hashCode()) % 360) / 360f;
        g.setColor(Color.getHSBColor(hue, 0.45f, 0.85f));
        g.fillRect(S * 2, S * 2, size - S * 4, size - S * 4);
        g.setColor(Color.getHSBColor(hue, 0.6f, 0.55f));
        g.fillRect(S * 2, S * 2, size - S * 4, S);
        g.fillRect(S * 2, size - S * 3, size - S * 4, S);
        g.fillRect(S * 2, S * 2, S, size - S * 4);
        g.fillRect(size - S * 3, S * 2, S, size - S * 4);

        StringBuilder ini = new StringBuilder();
        for (String part : id.toUpperCase().split("_")) {
            if (!part.isEmpty() && ini.length() < 2) ini.append(part.charAt(0));
        }
        String t = ini.toString();
        int w = textWidth(t, S);
        text(g, t, (size - w) / 2, (size - 5 * S) / 2, S, Color.WHITE);
    }

    // ------------------------------------------------------- czcionka 3x5

    private static final Map<Character, String> FONT = new HashMap<>();

    private static void def(char c, String rows) {
        FONT.put(c, rows);
    }

    static {
        def('0', "###" + "#.#" + "#.#" + "#.#" + "###");
        def('1', ".#." + "##." + ".#." + ".#." + "###");
        def('2', "##." + "..#" + ".#." + "#.." + "###");
        def('3', "##." + "..#" + ".#." + "..#" + "##.");
        def('4', "#.#" + "#.#" + "###" + "..#" + "..#");
        def('5', "###" + "#.." + "##." + "..#" + "##.");
        def('6', ".##" + "#.." + "###" + "#.#" + "###");
        def('7', "###" + "..#" + ".#." + ".#." + ".#.");
        def('8', "###" + "#.#" + "###" + "#.#" + "###");
        def('9', "###" + "#.#" + "###" + "..#" + "##.");
        def('A', ".#." + "#.#" + "###" + "#.#" + "#.#");
        def('B', "##." + "#.#" + "##." + "#.#" + "##.");
        def('C', ".##" + "#.." + "#.." + "#.." + ".##");
        def('D', "##." + "#.#" + "#.#" + "#.#" + "##.");
        def('E', "###" + "#.." + "##." + "#.." + "###");
        def('F', "###" + "#.." + "##." + "#.." + "#..");
        def('G', ".##" + "#.." + "#.#" + "#.#" + ".##");
        def('H', "#.#" + "#.#" + "###" + "#.#" + "#.#");
        def('I', "###" + ".#." + ".#." + ".#." + "###");
        def('J', "..#" + "..#" + "..#" + "#.#" + ".#.");
        def('K', "#.#" + "#.#" + "##." + "#.#" + "#.#");
        def('L', "#.." + "#.." + "#.." + "#.." + "###");
        def('M', "#.#" + "###" + "###" + "#.#" + "#.#");
        def('N', "##." + "#.#" + "#.#" + "#.#" + "#.#");
        def('O', ".#." + "#.#" + "#.#" + "#.#" + ".#.");
        def('P', "##." + "#.#" + "##." + "#.." + "#..");
        def('Q', ".#." + "#.#" + "#.#" + "##." + ".##");
        def('R', "##." + "#.#" + "##." + "#.#" + "#.#");
        def('S', ".##" + "#.." + ".#." + "..#" + "##.");
        def('T', "###" + ".#." + ".#." + ".#." + ".#.");
        def('U', "#.#" + "#.#" + "#.#" + "#.#" + "###");
        def('V', "#.#" + "#.#" + "#.#" + "#.#" + ".#.");
        def('W', "#.#" + "#.#" + "###" + "###" + "#.#");
        def('X', "#.#" + "#.#" + ".#." + "#.#" + "#.#");
        def('Y', "#.#" + "#.#" + ".#." + ".#." + ".#.");
        def('Z', "###" + "..#" + ".#." + "#.." + "###");
    }

    private static int textWidth(String t, int scale) {
        return t.isEmpty() ? 0 : t.length() * 4 * scale - scale;
    }

    private static void text(Graphics2D g, String t, int x, int y, int scale, Color color) {
        drawGlyphs(g, t, x + scale, y + scale, scale, new Color(0x3F3F3F));
        drawGlyphs(g, t, x, y, scale, color);
    }

    private static void drawGlyphs(Graphics2D g, String t, int x, int y, int scale, Color color) {
        g.setColor(color);
        int cx = x;
        for (char ch : t.toCharArray()) {
            String rows = FONT.get(ch);
            if (rows != null) {
                for (int i = 0; i < 15; i++) {
                    if (rows.charAt(i) == '#') {
                        g.fillRect(cx + (i % 3) * scale, y + (i / 3) * scale, scale, scale);
                    }
                }
            }
            cx += 4 * scale;
        }
    }
}
