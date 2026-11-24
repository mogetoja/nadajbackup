package pl.deathbackup;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Budowanie menu (lista backupow + podglad zawartosci). */
public final class Menus {

    public enum Mode { ADMIN, CLAIM, VERIFY }

    public static final int SLOT_BACK = 45;
    public static final int SLOT_ACTION = 53;
    public static final int SLOT_REJECT = 52;

    private Menus() {
    }

    public static final class ListHolder implements InventoryHolder {
        public final UUID target;
        public final String targetName;
        public final Mode mode;
        public final long[] ids = new long[54];
        public final UUID[] owners = new UUID[54];
        public final String[] ownerNames = new String[54];
        private Inventory inv;

        ListHolder(UUID target, String targetName, Mode mode) {
            this.target = target;
            this.targetName = targetName;
            this.mode = mode;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public static final class PreviewHolder implements InventoryHolder {
        public final UUID target;
        public final String targetName;
        public final Mode mode;
        public final long entryId;
        private Inventory inv;

        PreviewHolder(UUID target, String targetName, Mode mode, long entryId) {
            this.target = target;
            this.targetName = targetName;
            this.mode = mode;
            this.entryId = entryId;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    // ------------------------------------------------------------------ lista

    public static void openList(DeathBackupPlugin plugin, Player viewer, UUID target, String targetName, Mode mode) {
        List<BackupEntry> shown = new ArrayList<>();
        for (BackupEntry e : plugin.manager().get(target)) {
            if (mode == Mode.ADMIN || (e.granted && !e.claimed)) {
                shown.add(e);
            }
        }

        String title = mode == Mode.ADMIN
                ? "§8Backupy: §c" + targetName + " §8(" + shown.size() + "/" + BackupManager.MAX + ")"
                : "§8Twoje backupy do odbioru";

        ListHolder holder = new ListHolder(target, targetName, mode);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.inv = inv;

        for (int i = 0; i < shown.size() && i < BackupManager.MAX; i++) {
            BackupEntry e = shown.get(i);
            holder.ids[i] = e.id;
            inv.setItem(i, icon(e, mode, viewer.hasPermission(DeathBackupPlugin.PERM_BYPASS)));
        }

        if (shown.isEmpty()) {
            inv.setItem(22, named(Material.BARRIER, "§cBrak backupow",
                    mode == Mode.ADMIN
                            ? "§7Ten gracz nie ma zapisanych smierci."
                            : "§7Administrator nie nadal Ci jeszcze zadnego backupu."));
        }

        ItemStack filler = named(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int s = 50; s <= 52; s++) inv.setItem(s, filler);

        inv.setItem(53, named(Material.BOOK, "§eInformacje",
                "§7Zapisujemy ostatnie §f" + BackupManager.MAX + " §7smierci.",
                "§7Najnowsza smierc jest na poczatku.",
                " ",
                mode == Mode.ADMIN ? "§aZielone §7= nadane graczowi" : "§aKliknij §7aby odebrac backup",
                mode == Mode.ADMIN ? "§8Szare §7= juz odebrane" : "§7Najpierw zrob miejsce w ekwipunku",
                mode == Mode.ADMIN ? "§eZolte §7= czeka na weryfikacje" : " "));

        viewer.openInventory(inv);
    }

    private static ItemStack icon(BackupEntry e, Mode mode, boolean bypass) {
        Material mat;
        String status;
        if (e.claimed) {
            mat = Material.GRAY_SHULKER_BOX;
            status = "§8Odebrany przez gracza";
        } else if (e.pendingVerify) {
            mat = Material.YELLOW_SHULKER_BOX;
            status = "§eCzeka na weryfikacje" + (e.grantedBy.isBlank() ? "" : " §7(wystawil: §f" + e.grantedBy + "§7)");
        } else if (e.granted) {
            mat = Material.LIME_SHULKER_BOX;
            status = "§aNadany - czeka na odbior";
        } else {
            mat = Material.CHEST;
            status = "§7Nie nadany";
        }
        if (mode == Mode.CLAIM) mat = Material.LIME_SHULKER_BOX;

        List<String> lore = new ArrayList<>();
        lore.add("§7Przyczyna: §f" + e.cause);
        lore.add("§7Miejsce: §f" + e.world + " " + (int) e.x + ", " + (int) e.y + ", " + (int) e.z);
        lore.add("§7Przedmioty: §f" + e.stackCount() + " slotow §8(" + e.itemCount() + " szt.)");
        lore.add("§7XP: §f" + XpUtil.levelFor(e.xp) + " lvl §8(" + e.xp + " pkt)");
        if (mode == Mode.ADMIN) {
            lore.add("§7Status: " + status);
        }
        lore.add(" ");
        if (mode == Mode.ADMIN) {
            lore.add(bypass ? "§eLPM §7- nadaj graczowi" : "§eLPM §7- wyslij do weryfikacji");
            lore.add("§eShift+LPM §7- cofnij nadanie");
            lore.add("§ePPM §7- podglad zawartosci");
        } else {
            lore.add("§eLPM §7- odbierz");
            lore.add("§ePPM §7- podglad zawartosci");
        }

        return named(mat, "§6" + formatDate(e.time), lore.toArray(new String[0]));
    }

    // ------------------------------------------------------------ weryfikacja

    /** Male okienko (jak pojedyncza skrzynia) z backupami czekajacymi na potwierdzenie. */
    public static void openVerify(DeathBackupPlugin plugin, Player viewer) {
        List<BackupManager.PendingRef> list = plugin.manager().allPending();

        ListHolder holder = new ListHolder(null, "", Mode.VERIFY);
        Inventory inv = Bukkit.createInventory(holder, 27, "§8Do weryfikacji §7(" + list.size() + ")");
        holder.inv = inv;

        for (int i = 0; i < list.size() && i < 27; i++) {
            BackupManager.PendingRef r = list.get(i);
            BackupEntry e = r.entry();
            holder.ids[i] = e.id;
            holder.owners[i] = r.owner();
            holder.ownerNames[i] = r.ownerName();

            inv.setItem(i, named(Material.YELLOW_SHULKER_BOX, "§6" + r.ownerName() + " §8- §7" + formatDate(e.time),
                    "§7Gracz: §f" + r.ownerName(),
                    "§7Wystawil: §f" + (e.grantedBy.isBlank() ? "?" : e.grantedBy),
                    "§7Przyczyna: §f" + e.cause,
                    "§7Miejsce: §f" + e.world + " " + (int) e.x + ", " + (int) e.y + ", " + (int) e.z,
                    "§7Przedmioty: §f" + e.stackCount() + " slotow §8(" + e.itemCount() + " szt.)",
                    "§7XP: §f" + XpUtil.levelFor(e.xp) + " lvl §8(" + e.xp + " pkt)",
                    " ",
                    "§aLPM §7- potwierdz",
                    "§cPPM §7- odrzuc",
                    "§eShift+LPM §7- podglad zawartosci"));
        }

        if (list.isEmpty()) {
            inv.setItem(13, named(Material.BARRIER, "§cBrak backupow do weryfikacji"));
        }

        viewer.openInventory(inv);
    }

    // ----------------------------------------------------------------- podglad

    public static void openPreview(DeathBackupPlugin plugin, Player viewer, UUID target, String targetName,
                                   Mode mode, BackupEntry e) {
        PreviewHolder holder = new PreviewHolder(target, targetName, mode, e.id);
        Inventory inv = Bukkit.createInventory(holder, 54, "§8Podglad: §6" + formatDate(e.time));
        holder.inv = inv;

        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = 36; s < 54; s++) inv.setItem(s, filler);

        for (var en : e.items.entrySet()) {
            int idx = en.getKey();
            ItemStack it = en.getValue().clone();
            if (idx >= 9 && idx <= 35) {
                inv.setItem(idx - 9, it);          // gorne 3 rzedy
            } else if (idx >= 0 && idx <= 8) {
                inv.setItem(27 + idx, it);         // hotbar
            } else if (idx == 39) {
                inv.setItem(36, it);               // helm
            } else if (idx == 38) {
                inv.setItem(37, it);               // napiersnik
            } else if (idx == 37) {
                inv.setItem(38, it);               // spodnie
            } else if (idx == 36) {
                inv.setItem(39, it);               // buty
            } else if (idx == 40) {
                inv.setItem(41, it);               // druga reka
            }
        }

        inv.setItem(SLOT_BACK, named(Material.ARROW, "§eWroc do listy"));
        inv.setItem(49, named(Material.EXPERIENCE_BOTTLE, "§aDoswiadczenie",
                "§7Poziom: §f" + XpUtil.levelFor(e.xp),
                "§7Punkty: §f" + e.xp));
        if (mode == Mode.VERIFY) {
            inv.setItem(SLOT_ACTION, named(Material.LIME_DYE, "§aPotwierdz backup",
                    "§7Gracz §f" + targetName + " §7bedzie mogl go odebrac."));
            inv.setItem(SLOT_REJECT, named(Material.RED_DYE, "§cOdrzuc backup",
                    "§7Backup nie zostanie nadany."));
        } else if (mode == Mode.ADMIN) {
            boolean bypass = viewer.hasPermission(DeathBackupPlugin.PERM_BYPASS);
            inv.setItem(SLOT_ACTION, named(Material.LIME_DYE,
                    bypass ? "§aNadaj ten backup graczowi" : "§aWyslij backup do weryfikacji",
                    bypass ? "§7Gracz §f" + targetName + " §7odbierze go przez §e/odbierzbackup"
                            : "§7Gracz odbierze go dopiero po potwierdzeniu przez administracje."));
        } else {
            inv.setItem(SLOT_ACTION, named(Material.LIME_DYE, "§aOdbierz ten backup",
                    "§7Itemy i XP trafia do Twojego ekwipunku."));
        }

        viewer.openInventory(inv);
    }

    // ------------------------------------------------------------------ pomoc

    private static String formatDate(long t) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date(t));
    }

    private static ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) meta.setLore(List.of(lore));
            it.setItemMeta(meta);
        }
        return it;
    }
}
