package pl.deathbackup;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class DeathBackupPlugin extends JavaPlugin implements TabExecutor {

    public static final String PREFIX = "§8[§cBackup§8] §r";

    /** Widzi /backup-verify, potwierdza i odrzuca backupy. */
    public static final String PERM_VERIFY = "komendy.backup.verify";
    /** Backupy nadawane przez te osobe nie wymagaja weryfikacji. */
    public static final String PERM_BYPASS = "komendy.backup.verify.bypass";
    /** Widzi na czacie informacje o nadaniach, odbiorach i weryfikacjach backupow. */
    public static final String PERM_ALERTS = "komendy.backup.alerts";

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
        Bukkit.getPluginManager().registerEvents(new VerifyListener(this), this);

        // gracze online przy /reload
        for (Player p : Bukkit.getOnlinePlayers()) {
            bars.refresh(p.getUniqueId());
        }

        getCommand("nadajbackup").setExecutor(this);
        getCommand("nadajbackup").setTabCompleter(this);
        getCommand("odbierzbackup").setExecutor(this);
        getCommand("backup-verify").setExecutor(this);

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

    // ------------------------------------------------------------ nadawanie / weryfikacja

    /** Nadaje backup od razu (admin z bypass albo po potwierdzeniu). */
    public void grantNow(UUID target, String targetName, BackupEntry entry, String requester, String verifier) {
        entry.granted = true;
        entry.claimed = false;
        entry.pendingVerify = false;
        entry.grantedBy = requester == null ? "" : requester;
        entry.verifiedBy = verifier == null ? "" : verifier;
        manager.save(target);

        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            online.sendMessage(PREFIX + "§fAdministrator §c" + entry.grantedBy
                    + " §fnadal Ci backup. Wpisz §e/odbierzbackup §faby go odebrac.");
        }
        bars.refresh(target);
        announceGrant(entry.grantedBy, targetName, entry, entry.verifiedBy);
    }

    /** Admin bez bypass: backup czeka na potwierdzenie, gracz jeszcze nie moze go odebrac. */
    public void requestVerification(UUID target, String targetName, BackupEntry entry, String requester) {
        entry.pendingVerify = true;
        entry.granted = false;
        entry.claimed = false;
        entry.grantedBy = requester;
        entry.verifiedBy = "";
        manager.save(target);
        bars.refresh(target);
        announcePending(requester, targetName, entry);
    }

    /** Odrzucenie backupu czekajacego na weryfikacje. */
    public void rejectRequest(UUID target, String targetName, BackupEntry entry, String verifier) {
        String requester = entry.grantedBy;
        entry.pendingVerify = false;
        entry.granted = false;
        entry.grantedBy = "";
        entry.verifiedBy = "";
        manager.save(target);

        alerts(PREFIX + "§c" + verifier + " §fodrzucil backup gracza §a" + targetName
                + " §7(wystawil: §f" + (requester == null || requester.isBlank() ? "?" : requester) + "§7)§f.");

        if (requester != null && !requester.isBlank() && !requester.equalsIgnoreCase(verifier)) {
            Player r = Bukkit.getPlayerExact(requester);
            if (r != null && !r.hasPermission(PERM_ALERTS)) {
                r.sendMessage(PREFIX + "§cTwoj backup dla gracza §a" + targetName + " §czostal odrzucony przez §f"
                        + verifier + "§c.");
            }
        }
        DiscordLog.rejected(this, entry, targetName, requester, verifier);
    }

    // ------------------------------------------------------------ powiadomienia

    /** Wiadomosc tylko dla osob z komendy.backup.alerts (i konsoli). */
    private void alerts(String msg) {
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (pl.hasPermission(PERM_ALERTS)) pl.sendMessage(msg);
        }
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    public void announceGrant(String admin, String player, BackupEntry entry, String verifier) {
        String extra = verifier == null || verifier.isBlank() ? "" : " §7(zweryfikowal: §f" + verifier + "§7)";
        alerts(PREFIX + "§fAdministrator §c" + admin + " §fnadal backupa dla gracza §a" + player + "§f." + extra);
        DiscordLog.grant(this, entry, player, admin, verifier);
    }

    public void announceClaim(String player, String admin, BackupEntry entry) {
        String by = (admin == null || admin.isBlank()) ? "?" : admin;
        alerts(PREFIX + "§fGracz §a" + player + " §fodebral backup nadany przez administratora §c" + by + "§f.");
        DiscordLog.claim(this, entry, player, admin);
    }

    /** Backup czeka na weryfikacje: osoby z komendy.backup.verify dostaja klikalna wiadomosc. */
    public void announcePending(String requester, String player, BackupEntry entry) {
        String msg = PREFIX + "§eAdministrator §c" + requester + " §ewystawil backup dla gracza §a" + player
                + " §edo weryfikacji.";
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (pl.hasPermission(PERM_VERIFY)) sendClickable(pl, msg + " ", "§a§l[OTWORZ /backup-verify]");
            else if (pl.hasPermission(PERM_ALERTS)) pl.sendMessage(msg);
        }
        Bukkit.getConsoleSender().sendMessage(msg);
        DiscordLog.pending(this, entry, player, requester);
    }

    /** Wiadomosc z przyciskiem, ktory uruchamia /backup-verify. */
    public void sendClickable(Player p, String text, String button) {
        List<BaseComponent> parts = new ArrayList<>(Arrays.asList(TextComponent.fromLegacyText(text)));
        for (BaseComponent b : TextComponent.fromLegacyText(button)) {
            b.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/backup-verify"));
            parts.add(b);
        }
        p.spigot().sendMessage(parts.toArray(new BaseComponent[0]));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Tylko dla graczy.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("backup-verify")) {
            if (!p.hasPermission(PERM_VERIFY)) {
                p.sendMessage(PREFIX + "§cBrak uprawnien.");
                return true;
            }
            Menus.openVerify(this, p);
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
