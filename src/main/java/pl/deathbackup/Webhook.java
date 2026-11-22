package pl.deathbackup;

import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;

/** Wysylanie wiadomosci na webhook Discorda (asynchronicznie, nie blokuje serwera). */
public final class Webhook {

    private Webhook() {
    }

    /** Zwykla wiadomosc tekstowa. */
    public static void send(DeathBackupPlugin plugin, String url, String message) {
        post(plugin, url, "{\"content\":\"" + escape(message) + "\",\"allowed_mentions\":{\"parse\":[]}}");
    }

    /**
     * Embed. Kazde pole to {nazwa, wartosc, "true"/"false" (inline)}.
     * Gdy podano image, obrazek PNG jest renderowany asynchronicznie i dolaczany do embedu.
     */
    public static void sendEmbed(DeathBackupPlugin plugin, String url, String title, int color,
                                 String description, List<String[]> fields, Supplier<byte[]> image) {
        if (url == null || url.isBlank()) return;

        String plain = embedJson(title, color, description, fields, false);
        String withImage = embedJson(title, color, description, fields, true);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            byte[] png = null;
            if (image != null) {
                try {
                    png = image.get();
                } catch (Throwable t) {
                    plugin.getLogger().warning("Nie udalo sie wygenerowac obrazka backupu: " + t);
                }
            }
            if (png != null) postMultipart(plugin, url, withImage, png);
            else doPost(plugin, url, plain);
        });
    }

    private static String embedJson(String title, int color, String description,
                                    List<String[]> fields, boolean image) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"embeds\":[{");
        sb.append("\"title\":\"").append(escape(title)).append("\",");
        sb.append("\"description\":\"").append(escape(description)).append("\",");
        sb.append("\"color\":").append(color).append(',');
        sb.append("\"timestamp\":\"").append(Instant.now()).append("\",");
        sb.append("\"footer\":{\"text\":\"DeathBackup\"},");
        if (image) {
            sb.append("\"image\":{\"url\":\"attachment://backup.png\"},");
        }
        sb.append("\"fields\":[");
        for (int i = 0; i < fields.size(); i++) {
            String[] f = fields.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"name\":\"").append(escape(f[0])).append("\",")
                    .append("\"value\":\"").append(escape(f[1])).append("\",")
                    .append("\"inline\":").append(f[2]).append('}');
        }
        sb.append("]}],\"allowed_mentions\":{\"parse\":[]}}");
        return sb.toString();
    }

    private static void post(DeathBackupPlugin plugin, String url, String json) {
        if (url == null || url.isBlank()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> doPost(plugin, url, json));
    }

    private static void doPost(DeathBackupPlugin plugin, String url, String json) {
        try {
            HttpURLConnection c = open(url, "application/json");
            try (OutputStream os = c.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            check(plugin, c);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Nie udalo sie wyslac webhooka: " + ex.getMessage());
        }
    }

    private static void postMultipart(DeathBackupPlugin plugin, String url, String json, byte[] png) {
        try {
            String boundary = "----DeathBackup" + System.nanoTime();
            HttpURLConnection c = open(url, "multipart/form-data; boundary=" + boundary);
            try (OutputStream os = c.getOutputStream()) {
                os.write(("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"payload_json\"\r\n"
                        + "Content-Type: application/json\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.write(("\r\n--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"files[0]\"; filename=\"backup.png\"\r\n"
                        + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                os.write(png);
                os.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }
            check(plugin, c);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Nie udalo sie wyslac webhooka z obrazkiem: " + ex.getMessage());
        }
    }

    private static HttpURLConnection open(String url, String contentType) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url.trim()).toURL().openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(5000);
        c.setReadTimeout(10000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", contentType);
        c.setRequestProperty("User-Agent", "DeathBackup");
        return c;
    }

    private static void check(DeathBackupPlugin plugin, HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            plugin.getLogger().warning("Webhook Discorda zwrocil kod " + code);
        }
        c.disconnect();
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
