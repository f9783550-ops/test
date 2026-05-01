package pl.pcraft.chowany;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

public class ChowanyPlugin extends JavaPlugin implements Listener {

    private boolean gameRunning = false;
    private Player seeker;
    private List<Player> helpers = new ArrayList<>();
    private List<Player> hiders = new ArrayList<>();
    private List<Player> spectators = new ArrayList<>();
    private BossBar bossBar;
    private int gameTime = 300;
    private HashMap<Player, Long> cooldowns = new HashMap<>();
    private HashMap<Player, Material> disguisedPlayers = new HashMap<>();
    private HashMap<Player, Location> deathLocations = new HashMap<>();
    private static final int CD = 30;
    private static final String ADMIN = "Pcraft600";
    private static final int SPECTATOR_LIMIT = 100;
    private List<Material> blockList = new ArrayList<>();
    private static final List<Material> DEFAULT_BLOCKS = Arrays.asList(
        Material.NETHERRACK, Material.NETHER_BRICKS, Material.CRIMSON_STEM,
        Material.WARPED_STEM, Material.CRIMSON_NYLIUM, Material.WARPED_NYLIUM,
        Material.NETHER_WART_BLOCK, Material.WARPED_WART_BLOCK, Material.BLACKSTONE
    );
    BukkitRunnable timerTask;

    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        blockList.addAll(DEFAULT_BLOCKS);
        EditCommand ec = new EditCommand(this);
        getServer().getPluginManager().registerEvents(ec, this);
        getLogger().info("Chowany Plugin - Arena Piekielna gotowa!");
    }

    public void onDisable() {
        if (gameRunning) stopGame();
    }

    public List<Material> getBlockList() { return blockList; }
    public void setBlockList(List<Material> list) { this.blockList = list; }

    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!l.equalsIgnoreCase("chowany")) return false;
        if (a.length == 0) {
            s.sendMessage(ChatColor.GOLD + "/chowany start - Rozpocznij gre");
            s.sendMessage(ChatColor.GOLD + "/chowany stop - Zakoncz gre");
            s.sendMessage(ChatColor.GOLD + "/chowany test - Testuj przemiane");
            s.sendMessage(ChatColor.GOLD + "/chowany edit - Edytuj bloki");
            return true;
        }
        if (a[0].equalsIgnoreCase("test")) {
            if (!(s instanceof Player)) return true;
            Player p = (Player) s;
            if (!p.getName().equalsIgnoreCase(ADMIN)) { p.sendMessage(ChatColor.RED + "Tylko admin!"); return true; }
            giveTransformPotion(p);
            p.sendMessage(ChatColor.GREEN + "Mikstura Przemiany! Prawy klik = wybierz blok.");
            return true;
        }
        if (a[0].equalsIgnoreCase("edit")) {
            if (!(s instanceof Player)) return true;
            Player p = (Player) s;
            if (!p.getName().equalsIgnoreCase(ADMIN)) { p.sendMessage(ChatColor.RED + "Tylko admin!"); return true; }
            EditCommand ec = new EditCommand(this);
            ec.onCommand(s, c, l, new String[]{});
            return true;
        }
        if (a[0].equalsIgnoreCase("start")) {
            if (gameRunning) { s.sendMessage(ChatColor.RED + "Gra juz trwa!"); return true; }
            List<Player> pl = new ArrayList<>(getServer().getOnlinePlayers());
            pl.removeIf(x -> x.getName().equalsIgnoreCase(ADMIN));
            if (pl.size() < 3) { s.sendMessage(ChatColor.RED + "Minimum 3 graczy!"); return true; }
            startGame(pl);
            return true;
        }
        if (a[0].equalsIgnoreCase("stop")) {
            if (!gameRunning) { s.sendMessage(ChatColor.RED + "Gra nie trwa!"); return true; }
            stopGame();
            s.sendMessage(ChatColor.GREEN + "Gra zatrzymana!");
            return true;
        }
        return false;
    }

    void startGame(List<Player> pl) {
        gameRunning = true;
        helpers.clear(); hiders.clear(); spectators.clear();
        disguisedPlayers.clear(); deathLocations.clear(); cooldowns.clear();
        Collections.shuffle(pl);
        seeker = pl.get(0);
        hiders.addAll(pl.subList(1, pl.size()));

        bossBar = BossBar.bossBar(Component.text("Czas: 5:00", NamedTextColor.GREEN), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        pl.forEach(p -> { p.showBossBar(bossBar); p.getInventory().clear(); });

        // Szukajacy zamrozony na 1 minute, chowajacy dostaja Speed II
        seeker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 1200, 100));
        seeker.sendMessage(ChatColor.RED + "Poczekaj 1 minute az chowajacy sie ukryja...");
        hiders.forEach(h -> {
            h.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 1200, 1));
            giveTransformPotion(h);
            h.sendMessage(ChatColor.GREEN + "Uciekaj! Masz 1 minute na ukrycie sie!");
        });
        Player ad = getServer().getPlayer(ADMIN);
        if (ad != null) { ad.getInventory().clear(); giveTransformPotion(ad); }

        new BukkitRunnable() {
            public void run() {
                seeker.removePotionEffect(PotionEffectType.SLOWNESS);
                hiders.forEach(h -> h.removePotionEffect(PotionEffectType.SPEED));
                giveSeekerItems(seeker);
                seeker.showTitle(Title.title(Component.text("RUSZAJ!", NamedTextColor.RED), Component.empty()));
                startTimer(pl);
            }
        }.runTaskLater(this, 1200); // 1 minuta
    }

    void startTimer(List<Player> pl) {
        timerTask = new BukkitRunnable() {
            int t = gameTime;
            public void run() {
                if (t <= 0) {
                    pl.forEach(p -> { p.sendMessage(ChatColor.GREEN + "Koniec czasu! Chowajacy wygrywaja!"); p.hideBossBar(bossBar); });
                    stopGame(); cancel(); return;
                }
                int m = t / 60, s = t % 60;
                bossBar.name(Component.text("Czas: " + m + ":" + String.format("%02d", s), NamedTextColor.GREEN));
                bossBar.progress((float) t / gameTime);
                t--;
                // Sprawdzanie spectatorow
                for (Player sp : new ArrayList<>(spectators)) {
                    if (deathLocations.containsKey(sp)) {
                        Location death = deathLocations.get(sp);
                        if (sp.getLocation().distance(death) > SPECTATOR_LIMIT) {
                            sp.teleport(death);
                            sp.sendMessage(ChatColor.RED + "Nie mozesz oddalic sie wiecej niz 100 kratek!");
                        }
                    }
                }
                // Sprawdzanie czy zostal tylko szukajacy + 1 pomocnik
                if (hiders.isEmpty() && helpers.size() <= 1) {
                    pl.forEach(p -> { p.sendMessage(ChatColor.GOLD + "Wszyscy znalezieni! Szukajacy wygrywa!"); p.hideBossBar(bossBar); });
                    stopGame(); cancel();
                }
            }
        };
        timerTask.runTaskTimer(this, 0, 20);
    }

    void stopGame() {
        gameRunning = false;
        if (timerTask != null) timerTask.cancel();
        if (bossBar != null) getServer().getOnlinePlayers().forEach(p -> p.hideBossBar(bossBar));
        getServer().getOnlinePlayers().forEach(p -> {
            if (disguisedPlayers.containsKey(p)) undisguise(p);
            p.getInventory().clear();
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.SPEED);
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            p.removePotionEffect(PotionEffectType.GLOWING);
            p.setGameMode(GameMode.SURVIVAL);
            p.setWalkSpeed(0.2f);
        });
        helpers.clear(); hiders.clear(); spectators.clear();
        disguisedPlayers.clear(); deathLocations.clear(); cooldowns.clear();
        seeker = null;
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if (!gameRunning || !(e.getDamager() instanceof Player) || !(e.getEntity() instanceof Player)) return;
        Player d = (Player) e.getDamager(), t = (Player) e.getEntity();
        if ((!d.equals(seeker) && !helpers.contains(d)) || !hiders.contains(t)) return;
        e.setCancelled(true);
        find(t);
    }

    void find(Player h) {
        hiders.remove(h);
        if (disguisedPlayers.containsKey(h)) undisguise(h);
        deathLocations.put(h, h.getLocation());
        if (helpers.isEmpty()) {
            helpers.add(h);
            h.sendMessage(ChatColor.GOLD + "Zostales POMOCNIKIEM! Pomoz szukajacemu!");
            h.getInventory().clear();
            h.setWalkSpeed(0.2f);
        } else {
            spectators.add(h);
            h.setGameMode(GameMode.SPECTATOR);
            h.sendMessage(ChatColor.GRAY + "Jestes Spectatorem. Nie oddalaj sie wiecej niz 100 kratek!");
        }
        getServer().getOnlinePlayers().forEach(p -> p.sendMessage(ChatColor.YELLOW + h.getName() + " znaleziony! (" + hiders.size() + " pozostalo)"));
    }

    void giveTransformPotion(Player p) {
        ItemStack potion = new ItemStack(Material.POTION);
        ItemMeta meta = potion.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Mikstura Przemiany");
        meta.setLore(List.of(ChatColor.GRAY + "Prawy klik = wybierz blok do przemiany"));
        potion.setItemMeta(meta);
        p.getInventory().addItem(potion);
    }

    void giveSeekerItems(Player p) {
        p.getInventory().clear();
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta sm = sword.getItemMeta();
        sm.setDisplayName(ChatColor.RED + "Miecz Szukajacego");
        sword.setItemMeta(sm);
        p.getInventory().addItem(sword);
        ItemStack gui = new ItemStack(Material.CHEST);
        ItemMeta gm = gui.getItemMeta();
        gm.setDisplayName(ChatColor.AQUA + "Wspomagacze");
        gui.setItemMeta(gm);
        p.getInventory().addItem(gui);
    }

    void openGUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, Component.text("Wybierz blok", NamedTextColor.DARK_PURPLE));
        for (Material m : blockList) {
            if (m != null && m.isBlock()) inv.addItem(new ItemStack(m));
        }
        p.openInventory(inv);
    }

    void openHelp(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, Component.text("Wspomagacze", NamedTextColor.RED));
        ItemStack f = new ItemStack(Material.FIREWORK_ROCKET);
        ItemMeta fm = f.getItemMeta();
        fm.setDisplayName(ChatColor.GOLD + "Fajerwerka");
        f.setItemMeta(fm);
        inv.setItem(3, f);
        ItemStack a = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta am = a.getItemMeta();
        am.setDisplayName(ChatColor.AQUA + "Strzala Widma");
        a.setItemMeta(am);
        inv.setItem(5, a);
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        String t = e.getView().title().toString();
        if (t.contains("Wybierz blok")) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null) {
                disguise(p, e.getCurrentItem().getType());
                p.closeInventory();
            }
        }
        if (t.contains("Wspomagacze")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;
            if (cooldowns.containsKey(p)) {
                long left = CD - ((System.currentTimeMillis() - cooldowns.get(p)) / 1000);
                if (left > 0) { p.sendMessage(ChatColor.RED + "Poczekaj " + left + "s!"); p.closeInventory(); return; }
            }
            if (e.getCurrentItem().getType() == Material.FIREWORK_ROCKET) {
                if (hiders.isEmpty()) { p.sendMessage(ChatColor.RED + "Brak chowajacych!"); p.closeInventory(); return; }
                Player tgt = hiders.get(new Random().nextInt(hiders.size()));
                tgt.getWorld().spawnParticle(Particle.FIREWORK, tgt.getLocation().add(0, 1, 0), 50, 0.5, 1.5, 0.5, 0.1);
                p.sendMessage(ChatColor.GOLD + "Fajerwerka nad " + tgt.getName() + "!");
                cooldowns.put(p, System.currentTimeMillis());
                p.closeInventory();
            }
            if (e.getCurrentItem().getType() == Material.SPECTRAL_ARROW) {
                hiders.forEach(h -> h.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 300, 1)));
                p.sendMessage(ChatColor.AQUA + "Chowajacy podswietleni na 15s!");
                cooldowns.put(p, System.currentTimeMillis());
                p.closeInventory();
            }
        }
    }

    @EventHandler
    public void onPotionClick(PlayerInteractEvent e) {
        if (e.getItem() == null || e.getItem().getType() != Material.POTION) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        ItemMeta meta = e.getItem().getItemMeta();
        if (meta == null) return;

        // Sprawdzenie czy to Mikstura Przemiany (a nie Edycji)
        if (meta.getDisplayName().contains("Przemiany")) {
            if (gameRunning && hiders.contains(p)) {
                openGUI(p);
            } else if (p.getName().equalsIgnoreCase(ADMIN)) {
                openGUI(p);
            }
        }
    }

    @EventHandler
    public void onChestClick(PlayerInteractEvent e) {
        if (e.getItem() == null || e.getItem().getType() != Material.CHEST) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (gameRunning && (p.equals(seeker) || helpers.contains(p))) openHelp(p);
    }

    void disguise(Player p, Material mat) {
        undisguise(p);
        disguisedPlayers.put(p, mat);
        // Stawiamy blok dokladnie tam gdzie gracz
        p.getWorld().getBlockAt(p.getLocation()).setType(mat);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 100, false, false));
        p.sendMessage(ChatColor.GREEN + "Przemieniles sie w " + mat.name() + ". Skocz by wrocic.");
    }

    void undisguise(Player p) {
        if (disguisedPlayers.containsKey(p)) {
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.getWorld().getBlockAt(p.getLocation()).setType(Material.AIR);
            disguisedPlayers.remove(p);
            p.setWalkSpeed(0.2f);
        }
    }

    @EventHandler
    public void onJump(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!disguisedPlayers.containsKey(p) || p.isOnGround() || !(p.getVelocity().getY() > 0)) return;
        undisguise(p);
        p.sendMessage(ChatColor.YELLOW + "Skoczyles! Koniec kamuflazu.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (gameRunning && bossBar != null) e.getPlayer().showBossBar(bossBar);
    }
}
