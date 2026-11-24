package pl.deathbackup;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Przypomina osobom z komendy.backup.verify o backupach czekajacych na weryfikacje. */
public class VerifyListener implements Listener {

    private final DeathBackupPlugin plugin;

    public VerifyListener(DeathBackupPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPermission(DeathBackupPlugin.PERM_VERIFY)) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            int count = plugin.manager().allPending().size();
            if (count > 0) {
                plugin.sendClickable(p, DeathBackupPlugin.PREFIX + "§eMasz §f" + count
                        + " §ebackupow do zweryfikowania. ", "§a§l[OTWORZ /backup-verify]");
            }
        }, 60L);
    }
}
