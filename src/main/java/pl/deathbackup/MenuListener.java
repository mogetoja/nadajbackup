package pl.deathbackup;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Obsluga klikniec w menu backupow. */
public class MenuListener implements Listener {

    private final DeathBackupPlugin plugin;

    public MenuListener(DeathBackupPlugin plugin) {
        this.plugin = plugin;
    }

    private static boolean isOurs(InventoryHolder h) {
        return h instanceof Menus.ListHolder || h instanceof Menus.PreviewHolder;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (isOurs(e.getView().getTopInventory().getHolder())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!isOurs(holder)) return;

        e.setCancelled(true); // nic nie wolno wynosic / wkladac
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != top) return;

        if (holder instanceof Menus.ListHolder lh) {
            if (!allowed(p, lh.mode, lh.target)) return;
            handleList(p, lh, e.getSlot(), e.getClick());
        } else if (holder instanceof Menus.PreviewHolder ph) {
            if (!allowed(p, ph.mode, ph.target)) return;
            handlePreview(p, ph, e.getSlot());
        }
    }

    private boolean allowed(Player p, Menus.Mode mode, UUID target) {
        boolean ok = mode == Menus.Mode.ADMIN
                ? p.hasPermission("deathbackup.admin")
                : p.getUniqueId().equals(target);
        if (!ok) {
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
        }
        return ok;
    }

    private void handleList(Player p, Menus.ListHolder h, int slot, ClickType click) {
        if (slot < 0 || slot >= BackupManager.MAX) return;
        long id = h.ids[slot];
        if (id == 0) return;

        BackupEntry entry = plugin.manager().find(h.target, id);
        if (entry == null) {
            reopenList(p, h);
            return;
        }

        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            Bukkit.getScheduler().runTask(plugin,
                    () -> Menus.openPreview(plugin, p, h.target, h.targetName, h.mode, entry));
            return;
        }

        if (h.mode == Menus.Mode.ADMIN) {
            if (click == ClickType.SHIFT_LEFT) {
                revoke(p, h.target, h.targetName, entry);
            } else if (click == ClickType.LEFT) {
                grant(p, h.target, h.targetName, entry);
            } else {
                return;
            }
            reopenList(p, h);
        } else if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
            plugin.manager().claim(p, entry);
            reopenList(p, h);
        }
    }

    private void handlePreview(Player p, Menus.PreviewHolder h, int slot) {
        if (slot == Menus.SLOT_BACK) {
            Bukkit.getScheduler().runTask(plugin,
                    () -> Menus.openList(plugin, p, h.target, h.targetName, h.mode));
            return;
        }
        if (slot != Menus.SLOT_ACTION) return;

        BackupEntry entry = plugin.manager().find(h.target, h.entryId);
        if (entry == null) return;

        if (h.mode == Menus.Mode.ADMIN) {
            grant(p, h.target, h.targetName, entry);
        } else {
            plugin.manager().claim(p, entry);
        }
        Bukkit.getScheduler().runTask(plugin,
                () -> Menus.openList(plugin, p, h.target, h.targetName, h.mode));
    }

    private void reopenList(Player p, Menus.ListHolder h) {
        Bukkit.getScheduler().runTask(plugin,
                () -> Menus.openList(plugin, p, h.target, h.targetName, h.mode));
    }

    private void grant(Player admin, UUID target, String name, BackupEntry entry) {
        entry.granted = true;
        entry.claimed = false;
        plugin.manager().save(target);
        admin.sendMessage(DeathBackupPlugin.PREFIX + "§aNadano backup graczowi §f" + name + "§a.");

        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            online.sendMessage(DeathBackupPlugin.PREFIX + "§aOtrzymales backup! Wpisz §e/odbierzbackup §aaby go odebrac.");
        }
    }

    private void revoke(Player admin, UUID target, String name, BackupEntry entry) {
        entry.granted = false;
        entry.claimed = false;
        plugin.manager().save(target);
        admin.sendMessage(DeathBackupPlugin.PREFIX + "§eCofnieto nadanie backupu dla §f" + name + "§e.");
    }
}
