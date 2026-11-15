package pl.deathbackup;

import org.bukkit.entity.Player;

/** Przeliczanie XP (punkty <-> poziomy) wg wzorow Minecrafta. */
public final class XpUtil {

    private XpUtil() {
    }

    public static int totalForLevel(int l) {
        if (l <= 16) return l * l + 6 * l;
        if (l <= 31) return (int) (2.5 * l * l - 40.5 * l + 360);
        return (int) (4.5 * l * l - 162.5 * l + 2220);
    }

    public static int toNextLevel(int l) {
        if (l <= 15) return 2 * l + 7;
        if (l <= 30) return 5 * l - 38;
        return 9 * l - 158;
    }

    public static int getTotal(Player p) {
        int lvl = p.getLevel();
        return totalForLevel(lvl) + Math.round(p.getExp() * toNextLevel(lvl));
    }

    public static int levelFor(int total) {
        int l = 0;
        while (totalForLevel(l + 1) <= total) {
            l++;
        }
        return l;
    }
}
