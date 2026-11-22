package pl.deathbackup;

import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;

/** Wysylanie wiadomosci na webhook Discorda (asynchronicznie, nie blokuje serwera). */
public final class Webhook {

    private Webhook() {
    }

    /** Zwykla wiadomosc tekstowa. */
    public static void send(DeathBackupPlugin plugin, String url, String message) {
        post(plugin, url, "{\"content\":\"" + escape(message) + "\",\"allowed_mentions\":{\"parse\":[]}}");
    }

    /** Embed. Kazde pole to {nazwa, wartosc, "true"/"false" (inline)}. */
    public static void sendEmbed(DeathBackupPlugin plugin, String url, String title, int color,
                                 String description, List<String[]> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"embeds\":[{");
        sb.append("\"title\":\"").append(escape(title)).append("\",");
        sb.append("\"description\":\"").append(escape(description)).append("\",");
        sb.append("\"color\":").append(color).append(',');
        sb.append("\"timestamp\":\"").append(Instant.now()).append("\",");
        sb.append("\"footer\":{\"text\":\"DeathBackup\"},");
        sb.append("\"fields\":[");
        for (int i = 0; i < fields.size(); i++) {
            String[] f = fields.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"name\":\"").append(escape(f[0])).append("\",")
                    .append("\"value\":\"").append(escape(f[1])).append("\",")
                    .append("\"inline\":").append(f[2]).append('}');
        }
        sb.append("]}],\"allowed_mentions\":{\"parse\":[]}}");
        post(plugin, url, sb.toString());
    }

    private static void post(DeathBackupPlugin plugin, String url, String json) {
        if (url == null || url.isBlank()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection c = (HttpURLConnection) URI.create(url.trim()).toURL().openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("User-Agent", "DeathBackup");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                int code = c.getResponseCode();
                if (code < 200 || code >= 300) {
                    plugin.getLogger().warning("Webhook Discorda zwrocil kod " + code);
                }
                c.disconnect();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Nie udalo sie wyslac webhooka: " + ex.getMessage());
            }
        });
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        return sb.toString();
    }
}
