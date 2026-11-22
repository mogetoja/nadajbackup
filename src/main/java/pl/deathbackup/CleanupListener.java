package pl.deathbackup;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Dokancza usuwanie oryginalnych itemow u graczy offline i w niezaladowanych chunkach. */
public class CleanupListener implements Listener {

    private final DeathBackupPlugin plugin;

    public CleanupListener(DeathBackupPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        ItemCleaner cleaner = plugin.cleaner();
        if (!cleaner.hasPending()) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) cleaner.cleanPlayer(p, cleaner.pendingIds());
        }, 20L);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        ItemCleaner cleaner = plugin.cleaner();
        if (e.isNewChunk() || !cleaner.hasPending()) return;
        Chunk chunk = e.getChunk();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded()) cleaner.cleanChunk(chunk, cleaner.pendingIds());
        });
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        ItemCleaner cleaner = plugin.cleaner();
        if (!cleaner.hasPending()) return;
        List<Entity> entities = new ArrayList<>(e.getEntities());
        Bukkit.getScheduler().runTask(plugin, () -> {
            Set<Long> ids = cleaner.pendingIds();
            cleaner.cleanEntities(entities, ids);
        });
    }
}
