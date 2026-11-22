package pl.deathbackup;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class DeathBackupPlugin extends JavaPlugin implements TabExecutor {

    public static final String PREFIX = "§8[§cBackup§8] §r";

    private BackupManager manager;
    private BarManager bars;
    private ItemCleaner cleaner;
    private IconCache icons;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new BackupManager(this);
        bars = new BarManager(this);
        cleaner = new ItemCleaner(this);
        icons = new IconCache(this);

        Bukkit.getPluginManager().registerEvents(new DeathListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);
        Bukkit.getPluginManager().registerEvents(bars, this);
        Bukkit.getPluginManager().registerEvents(new CleanupListener(this), this);

        // gracze online przy /reload
        for (Player p : Bukkit.getOnlinePlayers()) {
            bars.refresh(p.getUniqueId());
        }

        getCommand("nadajbackup").setExecutor(this);
        getCommand("nadajbackup").setTabCompleter(this);
        getCommand("odbierzbackup").setExecutor(this);

        getLogger().info("DeathBackup wlaczony (limit " + BackupManager.MAX + " smierci na gracza).");
    }

    @Override
    public void onDisable() {
        if (bars != null) bars.removeAll();
    }

    public BackupManager manager() {
        return manager;
    }

    public BarManager bars() {
        return bars;
    }

    public ItemCleaner cleaner() {
        return cleaner;
    }

    public IconCache icons() {
        return icons;
    }

    /** Admin nadal backup graczowi: czat tylko dla osob z deathbackup.wiadomoscibc + embed na Discordzie. */
    public void announceGrant(String admin, String player, BackupEntry entry) {
        String msg = "§8[§cBackup§8] §fAdministrator §c" + admin + " §fnadal backupa dla gracza §a" + player + "§f.";
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (pl.hasPermission("deathbackup.wiadomoscibc")) pl.sendMessage(msg);
        }
        Bukkit.getConsoleSender().sendMessage(msg);
        DiscordLog.grant(this, entry, player, admin);
    }

    /** Gracz odebral backup: czat (jesli wlaczone) + embed na Discordzie. */
    public void announceClaim(String player, String admin, BackupEntry entry) {
        String by = (admin == null || admin.isBlank()) ? "" : admin;
        if (getConfig().getBoolean("chat-broadcast", true)) {
            Bukkit.broadcastMessage("§8[§cBackup§8] §fGracz §a" + player
                    + " §fodebral backup nadany przez administratora §c" + by + "§f.");
        }
        DiscordLog.claim(this, entry, player, admin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Tylko dla graczy.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("nadajbackup")) {
            if (!p.hasPermission("deathbackup.admin")) {
                p.sendMessage(PREFIX + "§cBrak uprawnien.");
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(PREFIX + "§cUzycie: /nadajbackup <gracz>");
                return true;
            }

            Player online = Bukkit.getPlayerExact(args[0]);
            UUID uuid;
            String name;
            if (online != null) {
                uuid = online.getUniqueId();
                name = online.getName();
            } else {
                OfflinePlayer off = Bukkit.getOfflinePlayer(args[0]);
                if (!manager.hasData(off.getUniqueId()) && !off.hasPlayedBefore()) {
                    p.sendMessage(PREFIX + "§cNie znaleziono gracza §f" + args[0] + "§c.");
                    return true;
                }
                uuid = off.getUniqueId();
                name = off.getName() != null ? off.getName() : args[0];
            }

            Menus.openList(this, p, uuid, name, Menus.Mode.ADMIN);
            return true;
        }

        // /odbierzbackup
        if (!p.hasPermission("deathbackup.claim")) {
            p.sendMessage(PREFIX + "§cBrak uprawnien.");
            return true;
        }
        Menus.openList(this, p, p.getUniqueId(), p.getName(), Menus.Mode.CLAIM);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1 || !sender.hasPermission("deathbackup.admin")) return out;

        String prefix = args[0].toLowerCase(Locale.ROOT);
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (pl.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(pl.getName());
        }
        for (OfflinePlayer off : Bukkit.getOfflinePlayers()) {
            String n = off.getName();
            if (n != null && out.size() < 30 && !out.contains(n)
                    && n.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(n);
            }
        }
        return out;
    }
}
