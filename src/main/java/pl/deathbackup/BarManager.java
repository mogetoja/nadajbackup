package pl.deathbackup;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Boss bar "Masz do odebrania backup" dla graczy, ktorzy maja nadany i nieodebrany backup. */
public class BarManager implements Listener {

    private static final String TITLE =
            "§a§lMasz do odebrania backup §r§7aby odebrac wpisz §e/odbierzbackup";

    private final DeathBackupPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public BarManager(DeathBackupPlugin plugin) {
        this.plugin = plugin;
    }

    /** Dodaje albo zdejmuje boss bar zaleznie od tego, czy gracz ma cos do odebrania. */
    public void refresh(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) {
            remove(id);
            return;
        }

        boolean pending = false;
        for (BackupEntry e : plugin.manager().get(id)) {
            if (e.granted && !e.claimed) {
                pending = true;
                break;
            }
        }

        if (pending) {
            if (!bars.containsKey(id)) {
                BossBar bar = Bukkit.createBossBar(TITLE, BarColor.GREEN, BarStyle.SOLID);
                bar.setProgress(1.0);
                bar.addPlayer(p);
                bars.put(id, bar);
            }
        } else {
            remove(id);
        }
    }

    private void remove(UUID id) {
        BossBar bar = bars.remove(id);
        if (bar != null) bar.removeAll();
    }

    public void removeAll() {
        for (BossBar bar : bars.values()) bar.removeAll();
        bars.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        refresh(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        remove(e.getPlayer().getUniqueId());
    }
}
