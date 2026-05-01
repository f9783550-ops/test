package pl.pcraft.chowany;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;

public class EditCommand implements CommandExecutor, Listener {

    private final ChowanyPlugin plugin;
    private static final String ADMIN = "Pcraft600";

    public EditCommand(ChowanyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tylko gracz!");
            return true;
        }
        Player p = (Player) sender;
        if (!p.getName().equalsIgnoreCase(ADMIN)) {
            p.sendMessage(ChatColor.RED + "Tylko admin moze edytowac bloki!");
            return true;
        }

        // Dawanie Mikstury Edycji
        ItemStack potion = new ItemStack(Material.POTION);
        ItemMeta meta = potion.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Mikstura Edycji");
        meta.setLore(List.of(ChatColor.GRAY + "Prawy klik = edytuj bloki"));
        potion.setItemMeta(meta);
        p.getInventory().addItem(potion);
        p.sendMessage(ChatColor.GREEN + "Masz Miksture Edycji! Prawy klik = edytuj bloki.");
        return true;
    }

    @EventHandler
    public void onEditPotionClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getItem() == null || e.getItem().getType() != Material.POTION) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!p.getName().equalsIgnoreCase(ADMIN)) return;

        ItemMeta meta = e.getItem().getItemMeta();
        if (meta == null || !meta.getDisplayName().contains("Edycji")) return;

        e.setCancelled(true);
        openBlockEditor(p);
    }

    public void openBlockEditor(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, ChatColor.GOLD + "Edycja blokow");
        for (Material m : plugin.getBlockList()) {
            if (m != null && m.isBlock()) {
                inv.addItem(new ItemStack(m));
            }
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onEditorClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (!e.getView().getTitle().equals(ChatColor.GOLD + "Edycja blokow")) return;
        if (!p.getName().equalsIgnoreCase(ADMIN)) {
            e.setCancelled(true);
            return;
        }
        // Admin moze dowolnie wyciagac/wkladac bloki
    }

    @EventHandler
    public void onEditorClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        if (!e.getView().getTitle().equals(ChatColor.GOLD + "Edycja blokow")) return;
        if (!p.getName().equalsIgnoreCase(ADMIN)) return;

        List<Material> newBlocks = new ArrayList<>();
        for (ItemStack item : e.getInventory().getContents()) {
            if (item != null && item.getType().isBlock()) {
                newBlocks.add(item.getType());
            }
        }
        if (!newBlocks.isEmpty()) {
            plugin.setBlockList(newBlocks);
            p.sendMessage(ChatColor.GREEN + "Lista blokow zaktualizowana! (" + newBlocks.size() + " blokow)");
        } else {
            p.sendMessage(ChatColor.RED + "Musi zostac co najmniej 1 blok!");
        }
    }
}
