package pl.deathbackup;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Zapisuje kazda smierc gracza (itemy + XP) - stan SPRZED smierci.
 *
 * Serwer oprozznia ekwipunek zanim wywola PlayerDeathEvent, dlatego migawke ekwipunku
 * robimy juz przy ostatnim (smiertelnym) obrazeniu, kiedy itemy jeszcze sa u gracza.
 */
public class DeathListener implements Listener {

    /** Jak dlugo migawka z smiertelnego obrazenia jest wazna (ms). */
    private static final long SNAPSHOT_MAX_AGE = 10_000L;

    private record Snapshot(long time, Map<Integer, ItemStack> items, int xp) {
    }

    private final DeathBackupPlugin plugin;
    private final Map<UUID, Snapshot> snapshots = new HashMap<>();

    public DeathListener(DeathBackupPlugin plugin) {
        this.plugin = plugin;
    }

    /** Smiertelne obrazenie - ekwipunek jest jeszcze nietkniety, robimy migawke. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFatalDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (p.getHealth() - e.getFinalDamage() > 0) return;

        snapshots.put(p.getUniqueId(),
                new Snapshot(System.currentTimeMillis(), copyInventory(p), XpUtil.getTotal(p)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Snapshot snap = snapshots.remove(p.getUniqueId());

        Map<Integer, ItemStack> items;
        int xp;

        if (snap != null && System.currentTimeMillis() - snap.time() <= SNAPSHOT_MAX_AGE) {
            // normalna sciezka: stan sprzed smierci
            items = snap.items();
            xp = snap.xp();
        } else {
            // smierc bez obrazen (np. setHealth(0)) - bierzemy co jest
            items = copyInventory(p);
            xp = XpUtil.getTotal(p);
            if (items.isEmpty()) {
                // ekwipunek juz oprozniony - ratujemy sie lista dropow
                int n = 0;
                for (ItemStack drop : e.getDrops()) {
                    if (drop == null || drop.getType() == Material.AIR) continue;
                    items.put(n < 36 ? n : 100 + n, plugin.cleaner().strip(drop.clone()));
                    n++;
                }
            }
            if (xp <= 0) xp = e.getDroppedExp();
        }

        if (items.isEmpty() && xp <= 0) {
            return; // nie ma czego backupowac
        }

        Location loc = p.getLocation();
        long now = System.currentTimeMillis();
        BackupEntry entry = new BackupEntry(
                now, now,
                loc.getWorld() == null ? "?" : loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                describeCause(p),
                xp, items, false, false);

        plugin.manager().add(p.getUniqueId(), entry);

        // oznaczamy wypadle itemy ID tej smierci - po nadaniu backupa usuniemy tylko je
        List<ItemStack> drops = e.getDrops();
        for (int i = 0; i < drops.size(); i++) {
            ItemStack d = drops.get(i);
            if (d != null && d.getType() != Material.AIR) {
                drops.set(i, plugin.cleaner().mark(d, now));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        snapshots.remove(e.getPlayer().getUniqueId());
    }

    private Map<Integer, ItemStack> copyInventory(Player p) {
        Map<Integer, ItemStack> items = new LinkedHashMap<>();
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && it.getType() != Material.AIR) {
                items.put(i, plugin.cleaner().strip(it.clone()));
            }
        }
        return items;
    }

    private String describeCause(Player p) {
        EntityDamageEvent last = p.getLastDamageCause();
        if (last == null) return "Nieznana";

        if (last instanceof EntityDamageByEntityEvent by) {
            Entity d = by.getDamager();
            if (d instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
                d = shooter;
            }
            String name = d.getName();
            return d instanceof Player ? "Gracz " + name : "Mob " + name;
        }

        return switch (last.getCause()) {
            case FALL -> "Upadek";
            case FIRE, FIRE_TICK -> "Ogien";
            case LAVA -> "Lawa";
            case DROWNING -> "Utoniecie";
            case VOID -> "Pustka";
            case STARVATION -> "Glod";
            case SUFFOCATION -> "Uduszenie";
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> "Eksplozja";
            case LIGHTNING -> "Piorun";
            case POISON -> "Trucizna";
            case WITHER -> "Wither";
            case MAGIC -> "Magia";
            case CONTACT -> "Kaktus / jagody";
            case SUICIDE -> "Samobojstwo";
            case FREEZE -> "Zamarznięcie";
            default -> last.getCause().name();
        };
    }
}
