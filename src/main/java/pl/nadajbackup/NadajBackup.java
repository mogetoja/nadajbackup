package pl.nadajbackup;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NadajBackup extends JavaPlugin implements TabExecutor {

    @Override
    public void onEnable() {
        if (getCommand("nadajbackup") != null) {
            getCommand("nadajbackup").setExecutor(this);
            getCommand("nadajbackup").setTabCompleter(this);
        }
        getLogger().info("NadajBackup wlaczony. /nadajbackup <nick> -> /ir restore <nick>");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("nadajbackup.use")) {
            sender.sendMessage("§cNie masz uprawnien do tej komendy.");
            return true;
        }

        // /ir restore otwiera menu (GUI) wiec musi byc wykonane przez gracza, nie przez konsole.
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cTa komende mozna uzyc tylko w grze.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cUzycie: /" + label + " <nick>");
            return true;
        }

        player.performCommand("ir restore " + args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player p : getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    result.add(p.getName());
                }
            }
        }
        return result;
    }
}
