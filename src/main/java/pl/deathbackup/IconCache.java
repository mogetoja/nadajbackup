package pl.deathbackup;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Tekstury itemow do obrazka na Discordzie. Pobierane z internetu przy pierwszym uzyciu
 * i zapisywane w plugins/DeathBackup/icons/ - plugin nie zawiera zadnych tekstur.
 */
public class IconCache implements InventoryImage.IconProvider {

    private static final long OFFLINE_PAUSE_MS = 10 * 60 * 1000L;
    private static final int MAX_BYTES = 1_000_000;

    private final File dir;
    private final List<String> templates;
    private final Logger log;
    private final Map<String, BufferedImage> memory = new ConcurrentHashMap<>();
    private final Set<String> missing = ConcurrentHashMap.newKeySet();
    private volatile long offlineUntil = 0;

    public IconCache(DeathBackupPlugin plugin) {
        this.log = plugin.getLogger();
        this.dir = new File(plugin.getDataFolder(), "icons");

        List<String> cfg = plugin.getConfig().getStringList("texture-urls");
        if (cfg.isEmpty()) {
            cfg = new ArrayList<>();
            String base = "https://raw.githubusercontent.com/PrismarineJS/minecraft-assets/master/data/";
            for (String version : new String[]{"1.21.4", "1.21.1"}) {
                cfg.add(base + version + "/items/%s.png");
                cfg.add(base + version + "/blocks/%s.png");
                cfg.add(base + version + "/blocks/%s_front.png");
                cfg.add(base + version + "/blocks/%s_side.png");
                cfg.add(base + version + "/blocks/%s_top.png");
            }
        }
        this.templates = cfg;
    }

    @Override
    public BufferedImage icon(String id) {
        if (!id.matches("[a-z0-9_]+")) return null;

        BufferedImage cached = memory.get(id);
        if (cached != null) return cached;
        if (missing.contains(id)) return null;

        File f = new File(dir, id + ".png");
        if (f.exists()) {
            try {
                BufferedImage img = ImageIO.read(f);
                if (img != null) {
                    img = square(img);
                    memory.put(id, img);
                    return img;
                }
            } catch (IOException ignored) {
            }
        }

        if (System.currentTimeMillis() < offlineUntil) return null;

        for (String t : templates) {
            byte[] data = download(String.format(t, id));
            if (data == null) {
                if (System.currentTimeMillis() < offlineUntil) return null; // brak sieci
                continue;
            }
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                if (img == null) continue;
                if (!dir.exists() && !dir.mkdirs()) {
                    log.warning("Nie udalo sie utworzyc folderu icons/");
                }
                ImageIO.write(img, "png", f);
                img = square(img);
                memory.put(id, img);
                return img;
            } catch (IOException ex) {
                // sprobuj nastepny adres
            }
        }
        missing.add(id);
        return null;
    }

    /** Tekstury animowane maja kilka klatek w pionie - bierzemy pierwsza. */
    private static BufferedImage square(BufferedImage img) {
        BufferedImage src = img;
        if (src.getHeight() > src.getWidth()) {
            src = src.getSubimage(0, 0, src.getWidth(), src.getWidth());
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(src, 0, 0, null);
        return out;
    }

    private byte[] download(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(6000);
            c.setRequestProperty("User-Agent", "DeathBackup");
            int code = c.getResponseCode();
            if (code != 200) {
                c.disconnect();
                return null; // 404 itp. - tekstury nie ma pod tym adresem
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BYTES) return null;
                }
                return out.toByteArray();
            } finally {
                c.disconnect();
            }
        } catch (IOException ex) {
            offlineUntil = System.currentTimeMillis() + OFFLINE_PAUSE_MS;
            log.warning("Nie mozna pobrac tekstur itemow (" + ex.getMessage()
                    + ") - ponowna proba za 10 minut. Na obrazku beda zastepcze ikony.");
            return null;
        }
    }
}
