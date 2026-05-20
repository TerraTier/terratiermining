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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Core service for the custom mining system.
 * Orchestrates sessions, speed calculations, and block breaking logic.
 */
public final class MiningService {
    private static final double TICKS_PER_SECOND = 20.0;

    private final Plugin plugin;
    private final BlockIdentityResolver blockResolver = new BlockIdentityResolver();
    private final ToolIdentityResolver toolResolver = new ToolIdentityResolver();
    private final MiningBuffResolver buffResolver;
    private final MiningCalculator calculator;
    private final MiningSessionManager sessionManager = new MiningSessionManager();
    private final Map<UUID, Long> placementCooldowns = new ConcurrentHashMap<>();
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            suppressor.remove(player);
        }
        regenerationManager.shutdown();
        HandlerList.unregisterAll(listener);
    }

    // --- API Methods ---

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

        // Block Section
        player.sendMessage("  §b§lBLOCK §8» §f" + target.getType().name().toLowerCase());
        player.sendMessage("    §8▪ §7Data: §f" + target.getBlockData().getAsString(false));
        player.sendMessage("    §8▪ §7Candidates: §3" + String.join("§8, §3", candidates));
        player.sendMessage("    §8▪ §7Matched Rule: " + (rule != null ? "§a" + rule.id() : "§cNone"));
        
        if (rule != null) {
            player.sendMessage("    §8▪ §7Strength: §f" + rule.strength());
            player.sendMessage("    §8▪ §7Requirement: §f" + rule.requirementText());
            player.sendMessage("    §8▪ §7Can Harvest: " + (rule.canHarvest(tool) ? "§aYes" : "§cNo"));
        }

        player.sendMessage("");

        // Tool Section
        player.sendMessage("  §d§lTOOL §8» §f" + tool.id());
        player.sendMessage("    §8▪ §7Type: §f" + tool.type().displayName());
        player.sendMessage("    §8▪ §7Tier: §f" + tool.tier());
        player.sendMessage("    §8▪ §7Speed: §f" + String.format("%.2f", tool.speed()));
        player.sendMessage("    §8▪ §7Source: §8§o" + tool.source());
    }

    private String fancy(String input) {
        return input.toLowerCase()
            .replace("a", "ᴀ")
            .replace("b", "ʙ")
            .replace("c", "ᴄ")
            .replace("d", "ᴅ")
            .replace("e", "ᴇ")
            .replace("f", "ꜰ")
            .replace("g", "ɢ")
            .replace("h", "ʜ")
            .replace("i", "ɪ")
            .replace("j", "ᴊ")
            .replace("k", "ᴋ")
            .replace("l", "ʟ")
            .replace("m", "ᴍ")
            .replace("n", "ɴ")
            .replace("o", "ᴏ")
            .replace("p", "ᴘ")
            .replace("q", "ǫ")
            .replace("r", "ʀ")
            .replace("s", "ꜱ")
            .replace("t", "ᴛ")
            .replace("u", "ᴜ")
            .replace("v", "ᴠ")
            .replace("w", "ᴡ")
            .replace("x", "x")
            .replace("y", "ʏ")
            .replace("z", "ᴢ");
    }

    // --- Event Handlers (Called by MiningListener) ---

    public void handleAnimation(PlayerAnimationEvent event) {
        if (!config.enabled() || shouldBypass(event.getPlayer())) return;

        Player player = event.getPlayer();
        if (sessionManager.hasSession(player.getUniqueId())) return;

        if (System.currentTimeMillis() - placementCooldowns.getOrDefault(player.getUniqueId(), 0L) < 250) {
            return;
        }

        Block target = rayTraceTarget(player);
        if (target == null) return;

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
        if (sessionManager.isSessionActive(player.getUniqueId(), block)) {
            return;
        }

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

    public void handleQuit(PlayerQuitEvent event) {
        clearSession(event.getPlayer());
        placementCooldowns.remove(event.getPlayer().getUniqueId());
    }

    public void handleChangedWorld(PlayerChangedWorldEvent event) {
        clearSession(event.getPlayer());
    }

    // --- Internal Logic ---

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
        sessionManager.markCompleting(player.getUniqueId(), session.blockKey());

        try {
            if (rule != null) {
                handleCustomBreak(player, block, originalData, rule);
                clearSession(player);
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

    private List<ItemStack> resolveDrops(Player player, Block block, MiningRule rule) {
        if (rule.customDrops().isEmpty()) {
            return new ArrayList<>(block.getDrops(player.getInventory().getItemInMainHand(), player));
        }

        List<ItemStack> drops = new ArrayList<>();
        for (MiningRule.DropRule dropRule : rule.customDrops()) {
            Material mat = Material.matchMaterial(dropRule.material());
            if (mat != null) {
                int amount = dropRule.min();
                if (dropRule.max() > dropRule.min()) {
                    amount = ThreadLocalRandom.current().nextInt(dropRule.min(), dropRule.max() + 1);
                }
                if (amount > 0) drops.add(new ItemStack(mat, amount));
            }
        }
        return drops;
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

    private int damageSourceId(Player player) {
        return 1_000_000_000 + (player.getEntityId() & 0x0FFFFFFF);
    }
}
