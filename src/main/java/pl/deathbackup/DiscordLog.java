package pl.deathbackup;

import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Buduje embedy dla logow na Discordzie (pelna lista itemow z backupu). */
public final class DiscordLog {

    private static final int FIELD_MAX = 1000;
    private static final int LINE_MAX = 100;
    private static final int COLOR_GRANT = 0xF39C12;
    private static final int COLOR_CLAIM = 0x2ECC71;

    private DiscordLog() {
    }

    public static void grant(DeathBackupPlugin plugin, BackupEntry e, String owner, String admin) {
        send(plugin, "Nadano backup", COLOR_GRANT,
                "Administrator **" + admin + "** nadal backupa dla gracza **" + owner + "**.",
                e, owner, admin);
    }

    public static void claim(DeathBackupPlugin plugin, BackupEntry e, String owner, String admin) {
        send(plugin, "Odebrano backup", COLOR_CLAIM,
                "Gracz **" + owner + "** odebral backup nadany przez administratora **"
                        + (admin == null || admin.isBlank() ? "?" : admin) + "**.",
                e, owner, admin);
    }

    private static void send(DeathBackupPlugin plugin, String title, int color, String description,
                             BackupEntry e, String owner, String admin) {
        String url = plugin.getConfig().getString("discord-webhook", "");
        if (url == null || url.isBlank()) return;

        List<String[]> fields = new ArrayList<>();
        fields.add(new String[]{"Gracz", owner, "true"});
        fields.add(new String[]{"Administrator", admin == null || admin.isBlank() ? "-" : admin, "true"});
        fields.add(new String[]{"Przyczyna smierci", e.cause, "true"});
        fields.add(new String[]{"Data smierci",
                new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date(e.time)), "true"});
        fields.add(new String[]{"Lokalizacja",
                e.world + " " + (int) e.x + ", " + (int) e.y + ", " + (int) e.z, "true"});
        fields.add(new String[]{"XP", XpUtil.levelFor(e.xp) + " lvl (" + e.xp + " pkt)", "true"});

        // lista itemow, podzielona na pola po max ~1000 znakow
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Integer, ItemStack> en : new TreeMap<>(e.items).entrySet()) {
            lines.add(describe(en.getValue()));
        }
        String header = "Przedmioty (" + e.stackCount() + " slotow, " + e.itemCount() + " szt.)";
        if (lines.isEmpty()) {
            fields.add(new String[]{header, "brak", "false"});
        } else {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String line : lines) {
                if (sb.length() > 0 && sb.length() + line.length() + 1 > FIELD_MAX) {
                    fields.add(new String[]{first ? header : "Przedmioty (cd.)", sb.toString(), "false"});
                    sb.setLength(0);
                    first = false;
                }
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
            if (sb.length() > 0) {
                fields.add(new String[]{first ? header : "Przedmioty (cd.)", sb.toString(), "false"});
            }
        }

        Webhook.sendEmbed(plugin, url, title, color, description, fields);
    }

    // ------------------------------------------------------------ opis itemu

    private static String describe(ItemStack it) {
        StringBuilder sb = new StringBuilder("• ").append(it.getAmount()).append("x ");
        ItemMeta meta = it.getItemMeta();

        if (meta != null && meta.hasDisplayName()) {
            sb.append(ChatColor.stripColor(meta.getDisplayName()))
                    .append(" (").append(pretty(it.getType().name())).append(')');
        } else {
            sb.append(pretty(it.getType().name()));
        }

        List<String> ench = new ArrayList<>();
        if (meta != null) {
            for (Map.Entry<Enchantment, Integer> en : meta.getEnchants().entrySet()) {
                ench.add(pretty(en.getKey().getKey().getKey()) + " " + roman(en.getValue()));
            }
            if (meta instanceof EnchantmentStorageMeta book) {
                for (Map.Entry<Enchantment, Integer> en : book.getStoredEnchants().entrySet()) {
                    ench.add(pretty(en.getKey().getKey().getKey()) + " " + roman(en.getValue()));
                }
            }
        }
        if (!ench.isEmpty()) {
            sb.append(" — ").append(String.join(", ", ench));
        }

        String out = sb.toString();
        return out.length() > LINE_MAX ? out.substring(0, LINE_MAX - 1) + "…" : out;
    }

    private static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 1 && n <= 10 ? r[n] : String.valueOf(n);
    }
}
