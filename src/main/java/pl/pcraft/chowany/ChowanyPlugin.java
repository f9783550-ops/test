package pl.pcraft.chowany;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.*;
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
    private static final int CD = 30;
    private static final String ADMIN = "Pcraft600";
    private static final Material[] BLOCKS = {
        Material.NETHERRACK,
        Material.NETHER_BRICKS,
        Material.CRIMSON_STEM,
        Material.WARPED_STEM,
        Material.CRIMSON_NYLIUM,
        Material.WARPED_NYLIUM,
        Material.NETHER_WART_BLOCK,
        Material.WARPED_WART_BLOCK,
        Material.BLACKSTONE
    };
    BukkitRunnable timerTask;

    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Chowany Plugin - Arena Piekielna gotowa!");
    }

    public void onDisable() {
        if (gameRunning) stopGame();
    }

    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!l.equalsIgnoreCase("chowany")) return false;
        if (a.length == 0) {
            s.sendMessage(ChatColor.GOLD + "/chowany start - Rozpocznij gre");
            s.sendMessage(ChatColor.GOLD + "/chowany stop - Zakoncz gre");
            s.sendMessage(ChatColor.GOLD + "/chowany test - Testuj przemiane");
            return true;
        }
        if (a[0].equalsIgnoreCase("test")) {
            if (!(s instanceof Player)) return true;
            Player p = (Player) s;
            if (!p.getName().equalsIgnoreCase(ADMIN)) {
                p.sendMessage(ChatColor.RED + "Tylko admin!");
                return true;
            }
            giveCompass(p);
            p.sendMessage(ChatColor.GREEN + "Kompas testowy! Skacz by wrocic.");
            return true;
        }
        if (a[0].equalsIgnoreCase("start")) {
            if (gameRunning) {
                s.sendMessage(ChatColor.RED + "Gra juz trwa!");
                return true;
            }
            List<Player> pl = new ArrayList<>(getServer().getOnlinePlayers());
            pl.removeIf(x -> x.getName().equalsIgnoreCase(ADMIN));
            if (pl.size() < 3) {
                s.sendMessage(ChatColor.RED + "Minimum 3 graczy!");
                return true;
            }
            startGame(pl);
            return true;
        }
        if (a[0].equalsIgnoreCase("stop")) {
            if (!gameRunning) {
                s.sendMessage(ChatColor.RED + "Gra nie trwa!");
                return true;
            }
            stopGame();
            s.sendMessage(ChatColor.GREEN + "Gra zatrzymana!");
            return true;
        }
        return false;
    }

    void startGame(List<Player> pl) {
        gameRunning = true;
        helpers.clear();
        hiders.clear();
        spectators.clear();
        disguisedPlayers.clear();
        cooldowns.clear();
        Collections.shuffle(pl);
        seeker = pl.get(0);
        hiders.addAll(pl.subList(1, pl.size()));
        bossBar = BossBar.bossBar(Component.text("Czas: 5:00", NamedTextColor.GREEN), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        pl.forEach(p -> { p.showBossBar(bossBar); p.getInventory().clear(); });
        new BukkitRunnable() {
            int c = 5;
            public void run() {
                if (c == 0) {
                    pl.forEach(p -> p.showTitle(Title.title(Component.text("START!", NamedTextColor.GREEN), Component.text("Chowajcie sie!", NamedTextColor.YELLOW))));
                    seeker.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 300, 1));
                    seeker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 300, 100));
                    hiders.forEach(h -> {
                        h.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1));
                        h.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 300, 1));
                        giveCompass(h);
                    });
                    Player ad = getServer().getPlayer(ADMIN);
                    if (ad != null) { ad.getInventory().clear(); giveCompass(ad); }
                    new BukkitRunnable() {
                        public void run() {
                            seeker.removePotionEffect(PotionEffectType.BLINDNESS);
                            seeker.removePotionEffect(PotionEffectType.SLOWNESS);
                            giveSeekerItems(seeker);
                            hiders.forEach(h -> {
                                h.removePotionEffect(PotionEffectType.SPEED);
                                h.removePotionEffect(PotionEffectType.INVISIBILITY);
                            });
                        }
                    }.runTaskLater(ChowanyPlugin.this, 300);
                    startTimer(pl);
                    this.cancel();
                    return;
                }
                pl.forEach(p -> p.showTitle(Title.title(Component.text("" + c, NamedTextColor.RED), Component.empty())));
                c--;
            }
        }.runTaskTimer(this, 0, 20);
    }

    void startTimer(List<Player> pl) {
        timerTask = new BukkitRunnable() {
            int t = gameTime;
            public void run() {
                if (t <= 0) {
                    pl.forEach(p -> {
                        p.sendMessage(ChatColor.GREEN + "Koniec czasu! Chowajacy wygrywaja!");
                        p.hideBossBar(bossBar);
                    });
                    stopGame();
                    cancel();
                    return;
                }
                int m = t / 60, s = t % 60;
                bossBar.name(Component.text("Czas: " + m + ":" + String.format("%02d", s), NamedTextColor.GREEN));
                bossBar.progress((float) t / gameTime);
                t--;
            }
        };
        timerTask.runTaskTimer(this, 320, 20);
    }

    void stopGame() {
        gameRunning = false;
        if (timerTask != null) timerTask.cancel();
        if (bossBar != null) getServer().getOnlinePlayers().forEach(p -> p.hideBossBar(bossBar));
        getServer().getOnlinePlayers().forEach(p -> {
            undisguise(p);
            p.getInventory().clear();
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.SPEED);
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            p.removePotionEffect(PotionEffectType.GLOWING);
            p.setGameMode(GameMode.SURVIVAL);
        });
        helpers.clear(); hiders.clear(); spectators.clear(); disguisedPlayers.clear(); cooldowns.clear(); seeker = null;
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
        undisguise(h);
        if (helpers.size() < 2) {
            helpers.add(h);
            h.sendMessage(ChatColor.GOLD + "Zostales POMOCNIKIEM!");
            h.getInventory().clear();
        } else {
            spectators.add(h);
            h.setGameMode(GameMode.SPECTATOR);
        }
        getServer().getOnlinePlayers().forEach(p -> p.sendMessage(ChatColor.YELLOW + h.getName() + " znaleziony! (" + hiders.size() + ")"));
        if (hiders.isEmpty()) {
            getServer().getOnlinePlayers().forEach(p -> {
                p.sendMessage(ChatColor.RED + "SZUKAJACY WYGRYWAJA!");
                p.hideBossBar(bossBar);
            });
            stopGame();
        }
    }

    void giveCompass(Player p) {
        ItemStack i = new ItemStack(Material.COMPASS);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(ChatColor.LIGHT_PURPLE + "Przemiana w blok");
        i.setItemMeta(m);
        p.getInventory().addItem(i);
    }

    void giveSeekerItems(Player p) {
        p.getInventory().clear();
        ItemStack s = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta sm = s.getItemMeta();
        sm.setDisplayName(ChatColor.RED + "Miecz Szukajacego");
        s.setItemMeta(sm);
        p.getInventory().addItem(s);
        ItemStack g = new ItemStack(Material.CHEST);
        ItemMeta gm = g.getItemMeta();
        gm.setDisplayName(ChatColor.AQUA + "Wspomagacze");
        g.setItemMeta(gm);
        p.getInventory().addItem(g);
    }

    void openGUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, Component.text("Wybierz blok", NamedTextColor.DARK_PURPLE));
        for (Material m : BLOCKS) inv.addItem(new ItemStack(m));
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
                if (left > 0) {
                    p.sendMessage(ChatColor.RED + "Poczekaj " + left + "s!");
                    p.closeInventory();
                    return;
                }
            }
            if (e.getCurrentItem().getType() == Material.FIREWORK_ROCKET) {
                if (hiders.isEmpty()) {
                    p.sendMessage(ChatColor.RED + "Brak chowajacych!");
                    p.closeInventory();
                    return;
                }
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
    public void onCompass(PlayerInteractEvent e) {
        if (e.getItem() == null || e.getItem().getType() != Material.COMPASS) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (p.getName().equalsIgnoreCase(ADMIN) || (gameRunning && hiders.contains(p))) openGUI(p);
    }

    @EventHandler
    public void onChest(PlayerInteractEvent e) {
        if (e.getItem() == null || e.getItem().getType() != Material.CHEST) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (gameRunning && (p.equals(seeker) || helpers.contains(p))) openHelp(p);
    }

    void disguise(Player p, Material m) {
        undisguise(p);
        disguisedPlayers.put(p, m);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
        p.sendMessage(ChatColor.GREEN + "Przemiana w " + m.name() + ". Skocz by wrocic.");
    }

    void undisguise(Player p) {
        if (disguisedPlayers.containsKey(p)) {
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            disguisedPlayers.remove(p);
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
