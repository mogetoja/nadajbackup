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
    private final Map<Long, Long> pending = new HashMap<>();

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

    private int cleanInventory(Inventory inv, Set<Long> ids) {
        if (inv == null) return 0;
        int removed = 0;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            Long m = markOf(contents[i]);
            if (m != null && ids.contains(m)) {
                removed += contents[i].getAmount();
                inv.setItem(i, null);
            }
        }
        return removed;
    }

    public int cleanPlayer(Player p, Set<Long> ids) {
        int removed = cleanInventory(p.getInventory(), ids) + cleanInventory(p.getEnderChest(), ids);
        ItemStack cursor = p.getItemOnCursor();
        Long m = markOf(cursor);
        if (m != null && ids.contains(m)) {
            removed += cursor.getAmount();
            p.setItemOnCursor(null);
        }
        return removed;
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
    public int removeDeathItems(long deathId) {
        pending.put(deathId, System.currentTimeMillis());
        savePending();

        Set<Long> ids = Set.of(deathId);
        int removed = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            removed += cleanPlayer(p, ids);
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
        if (pending.values().removeIf(t -> now - t > EXPIRE_MS)) {
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
                pending.put(Long.parseLong(k), s.getLong(k));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void savePending() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<Long, Long> e : pending.entrySet()) {
            cfg.set("pending." + e.getKey(), e.getValue());
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Nie udalo sie zapisac pending.yml: " + ex.getMessage());
        }
    }
}
