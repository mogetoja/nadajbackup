package pl.deathbackup;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/** Zapis / odczyt backupow smierci (po jednym pliku YAML na gracza) + logika odbioru. */
public class BackupManager {

    public static final int MAX = 50;

    private final DeathBackupPlugin plugin;
    private final File dir;
    private final Map<UUID, List<BackupEntry>> cache = new HashMap<>();

    public BackupManager(DeathBackupPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "data");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Nie udalo sie utworzyc folderu data/");
        }
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    public boolean hasData(UUID id) {
        return cache.containsKey(id) || file(id).exists();
    }

    /** Lista backupow gracza, od najnowszego. */
    public List<BackupEntry> get(UUID id) {
        return cache.computeIfAbsent(id, this::load);
    }

    public BackupEntry find(UUID id, long entryId) {
        for (BackupEntry e : get(id)) {
            if (e.id == entryId) return e;
        }
        return null;
    }

    public void add(UUID id, BackupEntry entry) {
        List<BackupEntry> list = get(id);
        list.add(0, entry);
        while (list.size() > MAX) {
            list.remove(list.size() - 1);
        }
        save(id);
    }

    private List<BackupEntry> load(UUID id) {
        List<BackupEntry> list = new ArrayList<>();
        File f = file(id);
        if (!f.exists()) return list;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection deaths = cfg.getConfigurationSection("deaths");
        if (deaths == null) return list;

        for (String key : deaths.getKeys(false)) {
            ConfigurationSection s = deaths.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Map<Integer, ItemStack> items = new LinkedHashMap<>();
                ConfigurationSection is = s.getConfigurationSection("items");
                if (is != null) {
                    for (String slot : is.getKeys(false)) {
                        ItemStack it = is.getItemStack(slot);
                        if (it != null) items.put(Integer.parseInt(slot), it);
                    }
                }
                BackupEntry entry = new BackupEntry(
                        Long.parseLong(key),
                        s.getLong("time"),
                        s.getString("world", "?"),
                        s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                        s.getString("cause", "Nieznana"),
                        s.getInt("xp"),
                        items,
                        s.getBoolean("granted"),
                        s.getBoolean("claimed"));
                entry.grantedBy = s.getString("grantedBy", "");
                list.add(entry);
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("Pominieto uszkodzony wpis " + key + " w " + f.getName());
            }
        }
        list.sort((a, b) -> Long.compare(b.time, a.time));
        while (list.size() > MAX) {
            list.remove(list.size() - 1);
        }
        return list;
    }

    public void save(UUID id) {
        List<BackupEntry> list = get(id);
        YamlConfiguration cfg = new YamlConfiguration();
        for (BackupEntry e : list) {
            String p = "deaths." + e.id + ".";
            cfg.set(p + "time", e.time);
            cfg.set(p + "world", e.world);
            cfg.set(p + "x", e.x);
            cfg.set(p + "y", e.y);
            cfg.set(p + "z", e.z);
            cfg.set(p + "cause", e.cause);
            cfg.set(p + "xp", e.xp);
            cfg.set(p + "granted", e.granted);
            cfg.set(p + "claimed", e.claimed);
            cfg.set(p + "grantedBy", e.grantedBy);
            for (Map.Entry<Integer, ItemStack> it : e.items.entrySet()) {
                cfg.set(p + "items." + it.getKey(), it.getValue());
            }
        }
        try {
            cfg.save(file(id));
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Nie udalo sie zapisac backupow gracza " + id, ex);
        }
    }

    /**
     * Probuje wydac graczowi itemy i XP z backupu.
     * Gdy brakuje miejsca w ekwipunku - nic nie wydaje i zwraca false.
     */
    public boolean claim(Player p, BackupEntry e) {
        if (!e.granted || e.claimed) {
            p.sendMessage(DeathBackupPlugin.PREFIX + "§cTen backup nie jest juz dostepny do odbioru.");
            return false;
        }

        PlayerInventory inv = p.getInventory();

        // symulacja na kopii ekwipunku, zeby niczego nie wydac "po polowie"
        Inventory sim = Bukkit.createInventory(null, 36);
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < 36; i++) {
            sim.setItem(i, storage[i] == null ? null : storage[i].clone());
        }

        Map<Integer, ItemStack> direct = new LinkedHashMap<>(); // zbroja / druga reka
        List<ItemStack> rest = new ArrayList<>();

        for (Map.Entry<Integer, ItemStack> en : e.items.entrySet()) {
            int slot = en.getKey();
            ItemStack item = en.getValue().clone();
            if (slot >= 36 && slot <= 40) {
                if (inv.getItem(slot) == null) {
                    direct.put(slot, item);
                } else {
                    rest.add(item);
                }
            } else if (slot >= 0 && slot < 36 && sim.getItem(slot) == null) {
                sim.setItem(slot, item); // wraca na swoje dawne miejsce, jesli wolne
            } else {
                rest.add(item);
            }
        }

        Map<Integer, ItemStack> leftovers = sim.addItem(rest.toArray(new ItemStack[0]));
        if (!leftovers.isEmpty()) {
            p.sendMessage(DeathBackupPlugin.PREFIX + "§cBrak miejsca w ekwipunku! Zrob miejsce i sprobuj ponownie.");
            return false;
        }

        inv.setStorageContents(sim.getContents());
        for (Map.Entry<Integer, ItemStack> d : direct.entrySet()) {
            inv.setItem(d.getKey(), d.getValue());
        }
        if (e.xp > 0) {
            p.giveExp(e.xp);
        }

        e.claimed = true;
        save(p.getUniqueId());
        plugin.bars().refresh(p.getUniqueId()); // dopiero po oznaczeniu jako odebrany
        plugin.announceClaim(p.getName(), e.grantedBy);

        // dopiero po odebraniu usuwamy oryginalne itemy z tej smierci (tylko te z tagiem ID smierci)
        if (plugin.getConfig().getBoolean("remove-original-items", true)) {
            int removed = plugin.cleaner().removeDeathItems(e.id, p.getName(), p.getUniqueId());
            if (removed > 0) {
                plugin.getLogger().info("Usunieto " + removed + " oryginalnych przedmiotow ze smierci "
                        + e.id + " gracza " + p.getName() + " (backup odebrany).");
            }
        }
        p.sendMessage(DeathBackupPlugin.PREFIX + "§aOdebrano backup! §7(" + e.itemCount() + " przedmiotow, "
                + XpUtil.levelFor(e.xp) + " lvl)");
        return true;
    }
}
