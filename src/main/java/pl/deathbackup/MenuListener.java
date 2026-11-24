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
            if (lh.mode == Menus.Mode.VERIFY) {
                handleVerifyList(p, lh, e.getSlot(), e.getClick());
            } else {
                handleList(p, lh, e.getSlot(), e.getClick());
            }
        } else if (holder instanceof Menus.PreviewHolder ph) {
            if (!allowed(p, ph.mode, ph.target)) return;
            handlePreview(p, ph, e.getSlot());
        }
    }

    private boolean allowed(Player p, Menus.Mode mode, UUID target) {
        boolean ok = switch (mode) {
            case ADMIN -> p.hasPermission("deathbackup.admin");
            case VERIFY -> p.hasPermission(DeathBackupPlugin.PERM_VERIFY);
            case CLAIM -> p.getUniqueId().equals(target);
        };
        if (!ok) {
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
        }
        return ok;
    }

    // ------------------------------------------------------------ /nadajbackup i /odbierzbackup

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
                grantOrRequest(p, h.target, h.targetName, entry);
            } else {
                return;
            }
            reopenList(p, h);
        } else if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
            plugin.manager().claim(p, entry);
            reopenList(p, h);
        }
    }

    private void reopenList(Player p, Menus.ListHolder h) {
        Bukkit.getScheduler().runTask(plugin,
                () -> Menus.openList(plugin, p, h.target, h.targetName, h.mode));
    }

    /** Admin z bypass nadaje od razu, pozostali wysylaja backup do weryfikacji. */
    private void grantOrRequest(Player admin, UUID target, String name, BackupEntry entry) {
        if (admin.hasPermission(DeathBackupPlugin.PERM_BYPASS)) {
            plugin.grantNow(target, name, entry, admin.getName(), "");
        } else if (entry.pendingVerify) {
            admin.sendMessage(DeathBackupPlugin.PREFIX + "§eTen backup juz czeka na weryfikacje.");
        } else {
            plugin.requestVerification(target, name, entry, admin.getName());
            admin.sendMessage(DeathBackupPlugin.PREFIX + "§aBackup wyslany do weryfikacji. §7Gracz §f" + name
                    + " §7odbierze go dopiero po potwierdzeniu przez administracje.");
        }
    }

    private void revoke(Player admin, UUID target, String name, BackupEntry entry) {
        entry.granted = false;
        entry.claimed = false;
        entry.pendingVerify = false;
        entry.grantedBy = "";
        entry.verifiedBy = "";
        plugin.manager().save(target);
        admin.sendMessage(DeathBackupPlugin.PREFIX + "§eCofnieto nadanie backupu dla §f" + name + "§e.");
        plugin.bars().refresh(target);
    }

    // ------------------------------------------------------------ /backup-verify

    private void handleVerifyList(Player p, Menus.ListHolder h, int slot, ClickType click) {
        if (slot < 0 || slot >= 27) return;
        long id = h.ids[slot];
        UUID owner = h.owners[slot];
        if (id == 0 || owner == null) return;
        String ownerName = h.ownerNames[slot];

        BackupEntry entry = plugin.manager().find(owner, id);
        if (entry == null || !entry.pendingVerify) {
            p.sendMessage(DeathBackupPlugin.PREFIX + "§eTen backup zostal juz obsluzony.");
            reopenVerify(p);
            return;
        }

        if (click == ClickType.SHIFT_LEFT) {
            Bukkit.getScheduler().runTask(plugin,
                    () -> Menus.openPreview(plugin, p, owner, ownerName, Menus.Mode.VERIFY, entry));
        } else if (click == ClickType.LEFT) {
            approve(p, owner, ownerName, entry);
            reopenVerify(p);
        } else if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            reject(p, owner, ownerName, entry);
            reopenVerify(p);
        }
    }

    private void reopenVerify(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> Menus.openVerify(plugin, p));
    }

    private void approve(Player p, UUID owner, String ownerName, BackupEntry entry) {
        if (!entry.pendingVerify) {
            p.sendMessage(DeathBackupPlugin.PREFIX + "§eTen backup zostal juz obsluzony.");
            return;
        }
        if (entry.grantedBy.equalsIgnoreCase(p.getName()) && !p.hasPermission(DeathBackupPlugin.PERM_BYPASS)) {
            p.sendMessage(DeathBackupPlugin.PREFIX + "§cNie mozesz potwierdzic backupu, ktory sam wystawiles.");
            return;
        }
        plugin.grantNow(owner, ownerName, entry, entry.grantedBy, p.getName());
        p.sendMessage(DeathBackupPlugin.PREFIX + "§aPotwierdzono backup gracza §f" + ownerName + "§a.");
    }

    private void reject(Player p, UUID owner, String ownerName, BackupEntry entry) {
        if (!entry.pendingVerify) {
            p.sendMessage(DeathBackupPlugin.PREFIX + "§eTen backup zostal juz obsluzony.");
            return;
        }
        plugin.rejectRequest(owner, ownerName, entry, p.getName());
        p.sendMessage(DeathBackupPlugin.PREFIX + "§cOdrzucono backup gracza §f" + ownerName + "§c.");
    }

    // ------------------------------------------------------------ podglad

    private void handlePreview(Player p, Menus.PreviewHolder h, int slot) {
        if (slot == Menus.SLOT_BACK) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (h.mode == Menus.Mode.VERIFY) Menus.openVerify(plugin, p);
                else Menus.openList(plugin, p, h.target, h.targetName, h.mode);
            });
            return;
        }

        boolean confirm = slot == Menus.SLOT_ACTION;
        boolean rejectBtn = slot == Menus.SLOT_REJECT && h.mode == Menus.Mode.VERIFY;
        if (!confirm && !rejectBtn) return;

        BackupEntry entry = plugin.manager().find(h.target, h.entryId);
        if (entry == null) return;

        switch (h.mode) {
            case VERIFY -> {
                if (confirm) approve(p, h.target, h.targetName, entry);
                else reject(p, h.target, h.targetName, entry);
                reopenVerify(p);
                return;
            }
            case ADMIN -> grantOrRequest(p, h.target, h.targetName, entry);
            case CLAIM -> plugin.manager().claim(p, entry);
        }
        Bukkit.getScheduler().runTask(plugin,
                () -> Menus.openList(plugin, p, h.target, h.targetName, h.mode));
    }
}
