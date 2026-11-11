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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class CursedMiniEvents extends JavaPlugin implements Listener {
    private final Random random = new Random();
    private NamespacedKey appleKey;
    private NamespacedKey appleModelKey;
    private boolean luckyLeavesActive = false;
    private boolean appleDroppedThisEvent = false;
    private long appleUnlockTimeMillis = 0L;
    private final Map<UUID, Integer> leavesBroken = new HashMap<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    private BukkitTask bossBarTask;
    private BossBar eventBossBar;

    @Override
    public void onEnable() {
        appleKey = new NamespacedKey(this, "apple_of_life");
        appleModelKey = new NamespacedKey(this, "apple_of_life");
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Cursed Mini Events 1.0.9 enabled.");
    }

    @Override
    public void onDisable() {
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
                    startLuckyLeaves();
                    Bukkit.broadcastMessage("§6§lLUCKY LEAVES §ehas started! Break leaves for rare rewards. The event ends when someone finds the Apple Of Life!");
                }
                case "stop" -> {
                    if (!luckyLeavesActive) sender.sendMessage("§cLucky Leaves is not running.");
                    else {
                        finishLuckyLeaves("§cThe event was stopped by an administrator.");
                    }
                }
                case "status" -> {
                    if (!luckyLeavesActive) sender.sendMessage("§eLucky Leaves is currently inactive.");
                    else sender.sendMessage("§aLucky Leaves is active. It ends when the Apple Of Life is found.");
                }
                case "locktime" -> {
                    if (!(sender instanceof Player player) || !player.isOp()) {
                        sender.sendMessage("§cYou do not have permission to use this testing command.");
                        return true;
                    }
                    if (!luckyLeavesActive) {
                        sender.sendMessage("§eThere is no active Lucky Leaves event.");
                        return true;
                    }
                    long remainingMillis = Math.max(0L, appleUnlockTimeMillis - System.currentTimeMillis());
                    long remainingMinutes = (remainingMillis + 59_999L) / 60_000L;
                    long remainingSeconds = (remainingMillis + 999L) / 1_000L;
                    sender.sendMessage("§d[Testing] Apple Of Life unlock: §f" + remainingMinutes
                            + " minute(s) remaining §7(" + remainingSeconds + " second(s)).");
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cursedminievents.admin")) return List.of();

        if (command.getName().equalsIgnoreCase("minievent")) {
            if (args.length == 1) {
                return matching(args[0], List.of("luckyleaves"));
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("luckyleaves")) {
                List<String> options = new ArrayList<>(List.of("start", "stop", "status"));
                if (sender instanceof Player player && player.isOp()) options.add("locktime");
                return matching(args[1], options);
            }
            return List.of();
        }

        if (command.getName().equalsIgnoreCase("minievents")) {
            if (args.length == 1) return matching(args[0], List.of("give"));
            if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                return matching(args[1], List.of("apple"));
            }
        }
        return List.of();
    }

    private List<String> matching(String typed, List<String> options) {
        String prefix = typed.toLowerCase();
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }

    private void startLuckyLeaves() {
        luckyLeavesActive = true;
        appleDroppedThisEvent = false;
        appleUnlockTimeMillis = System.currentTimeMillis() + 10L * 60L * 1000L;
        leavesBroken.clear();
        playerNames.clear();
        if (eventBossBar != null) eventBossBar.removeAll();
        eventBossBar = Bukkit.createBossBar(
                "§6Lucky Leaves Event: Mine leaves for a chance of getting the apple of life and other OP Loot",
                BarColor.GREEN, BarStyle.SOLID);
        eventBossBar.setVisible(true);
        eventBossBar.setProgress(1.0);
        for (Player online : Bukkit.getOnlinePlayers()) eventBossBar.addPlayer(online);
    }

    private void finishLuckyLeaves(String endingMessage) {
        if (!luckyLeavesActive) return;
        luckyLeavesActive = false;
        if (eventBossBar != null) {
            eventBossBar.removeAll();
            eventBossBar = null;
        }
        Bukkit.broadcastMessage("§6§l========== LUCKY LEAVES LEADERBOARD ==========");
        Bukkit.broadcastMessage(endingMessage);
        List<Map.Entry<UUID, Integer>> ranking = new ArrayList<>(leavesBroken.entrySet());
        ranking.sort(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(entry -> playerNames.getOrDefault(entry.getKey(), "Unknown"), String.CASE_INSENSITIVE_ORDER));
        if (ranking.isEmpty()) {
            Bukkit.broadcastMessage("§7No leaves were broken during this event.");
        } else {
            int place = 1;
            for (Map.Entry<UUID, Integer> entry : ranking) {
                String playerName = playerNames.getOrDefault(entry.getKey(), "Unknown");
                Bukkit.broadcastMessage("§e#" + place + " §f" + playerName + " §7- §a" + entry.getValue() + " leaves");
                place++;
            }
        }
        Bukkit.broadcastMessage("§6§l============================================");
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeafBreak(BlockBreakEvent event) {
        if (!luckyLeavesActive) return;
        Block block = event.getBlock();
        if (!block.getType().name().endsWith("_LEAVES")) return;

        UUID playerId = event.getPlayer().getUniqueId();
        leavesBroken.merge(playerId, 1, Integer::sum);
        playerNames.put(playerId, event.getPlayer().getName());

        dropIf(1, 10, Material.GOLDEN_APPLE, block);
        dropIf(1, 50, Material.GOLD_BLOCK, block);
        dropIf(1, 10, Material.GOLD_INGOT, block);
        dropIf(1, 2, Material.IRON_INGOT, block);
        dropIf(1, 2, Material.COPPER_INGOT, block);
        dropIf(1, 25, Material.DIAMOND, block);
        dropIf(1, 25, Material.NETHERITE_SCRAP, block);

        if (!appleDroppedThisEvent && System.currentTimeMillis() >= appleUnlockTimeMillis
                && random.nextInt(2000) == 0) {
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

        if (appleDroppedThisEvent) {
            finishLuckyLeaves("§d" + event.getPlayer().getName() + " §ffound the Apple Of Life! The event is over.");
        }
    }

    private void dropIf(int numerator, int denominator, Material material, Block block) {
        if (random.nextInt(denominator) < numerator) {
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
