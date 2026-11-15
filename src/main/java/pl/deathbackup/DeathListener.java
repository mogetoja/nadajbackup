package pl.deathbackup;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;

/** Zapisuje kazda smierc gracza (itemy + XP). */
public class DeathListener implements Listener {

    private final DeathBackupPlugin plugin;

    public DeathListener(DeathBackupPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();

        Map<Integer, ItemStack> items = new LinkedHashMap<>();
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && it.getType() != Material.AIR) {
                items.put(i, it.clone());
            }
        }

        int xp = XpUtil.getTotal(p);
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
    }

    private String describeCause(Player p) {
        EntityDamageEvent last = p.getLastDamageCause();
        if (last == null) return "Nieznana";

        if (last instanceof EntityDamageByEntityEvent by) {
            Entity d = by.getDamager();
            if (d instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Entity shooter) {
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
