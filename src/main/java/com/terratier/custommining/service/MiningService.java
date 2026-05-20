package com.terratier.custommining.service;

import com.terratier.custommining.config.ItemAttributesConfig;
import com.terratier.custommining.config.MiningConfig;
import com.terratier.custommining.config.MiningRule;
import com.terratier.custommining.listener.MiningListener;
import com.terratier.custommining.model.BlockKey;
import com.terratier.custommining.model.MiningBuffTotals;
import com.terratier.custommining.model.MiningSession;
import com.terratier.custommining.model.ToolStats;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class MiningService {
    private static final double TICKS_PER_SECOND = 20.0;

    private final Plugin plugin;
    private final BlockIdentityResolver blockResolver = new BlockIdentityResolver();
    private final ToolIdentityResolver toolResolver = new ToolIdentityResolver();
    private final MiningBuffResolver buffResolver;
    private final MiningCalculator calculator;
    private final MiningSessionManager sessionManager = new MiningSessionManager();
    private final Map<UUID, Long> placementCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> interactionCooldowns = new ConcurrentHashMap<>();
    private final Map<BlockKey, Long> breakCooldowns = new ConcurrentHashMap<>();
    private final VanillaMiningSuppressor suppressor;
    private final BlockRegenerationManager regenerationManager;
    private final FortuneManager fortuneManager = new FortuneManager();
    private final Listener listener;
    private final BukkitTask tickTask;
    private MiningConfig config;

    public MiningService(Plugin plugin, MiningConfig config, ItemAttributesConfig itemAttributesConfig) {
        this.plugin = plugin;
        this.config = config;
        this.buffResolver = new MiningBuffResolver(toolResolver, plugin, itemAttributesConfig);
        this.suppressor = new VanillaMiningSuppressor(plugin, blockResolver);
        this.calculator = new MiningCalculator(toolResolver, buffResolver, suppressor.getKey());
        this.regenerationManager = new BlockRegenerationManager(plugin);
        this.fortuneManager.updateMappings(config.fortuneMappings());

        this.listener = new MiningListener(this);
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        this.tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                suppressor.tick(MiningService.this.config);
                tickSessions();
                breakCooldowns.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > 200);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void updateConfig(MiningConfig config, ItemAttributesConfig itemAttributesConfig) {
        this.config = config;
        this.buffResolver.updateItemAttributesConfig(itemAttributesConfig);
        this.fortuneManager.updateMappings(config.fortuneMappings());
        clearAllSessions();
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        clearAllSessions();
        placementCooldowns.clear();
        interactionCooldowns.clear();
        breakCooldowns.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            suppressor.remove(player);
        }
        regenerationManager.shutdown();
        HandlerList.unregisterAll(listener);
    }

    public ToolStats resolveTool(Player player) {
        return calculator.resolveTool(player, config);
    }

    public MiningBuffTotals resolveBuffs(Player player) {
        return buffResolver.resolve(player, config);
    }

    public void debug(Player player) {
        Block target = rayTraceTarget(player);
        if (target == null) {
            player.sendMessage("  §c§l× §7" + fancy("No block in sight."));
            return;
        }

        List<String> candidates = blockResolver.candidates(target, config);
        MiningRule rule = config.findBlockRule(candidates);
        ToolStats tool = resolveTool(player);

        player.sendMessage("  §b§l" + fancy("BLOCK") + " §8» §f" + target.getType().name().toLowerCase());
        player.sendMessage("    §8▪ §7" + fancy("Data") + ": §f" + target.getBlockData().getAsString(false));
        player.sendMessage("    §8▪ §7" + fancy("Matched") + ": " + (rule != null ? "§a" + rule.id() : "§cNone"));
        
        if (rule != null) {
            player.sendMessage("    §8▪ §7" + fancy("Strength") + ": §f" + rule.strength());
            player.sendMessage("    §8▪ §7" + fancy("Tool") + ": §f" + rule.requirementText());
        }

        player.sendMessage("");
        player.sendMessage("  §d§l" + fancy("TOOL") + " §8» §f" + tool.id());
        player.sendMessage("    §8▪ §7" + fancy("Type") + ": §f" + tool.type().displayName());
        player.sendMessage("    §8▪ §7" + fancy("Tier") + ": §f" + tool.tier());
        player.sendMessage("    §8▪ §7" + fancy("Speed") + ": §f" + String.format("%.1f", tool.speed()));
    }

    public void handleAnimation(PlayerAnimationEvent event) {
        if (!config.enabled() || shouldBypass(event.getPlayer())) return;

        Player player = event.getPlayer();
        if (sessionManager.hasSession(player.getUniqueId())) return;

        long now = System.currentTimeMillis();
        if (now - placementCooldowns.getOrDefault(player.getUniqueId(), 0L) < 250) return;
        if (now - interactionCooldowns.getOrDefault(player.getUniqueId(), 0L) < 250) return;

        Block target = rayTraceTarget(player);
        if (target == null) return;

        if (breakCooldowns.containsKey(BlockKey.from(target))) return;

        MiningRule rule = config.findBlockRule(blockResolver.candidates(target, config));
        if (rule != null || regenerationManager.isRegenerating(target.getLocation())) {
            sessionManager.startSession(player, target);
        }
    }

    public void handleBlockDamage(BlockDamageEvent event) {
        if (!config.enabled() || shouldBypass(event.getPlayer())) return;

        Block block = event.getBlock();
        Player player = event.getPlayer();
        
        if (!isWithinReach(player, block)) return;

        long now = System.currentTimeMillis();
        if (now - interactionCooldowns.getOrDefault(player.getUniqueId(), 0L) < 250) {
            event.setCancelled(true);
            return;
        }

        if (breakCooldowns.containsKey(BlockKey.from(block))) {
            event.setCancelled(true);
            return;
        }

        boolean isRegen = regenerationManager.isRegenerating(block.getLocation());
        MiningRule rule = config.findBlockRule(blockResolver.candidates(block, config));
        
        if (!isControlledBlock(rule) && !isRegen) return;

        event.setInstaBreak(false);

        if (rule == null && !isRegen) {
            clearSession(player);
            event.setCancelled(true);
            resetClientBlock(player, block, true);
            return;
        }

        ToolStats tool = resolveTool(player);
        if (sessionManager.isSessionActive(player.getUniqueId(), block)) return;

        clearSession(player);
        if (!isRegen && !rule.canHarvest(tool)) {
            event.setCancelled(true);
            resetClientBlock(player, block, true);
            return;
        }

        sessionManager.startSession(player, block);
    }

    public void handleBlockBreak(BlockBreakEvent event) {
        if (!config.enabled() || shouldBypass(event.getPlayer())) return;

        Block block = event.getBlock();
        if (regenerationManager.isRegenerating(block.getLocation())) {
            event.setCancelled(true);
            return;
        }

        MiningRule rule = config.findBlockRule(blockResolver.candidates(block, config));
        if (!isControlledBlock(rule)) return;

        Player player = event.getPlayer();
        if (event.isCancelled()) {
            resetClientBlock(player, block, true);
            return;
        }

        if (sessionManager.isCompleting(player.getUniqueId(), BlockKey.from(block))) return;

        if (rule == null) {
            clearSession(player);
            event.setCancelled(true);
            resetClientBlock(player, block, true);
            return;
        }

        MiningSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null || !session.sameBlock(block) || session.progress() < 1.0) {
            event.setCancelled(true);
            resetClientBlock(player, block, true);
        }
    }

    public void handleCancelledBreakVisual(BlockBreakEvent event) {
        Block block = event.getBlock();
        MiningRule rule = config.findBlockRule(blockResolver.candidates(block, config));
        if (isControlledBlock(rule)) {
            resetClientBlock(event.getPlayer(), block, true);
        }
    }

    public void handleBlockDamageAbort(BlockDamageAbortEvent event) {
        if (sessionManager.isTransitioning(BlockKey.from(event.getBlock()))) return;

        MiningSession session = sessionManager.getSession(event.getPlayer().getUniqueId());
        if (session != null && session.sameBlock(event.getBlock())) {
            clearSession(event.getPlayer());
        }
    }

    public void handleBlockPlace(BlockPlaceEvent event) {
        placementCooldowns.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    public void handleInteract(PlayerInteractEvent event) {
        if (event.getAction().name().contains("RIGHT")) {
            interactionCooldowns.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    public void handleQuit(PlayerQuitEvent event) {
        clearSession(event.getPlayer());
        placementCooldowns.remove(event.getPlayer().getUniqueId());
        interactionCooldowns.remove(event.getPlayer().getUniqueId());
    }

    public void handleChangedWorld(PlayerChangedWorldEvent event) {
        clearSession(event.getPlayer());
    }

    private void tickSessions() {
        Iterator<MiningSession> iterator = sessionManager.getActiveSessions().iterator();
        while (iterator.hasNext()) {
            MiningSession session = iterator.next();
            Player player = Bukkit.getPlayer(session.playerId());
            if (player == null || !player.isOnline() || shouldBypass(player)) {
                iterator.remove();
                continue;
            }

            Block block = session.block();
            if (!isWithinReach(player, block) || !isStillTargeting(player, session)) {
                clearAnimation(player, session);
                iterator.remove();
                continue;
            }

            boolean isRegen = regenerationManager.isRegenerating(block.getLocation());
            MiningRule rule = config.findBlockRule(blockResolver.candidates(block, config));
            
            if ((!isControlledBlock(rule) || rule == null) && !isRegen) {
                clearAnimation(player, session);
                iterator.remove();
                continue;
            }

            ToolStats tool = resolveTool(player);
            if (!isRegen && !rule.canHarvest(tool)) {
                clearAnimation(player, session);
                resetClientBlock(player, block, true);
                iterator.remove();
                continue;
            }

            if (isRegen) continue;

            double progressPerTick = tool.speed() / rule.strength() / TICKS_PER_SECOND;
            session.addProgress(progressPerTick);

            if (session.progress() >= 1.0) {
                completeBreak(player, session);
                iterator.remove();
                continue;
            }

            sendCrackAnimation(player, session);
        }
    }

    private void completeBreak(Player player, MiningSession session) {
        Block block = session.block();
        BlockData originalData = block.getBlockData().clone();
        MiningRule rule = config.findBlockRule(blockResolver.candidates(block, config));

        clearAnimation(player, session);
        sessionManager.endSession(player);
        sessionManager.markCompleting(player.getUniqueId(), session.blockKey());
        breakCooldowns.put(session.blockKey(), System.currentTimeMillis());

        try {
            if (rule != null) {
                handleCustomBreak(player, block, originalData, rule);
            } else if (player.breakBlock(block)) {
                playBreakEffects(block, originalData);
            }
        } finally {
            sessionManager.unmarkCompleting(player.getUniqueId());
        }
    }

    private void handleCustomBreak(Player player, Block block, BlockData originalData, MiningRule rule) {
        List<ItemStack> drops = resolveDrops(player, block, rule);
        MiningBuffTotals buffs = resolveBuffs(player);
        fortuneManager.applyFortune(player, block, drops, buffs);
        playBreakEffects(block, originalData);

        boolean useAutoPickup = rule.autoPickupOverride() != null ? rule.autoPickupOverride() : config.autoPickup();
        handleDrops(player, block.getLocation(), drops, useAutoPickup);

        if (rule.canRegenerate()) {
            if (useAutoPickup) block.setType(Material.AIR, false);
            regenerationManager.queueRegeneration(block, originalData, rule, () -> handleRegenComplete(block));
        } else {
            block.setType(Material.AIR, true);
        }
    }

    public void showDrops(Player player) {
        Block target = rayTraceTarget(player);
        if (target == null) {
            player.sendMessage("  §c§l× §7" + fancy("No block in sight."));
            return;
        }

        List<String> candidates = blockResolver.candidates(target, config);
        MiningRule rule = config.findBlockRule(candidates);

        if (rule == null) {
            player.sendMessage("  §c§l× §7" + fancy("No custom drops configured."));
            return;
        }

        MiningBuffTotals buffs = resolveBuffs(player);
        double luck = buffs.get("luck", 0.0) + buffs.get("mining_luck", 0.0);
        double luckMultiplier = 1.0 + (luck / 100.0);

        player.sendMessage("");
        String blockName = target.getType().name().replace("_", " ");
        player.sendMessage("  " + fade(blockName, "#4facfe", "#00f2fe", true) + " §8| §7Luck: §b" + String.format("%.1f", luck) + " ✦");
        player.sendMessage("");

        String[][] palettes = {
            {"#ff9a9e", "#fecfef"}, {"#a1c4fd", "#c2e9fb"}, {"#84fab0", "#8fd3f4"}, {"#f6d365", "#fda085"}, {"#667eea", "#764ba2"}
        };

        int tIdx = 0;
        for (MiningRule.LootTable table : rule.tables()) {
            String[] colors = palettes[tIdx % palettes.length];
            String displayType = switch(table.strategy()) {
                case POOLED -> "WEIGHTED";
                case STATIC -> "FIXED";
                case INDEPENDENT -> "CHANCE";
            };
            String techType = table.strategy().name().toLowerCase();
            
            player.sendMessage("  " + hex(colors[0]) + "§l" + fancy(displayType) + " §8" + fancy(techType));

            if (table.strategy() == MiningRule.DropStrategy.POOLED) {
                double rawTotal = 0;
                for (MiningRule.DropRule r : table.pool()) rawTotal += r.value();

                double luckPercent = luck / 100.0;
                double totalMod = 0;
                double[] modWeights = new double[table.pool().size()];

                for (int i = 0; i < table.pool().size(); i++) {
                    MiningRule.DropRule r = table.pool().get(i);
                    double weight = r.value();
                    double rarity = Math.log10(Math.max(1.0, rawTotal / Math.max(0.001, weight)));
                    double boost = 1.0 + (luckPercent * rarity);
                    if (r.material().equalsIgnoreCase("minecraft:air") || r.material().equalsIgnoreCase("air")) boost = 1.0;
                    modWeights[i] = weight * boost;
                    totalMod += modWeights[i];
                }

                for (int i = 0; i < table.pool().size(); i++) {
                    MiningRule.DropRule r = table.pool().get(i);
                    double base = rawTotal > 0 ? (r.value() / rawTotal) * 100 : 0;
                    double curr = totalMod > 0 ? (modWeights[i] / totalMod) * 100 : 0;
                    renderLine(player, r, base, curr, luck != 0, colors[1]);
                }
            } else {
                for (MiningRule.DropRule r : table.pool()) {
                    double base = r.value() * 100;
                    double curr = table.strategy() == MiningRule.DropStrategy.STATIC ? base : Math.min(100, base * luckMultiplier);
                    renderLine(player, r, base, curr, luck != 0 && table.strategy() != MiningRule.DropStrategy.STATIC, colors[1]);
                }
            }
            tIdx++;
            if (tIdx < rule.tables().size()) player.sendMessage("");
        }
        player.sendMessage("");
    }

    private void renderLine(Player player, MiningRule.DropRule r, double base, double curr, boolean showLuck, String secColor) {
        String mat = r.material().replace("minecraft:", "").replace("_", " ");
        String amt = " §8x" + String.valueOf(r.min()) + (r.max() > r.min() ? "-" + String.valueOf(r.max()) : "");
        
        String prob;
        if (!showLuck || Math.abs(curr - base) < 0.0001) {
            prob = "§7" + formatPercent(base);
        } else {
            String color = curr > base ? "§b" : "§7";
            prob = "§7" + formatPercent(base) + " §8→ " + color + formatPercent(curr);
        }
        
        player.sendMessage("    §8▪ " + hex(secColor) + fancy(mat) + amt + " §8» " + prob);
    }

    private String hex(String code) {
        return "§x" + code.substring(1).chars().mapToObj(c -> "§" + (char)c).reduce("", (a, b) -> a + b);
    }

    private String fade(String text, String from, String to, boolean bold) {
        int r1 = Integer.valueOf(from.substring(1, 3), 16);
        int g1 = Integer.valueOf(from.substring(3, 5), 16);
        int b1 = Integer.valueOf(from.substring(5, 7), 16);
        int r2 = Integer.valueOf(to.substring(1, 3), 16);
        int g2 = Integer.valueOf(to.substring(3, 5), 16);
        int b2 = Integer.valueOf(to.substring(5, 7), 16);

        StringBuilder sb = new StringBuilder();
        int length = text.length();
        for (int i = 0; i < length; i++) {
            int r = r1 + (r2 - r1) * i / Math.max(1, length - 1);
            int g = g1 + (g2 - g1) * i / Math.max(1, length - 1);
            int b = b1 + (b2 - b1) * i / Math.max(1, length - 1);
            String hex = String.format("#%02x%02x%02x", r, g, b);
            sb.append(hex(hex));
            if (bold) sb.append("§l");
            sb.append(fancy(String.valueOf(text.charAt(i))));
        }
        return sb.toString();
    }

    private String formatPercent(double val) {
        if (val >= 10) return String.format("%.1f%%", val);
        if (val >= 1) return String.format("%.2f%%", val);
        return String.format("%.4f%%", val);
    }

    private List<ItemStack> resolveDrops(Player player, Block block, MiningRule rule) {
        if (rule.tables().isEmpty()) {
            return new ArrayList<>(block.getDrops(player.getInventory().getItemInMainHand(), player));
        }

        MiningBuffTotals buffs = resolveBuffs(player);
        double luck = buffs.get("luck", 0.0) + buffs.get("mining_luck", 0.0);
        double luckMultiplier = 1.0 + (luck / 100.0);

        List<ItemStack> drops = new ArrayList<>();
        for (MiningRule.LootTable table : rule.tables()) {
            int rolls = table.minRolls();
            if (table.maxRolls() > table.minRolls()) {
                rolls = ThreadLocalRandom.current().nextInt(table.minRolls(), table.maxRolls() + 1);
            }

            for (int i = 0; i < rolls; i++) {
                if (table.strategy() == MiningRule.DropStrategy.POOLED) {
                    selectWeighted(table.pool(), luck).ifPresent(dropRule -> addDrop(drops, dropRule));
                } else if (table.strategy() == MiningRule.DropStrategy.INDEPENDENT) {
                    for (MiningRule.DropRule dropRule : table.pool()) {
                        if (ThreadLocalRandom.current().nextDouble() <= (dropRule.value() * luckMultiplier)) {
                            addDrop(drops, dropRule);
                        }
                    }
                } else if (table.strategy() == MiningRule.DropStrategy.STATIC) {
                    for (MiningRule.DropRule dropRule : table.pool()) {
                        if (ThreadLocalRandom.current().nextDouble() <= dropRule.value()) {
                            addDrop(drops, dropRule);
                        }
                    }
                }
            }
        }
        return drops;
    }

    private java.util.Optional<MiningRule.DropRule> selectWeighted(List<MiningRule.DropRule> rules, double luck) {
        double rawTotal = 0;
        for (MiningRule.DropRule r : rules) rawTotal += r.value();
        if (rawTotal <= 0) return java.util.Optional.empty();

        double luckPercent = luck / 100.0;
        double totalModified = 0;
        double[] modifiedWeights = new double[rules.size()];

        for (int i = 0; i < rules.size(); i++) {
            MiningRule.DropRule rule = rules.get(i);
            double weight = rule.value();
            if (weight <= 0) continue;

            double rarityFactor = Math.log10(Math.max(1.0, rawTotal / weight));
            double boost = 1.0 + (luckPercent * rarityFactor);

            if (rule.material().equalsIgnoreCase("minecraft:air") || rule.material().equalsIgnoreCase("air")) {
                boost = 1.0;
            }

            modifiedWeights[i] = weight * boost;
            totalModified += modifiedWeights[i];
        }

        if (totalModified <= 0) return java.util.Optional.empty();

        double roll = ThreadLocalRandom.current().nextDouble() * totalModified;
        double cumulative = 0;
        for (int i = 0; i < rules.size(); i++) {
            cumulative += modifiedWeights[i];
            if (roll <= cumulative) return java.util.Optional.of(rules.get(i));
        }
        return java.util.Optional.empty();
    }


    private void addDrop(List<ItemStack> drops, MiningRule.DropRule dropRule) {
        Material mat = Material.matchMaterial(dropRule.material());
        if (mat != null) {
            int amount = dropRule.min();
            if (dropRule.max() > dropRule.min()) {
                amount = ThreadLocalRandom.current().nextInt(dropRule.min(), dropRule.max() + 1);
            }
            if (amount > 0) drops.add(new ItemStack(mat, amount));
        }
    }

    private void handleDrops(Player player, Location loc, List<ItemStack> drops, boolean autoPickup) {
        for (ItemStack drop : drops) {
            if (autoPickup) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(drop);
                for (ItemStack item : overflow.values()) popItem(player.getLocation(), item);
            } else {
                popItem(loc, drop);
            }
        }
    }

    private void handleRegenComplete(Block block) {
        BlockKey key = BlockKey.from(block);
        sessionManager.markTransitioning(key);
        for (MiningSession s : sessionManager.getActiveSessions()) {
            if (s.blockKey().equals(key)) {
                Player p = Bukkit.getPlayer(s.playerId());
                if (p != null && p.isOnline()) {
                    resetClientBlock(p, block, false);
                    sessionManager.startSession(p, block);
                }
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> sessionManager.unmarkTransitioning(key), 2L);
    }

    private boolean shouldBypass(Player player) {
        if (!config.bypassCreative()) return false;
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }

    private boolean isControlledBlock(MiningRule rule) {
        return rule != null || !config.controlConfiguredBlocksOnly();
    }

    private boolean isWithinReach(Player player, Block block) {
        if (!player.getWorld().equals(block.getWorld())) return false;
        double reach = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).getValue();
        return player.getLocation().distanceSquared(block.getLocation().add(0.5, 0.5, 0.5)) <= Math.pow(reach + 1.25, 2);
    }

    private boolean isStillTargeting(Player player, MiningSession session) {
        Block target = rayTraceTarget(player);
        return target != null && session.sameBlock(target);
    }

    private Block rayTraceTarget(Player player) {
        double reach = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).getValue();
        RayTraceResult result = player.getWorld().rayTraceBlocks(
            player.getEyeLocation(), player.getLocation().getDirection(), reach, FluidCollisionMode.NEVER, false
        );
        return result != null ? result.getHitBlock() : null;
    }

    private void clearSession(Player player) {
        MiningSession removed = sessionManager.endSession(player);
        if (removed != null) clearAnimation(player, removed);
    }

    private void clearAllSessions() {
        for (MiningSession session : sessionManager.getActiveSessions()) {
            Player player = Bukkit.getPlayer(session.playerId());
            if (player != null) clearAnimation(player, session);
        }
        sessionManager.clearAll();
    }

    private void sendCrackAnimation(Player player, MiningSession session) {
        player.sendBlockDamage(session.animationLocation(), (float) Math.max(0.0, Math.min(0.99, session.progress())), damageSourceId(player));
    }

    private void clearAnimation(Player player, MiningSession session) {
        player.sendBlockDamage(session.animationLocation(), 0.0f, damageSourceId(player));
    }

    private void popItem(Location loc, ItemStack item) {
        double x = loc.getX() + (ThreadLocalRandom.current().nextDouble() * 0.5) + 0.25;
        double y = loc.getY() + (ThreadLocalRandom.current().nextDouble() * 0.5) + 0.25;
        double z = loc.getZ() + (ThreadLocalRandom.current().nextDouble() * 0.5) + 0.25;
        Location spawnLoc = new Location(loc.getWorld(), x, y, z);
        org.bukkit.entity.Item dropped = loc.getWorld().dropItem(spawnLoc, item);
        dropped.setVelocity(new Vector((ThreadLocalRandom.current().nextDouble() * 0.1) - 0.05, 0.2, (ThreadLocalRandom.current().nextDouble() * 0.1) - 0.05));
    }

    private void playBreakEffects(Block block, BlockData blockData) {
        block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, blockData);
    }

    private void resetClientBlock(Player player, Block block, boolean repeatNextTick) {
        player.sendBlockDamage(block.getLocation(), 0.0f);
        player.sendBlockDamage(block.getLocation(), 0.0f, damageSourceId(player));
        player.sendBlockChange(block.getLocation(), block.getBlockData());
        if (repeatNextTick) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && player.getWorld().equals(block.getWorld())) {
                    player.sendBlockDamage(block.getLocation(), 0.0f);
                    player.sendBlockDamage(block.getLocation(), 0.0f, damageSourceId(player));
                    player.sendBlockChange(block.getLocation(), block.getBlockData());
                }
            });
        }
    }

    private String fancy(String input) {
        if (input == null) return "";
        return input.toLowerCase()
            .replace("a", "ᴀ").replace("b", "ʙ").replace("c", "ᴄ").replace("d", "ᴅ")
            .replace("e", "ᴇ").replace("f", "ꜰ").replace("g", "ɢ").replace("h", "ʜ")
            .replace("i", "ɪ").replace("j", "ᴊ").replace("k", "ᴋ").replace("l", "ʟ")
            .replace("m", "ᴍ").replace("n", "ɴ").replace("o", "ᴏ").replace("p", "ᴘ")
            .replace("q", "ǫ").replace("r", "ʀ").replace("s", "ꜱ").replace("t", "ᴛ")
            .replace("u", "ᴜ").replace("v", "ᴠ").replace("w", "ᴡ").replace("x", "x")
            .replace("y", "ʏ").replace("z", "ᴢ");
    }

    private int damageSourceId(Player player) {
        return 1_000_000_000 + (player.getEntityId() & 0x0FFFFFFF);
    }
}
