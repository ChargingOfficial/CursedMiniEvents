package com.cursedminievents;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class CursedMiniEvents extends JavaPlugin implements Listener {
    private final Random random = new Random();
    private NamespacedKey appleKey;
    private NamespacedKey appleModelKey;
    private boolean luckyLeavesActive = false;
    private boolean appleDroppedThisEvent = false;
    private long eventEndsAt = 0L;
    private BukkitTask stopTask;
    private BukkitTask bossBarTask;
    private BossBar eventBossBar;

    @Override
    public void onEnable() {
        appleKey = new NamespacedKey(this, "apple_of_life");
        appleModelKey = new NamespacedKey(this, "apple_of_life");
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Cursed Mini Events 1.0.8 enabled.");
    }

    @Override
    public void onDisable() {
        if (stopTask != null) stopTask.cancel();
        if (bossBarTask != null) bossBarTask.cancel();
        if (eventBossBar != null) eventBossBar.removeAll();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cursedminievents.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("minievent")) {
            if (args.length == 0 || !args[0].equalsIgnoreCase("luckyleaves")) {
                sender.sendMessage("§eUsage: /minievent luckyleaves <start [minutes]|stop|status>");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§eUsage: /minievent luckyleaves <start [minutes]|stop|status>");
                return true;
            }
            switch (args[1].toLowerCase()) {
                case "start" -> {
                    int minutes = 10;
                    if (args.length >= 3) {
                        try { minutes = Math.max(1, Integer.parseInt(args[2])); }
                        catch (NumberFormatException ex) {
                            sender.sendMessage("§cMinutes must be a whole number.");
                            return true;
                        }
                    }
                    startLuckyLeaves(minutes);
                    Bukkit.broadcastMessage("§6§lLUCKY LEAVES §ehas started! Break any leaves for a chance at rare rewards. Duration: " + minutes + " minute(s).");
                }
                case "stop" -> {
                    if (!luckyLeavesActive) sender.sendMessage("§cLucky Leaves is not running.");
                    else {
                        stopLuckyLeaves();
                        Bukkit.broadcastMessage("§6§lLUCKY LEAVES §chas ended.");
                    }
                }
                case "status" -> {
                    if (!luckyLeavesActive) sender.sendMessage("§eLucky Leaves is currently inactive.");
                    else sender.sendMessage("§aLucky Leaves is active for about " + Math.max(0, (eventEndsAt - System.currentTimeMillis()) / 1000) + " seconds.");
                }
                default -> sender.sendMessage("§eUsage: /minievent luckyleaves <start [minutes]|stop|status>");
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("minievents")) {
            if (args.length == 2 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("apple")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can receive the Apple Of Life.");
                    return true;
                }
                player.getInventory().addItem(createAppleOfLife()).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                player.sendMessage("§dYou received the §5§lApple Of Life§d!");
            } else {
                sender.sendMessage("§eUsage: /minievents give apple");
            }
            return true;
        }
        return false;
    }

    private void startLuckyLeaves(int minutes) {
        if (stopTask != null) stopTask.cancel();
        luckyLeavesActive = true;
        appleDroppedThisEvent = false;
        if (eventBossBar != null) eventBossBar.removeAll();
        eventBossBar = Bukkit.createBossBar(
                "§6Lucky Leaves Event: Mine leaves for a chance of getting the apple of life and other OP Loot",
                BarColor.GREEN, BarStyle.SOLID);
        eventBossBar.setVisible(true);
        for (Player online : Bukkit.getOnlinePlayers()) eventBossBar.addPlayer(online);
        if (bossBarTask != null) bossBarTask.cancel();
        bossBarTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!luckyLeavesActive || eventBossBar == null) return;
            long remaining = Math.max(0L, eventEndsAt - System.currentTimeMillis());
            eventBossBar.setProgress(Math.max(0.0, Math.min(1.0, (double) remaining / (minutes * 60_000L))));
        }, 0L, 20L);
        eventEndsAt = System.currentTimeMillis() + minutes * 60_000L;
        stopTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            if (luckyLeavesActive) {
                stopLuckyLeaves();
                Bukkit.broadcastMessage("§6§lLUCKY LEAVES §chas ended.");
            }
        }, minutes * 60L * 20L);
    }

    private void stopLuckyLeaves() {
        luckyLeavesActive = false;
        eventEndsAt = 0L;
        if (bossBarTask != null) { bossBarTask.cancel(); bossBarTask = null; }
        if (eventBossBar != null) { eventBossBar.removeAll(); eventBossBar = null; }
        if (stopTask != null) {
            stopTask.cancel();
            stopTask = null;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeafBreak(BlockBreakEvent event) {
        if (!luckyLeavesActive) return;
        Block block = event.getBlock();
        if (!block.getType().name().endsWith("_LEAVES")) return;

        dropIf(10, Material.GOLDEN_APPLE, block);
        dropIf(100, Material.GOLD_BLOCK, block);
        dropIf(5, Material.GOLD_INGOT, block);
        dropIf(5, Material.IRON_INGOT, block);
        dropIf(5, Material.COPPER_INGOT, block);
        dropIf(50, Material.DIAMOND, block);
        dropIf(50, Material.NETHERITE_SCRAP, block);

        if (!appleDroppedThisEvent && random.nextInt(5_000) == 0) {
            appleDroppedThisEvent = true;
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), createAppleOfLife());
            Bukkit.broadcastMessage("§5§l✦ APPLE OF LIFE ✦ §d" + event.getPlayer().getName() + " §fhas discovered the §5§lApple Of Life§f!");
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendTitle("§5§lAPPLE OF LIFE!", "§d" + event.getPlayer().getName() + " found the rarest reward!", 10, 80, 20);
                online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.7f);
            }
        }

        List<Material> successfulArmorRolls = new ArrayList<>();
        for (Material piece : List.of(Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
                Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS)) {
            if (random.nextInt(500) == 0) successfulArmorRolls.add(piece);
        }
        if (!successfulArmorRolls.isEmpty()) {
            Material chosenPiece = successfulArmorRolls.get(random.nextInt(successfulArmorRolls.size()));
            dropArmor(chosenPiece, block, event.getPlayer());
        }
    }

    private void dropIf(int denominator, Material material, Block block) {
        if (random.nextInt(denominator) == 0) {
            int amount = weightedStackAmount();
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), new ItemStack(material, amount));
        }
    }

    // Quantity weights are 1, 1/1.5, 1/1.5^2 ... through quantity 10.
    private int weightedStackAmount() {
        double total = 0.0;
        for (int i = 0; i < 10; i++) total += 1.0 / Math.pow(1.5, i);
        double roll = random.nextDouble() * total;
        for (int i = 0; i < 10; i++) {
            roll -= 1.0 / Math.pow(1.5, i);
            if (roll <= 0.0) return i + 1;
        }
        return 10;
    }

    private void dropArmor(Material material, Block block, Player finder) {
        ItemStack armor = new ItemStack(material);
        ItemMeta meta = armor.getItemMeta();
        if (meta != null) {
            addEnchant(meta, "protection", 3);
            addEnchant(meta, "unbreaking", 3);
            addEnchant(meta, "mending", 1);
            switch (material) {
                case DIAMOND_HELMET -> {
                    addEnchant(meta, "respiration", 3);
                    addEnchant(meta, "aqua_affinity", 1);
                }
                case DIAMOND_LEGGINGS -> addEnchant(meta, "swift_sneak", 3);
                case DIAMOND_BOOTS -> {
                    addEnchant(meta, "feather_falling", 4);
                    addEnchant(meta, "depth_strider", 3);
                    addEnchant(meta, "soul_speed", 3);
                }
                default -> { }
            }
            armor.setItemMeta(meta);
        }
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), armor);
        String pieceName = switch (material) {
            case DIAMOND_HELMET -> "Diamond Helmet";
            case DIAMOND_CHESTPLATE -> "Diamond Chestplate";
            case DIAMOND_LEGGINGS -> "Diamond Leggings";
            case DIAMOND_BOOTS -> "Diamond Boots";
            default -> material.name();
        };
        Bukkit.broadcastMessage("§b§lDIAMOND ARMOR! §f" + finder.getName() + " §7found enchanted §b" + pieceName + "§7!");
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (luckyLeavesActive && eventBossBar != null) eventBossBar.addPlayer(event.getPlayer());
    }

    private void addEnchant(ItemMeta meta, String key, int level) {
        Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        if (enchantment != null) meta.addEnchant(enchantment, level, true);
    }

    private ItemStack createAppleOfLife() {
        ItemStack apple = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta meta = apple.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5§lApple Of Life");
            meta.setItemModel(appleModelKey);
            meta.setLore(java.util.List.of("§dAn endlessly reusable golden apple.", "§7A rare treasure from Lucky Leaves."));
            meta.getPersistentDataContainer().set(appleKey, PersistentDataType.BYTE, (byte) 1);
            meta.setEnchantmentGlintOverride(true);
            apple.setItemMeta(meta);
        }
        return apple;
    }

    @EventHandler
    public void onAppleConsume(PlayerItemConsumeEvent event) {
        ItemMeta meta = event.getItem().getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(appleKey, PersistentDataType.BYTE)) return;

        // Cancel vanilla consumption so the exact Apple Of Life stack stays in its slot.
        event.setCancelled(true);
        Player player = event.getPlayer();
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 100, 1));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION, 2400, 0));
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + 4));
        player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + 9.6f));
    }
}
