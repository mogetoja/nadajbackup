package pl.deathbackup;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Znakowanie itemow z drop'u smierci ukrytym ID smierci i usuwanie ich po nadaniu backupa.
 * Usuwane sa WYLACZNIE itemy z tagiem danej smierci - zwykle itemy tego samego typu zostaja.
 */
public class ItemCleaner {

    /** Jak dlugo pamietamy "do usuniecia" dla graczy offline / niezaladowanych chunkow. */
    private static final long EXPIRE_MS = 30L * 24 * 60 * 60 * 1000;

    private final DeathBackupPlugin plugin;
    private final NamespacedKey key;
    private final File file;
    private record Pending(long time, String owner) {
    }

    private final Map<Long, Pending> pending = new HashMap<>();

    public ItemCleaner(DeathBackupPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "death");
        this.file = new File(plugin.getDataFolder(), "pending.yml");
        loadPending();
    }

    // ------------------------------------------------------------ znacznik

    /** Kopia itemu z tagiem ID smierci. */
    public ItemStack mark(ItemStack item, long deathId) {
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) return copy;
        meta.getPersistentDataContainer().set(key, PersistentDataType.LONG, deathId);
        copy.setItemMeta(meta);
        return copy;
    }

    /** Zdejmuje tag (zeby itemy w backupie / po odbiorze byly czyste). */
    public ItemStack strip(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(key, PersistentDataType.LONG)) {
            return item;
        }
        meta.getPersistentDataContainer().remove(key);
        item.setItemMeta(meta);
        return item;
    }

    private Long markOf(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(key, PersistentDataType.LONG);
    }

    // ------------------------------------------------------------ czyszczenie

    private int cleanInventory(Inventory inv, Set<Long> ids, Map<Long, Integer> perDeath) {
        if (inv == null) return 0;
        int removed = 0;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            Long m = markOf(contents[i]);
            if (m != null && ids.contains(m)) {
                int amount = contents[i].getAmount();
                removed += amount;
                perDeath.merge(m, amount, Integer::sum);
                inv.setItem(i, null);
            }
        }
        return removed;
    }

    private int cleanInventory(Inventory inv, Set<Long> ids) {
        return cleanInventory(inv, ids, new HashMap<>());
    }

    /** Czysci gracza (ekwipunek, enderchest, kursor) i - jesli notify - informuje go o tym. */
    public int cleanPlayer(Player p, Set<Long> ids, boolean notify) {
        Map<Long, Integer> perDeath = new HashMap<>();
        int removed = cleanInventory(p.getInventory(), ids, perDeath)
                + cleanInventory(p.getEnderChest(), ids, perDeath);
        ItemStack cursor = p.getItemOnCursor();
        Long m = markOf(cursor);
        if (m != null && ids.contains(m)) {
            removed += cursor.getAmount();
            perDeath.merge(m, cursor.getAmount(), Integer::sum);
            p.setItemOnCursor(null);
        }
        if (notify) {
            for (Map.Entry<Long, Integer> en : perDeath.entrySet()) {
                Pending pd = pending.get(en.getKey());
                notifyPlayer(p, en.getValue(), pd == null ? "" : pd.owner());
            }
        }
        return removed;
    }

    public int cleanPlayer(Player p, Set<Long> ids) {
        return cleanPlayer(p, ids, true);
    }

    private void notifyPlayer(Player p, int amount, String owner) {
        String who = owner == null || owner.isBlank() ? "inny gracz" : "gracz §a" + owner + "§7";
        p.sendMessage(" ");
        p.sendMessage("§8§m                                                ");
        p.sendMessage("§c§l  BACKUP §8» §fUsunieto §e" + amount + " §f" + plural(amount)
                + " z Twojego ekwipunku");
        p.sendMessage("§7  Powod: " + who + " odebral backup swojej smierci,");
        p.sendMessage("§7  a te przedmioty pochodzily wlasnie z niej.");
        p.sendMessage("§8§m                                                ");
        p.sendMessage(" ");
    }

    private static String plural(int n) {
        if (n == 1) return "przedmiot";
        int last = n % 10, last2 = n % 100;
        if (last >= 2 && last <= 4 && !(last2 >= 12 && last2 <= 14)) return "przedmioty";
        return "przedmiotow";
    }

    /** Skrzynie, beczki, shulkery (postawione), leje itp. w chunku. */
    public int cleanChunk(Chunk chunk, Set<Long> ids) {
        int removed = 0;
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Container c) {
                removed += cleanInventory(c.getInventory(), ids);
            }
        }
        return removed;
    }

    /** Itemy na ziemi i minecarty ze skrzynia. */
    public int cleanEntities(Collection<Entity> entities, Set<Long> ids) {
        int removed = 0;
        for (Entity en : entities) {
            if (!en.isValid() || en instanceof Player) continue;
            if (en instanceof Item it) {
                Long m = markOf(it.getItemStack());
                if (m != null && ids.contains(m)) {
                    removed += it.getItemStack().getAmount();
                    it.remove();
                }
            } else if (en instanceof InventoryHolder holder) {
                removed += cleanInventory(holder.getInventory(), ids);
            }
        }
        return removed;
    }

    /**
     * Usuwa wszystkie itemy z tej smierci: gracze online (ekwipunek + enderchest),
     * zaladowane chunki (skrzynie, itemy na ziemi). Reszta (gracze offline,
     * niezaladowane chunki) zostanie wyczyszczona przy wejsciu / zaladowaniu chunku.
     */
    public int removeDeathItems(long deathId, String owner, java.util.UUID claimant) {
        pending.put(deathId, new Pending(System.currentTimeMillis(), owner));
        savePending();

        Set<Long> ids = Set.of(deathId);
        int removed = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            removed += cleanPlayer(p, ids, !p.getUniqueId().equals(claimant));
        }
        for (World w : Bukkit.getWorlds()) {
            for (Chunk ch : w.getLoadedChunks()) {
                removed += cleanChunk(ch, ids);
                removed += cleanEntities(Arrays.asList(ch.getEntities()), ids);
            }
        }
        return removed;
    }

    // ------------------------------------------------------------ oczekujace

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public Set<Long> pendingIds() {
        long now = System.currentTimeMillis();
        if (pending.values().removeIf(pd -> now - pd.time() > EXPIRE_MS)) {
            savePending();
        }
        return new HashSet<>(pending.keySet());
    }

    private void loadPending() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = cfg.getConfigurationSection("pending");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                long id = Long.parseLong(k);
                if (s.isConfigurationSection(k)) {
                    pending.put(id, new Pending(s.getLong(k + ".time"), s.getString(k + ".owner", "")));
                } else {
                    pending.put(id, new Pending(s.getLong(k), "")); // stary format
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void savePending() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<Long, Pending> e : pending.entrySet()) {
            cfg.set("pending." + e.getKey() + ".time", e.getValue().time());
            cfg.set("pending." + e.getKey() + ".owner", e.getValue().owner());
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Nie udalo sie zapisac pending.yml: " + ex.getMessage());
        }
    }
}
