package pl.deathbackup;

import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Jeden zapisany backup smierci gracza. */
public class BackupEntry {

    public final long id;
    public final long time;
    public final String world;
    public final double x, y, z;
    public final String cause;
    public final int xp;
    /** Klucz = indeks slotu z PlayerInventory#getContents (0-35 ekwipunek, 36-39 zbroja, 40 druga reka). */
    public final Map<Integer, ItemStack> items;

    /** Admin nadal ten backup graczowi do odbioru. */
    public boolean granted;
    /** Gracz juz odebral ten backup. */
    public boolean claimed;
    /** Nick administratora, ktory nadal backup. */
    public String grantedBy = "";
    /** Backup wystawiony do nadania, czeka na potwierdzenie przez osobe z komendy.backup.verify. */
    public boolean pendingVerify;
    /** Nick osoby, ktora potwierdzila backup. */
    public String verifiedBy = "";

    public BackupEntry(long id, long time, String world, double x, double y, double z,
                       String cause, int xp, Map<Integer, ItemStack> items,
                       boolean granted, boolean claimed) {
        this.id = id;
        this.time = time;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.cause = cause;
        this.xp = xp;
        this.items = items;
        this.granted = granted;
        this.claimed = claimed;
    }

    public int stackCount() {
        return items.size();
    }

    public int itemCount() {
        int sum = 0;
        for (ItemStack it : items.values()) {
            sum += it.getAmount();
        }
        return sum;
    }
}
