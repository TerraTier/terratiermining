package com.terratier.custommining;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.SoundGroup;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
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
import org.bukkit.util.RayTraceResult;

final class MiningService {
    private static final double TICKS_PER_SECOND = 20.0;

    private final Plugin plugin;
    private final BlockIdentityResolver blockResolver = new BlockIdentityResolver();
    private final ToolIdentityResolver toolResolver = new ToolIdentityResolver();
    private final MiningBuffResolver buffResolver;
    private final MiningCalculator calculator;
    private final MiningSessionManager sessionManager = new MiningSessionManager();
    private final Map<UUID, Long> placementCooldowns = new java.util.concurrent.ConcurrentHashMap<>();
    private final VanillaMiningSuppressor suppressor;
    private final BlockRegenerationManager regenerationManager;
    private final FortuneManager fortuneManager = new FortuneManager();
    private MiningConfig config;

    MiningService(Plugin plugin, MiningConfig config, ItemAttributesConfig itemAttributesConfig) {
        this.plugin = plugin;
        this.config = config;
        this.buffResolver = new MiningBuffResolver(toolResolver, plugin, itemAttributesConfig);
        this.suppressor = new VanillaMiningSuppressor(plugin, blockResolver);
        this.calculator = new MiningCalculator(toolResolver, buffResolver, suppressor.getKey());
        this.regenerationManager = new BlockRegenerationManager(plugin);
        this.fortuneManager.updateMappings(config.fortuneMappings());

        Bukkit.getPluginManager().registerEvents(new MiningEventHandler(this), plugin);
        
        // Internal listener for placement cooldowns
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
            public void onBlockPlace(BlockPlaceEvent event) {
                placementCooldowns.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
            }
        }, plugin);

        new BukkitRunnable() {
            @Override
            public void run() {
                suppressor.tick(config);
                tickSessions();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    void updateConfig(MiningConfig config, ItemAttributesConfig itemAttributesConfig) {
        this.config = config;
        this.buffResolver.updateItemAttributesConfig(itemAttributesConfig);
        this.fortuneManager.updateMappings(config.fortuneMappings());
        clearAllSessions();
    }

    void shutdown() {
        clearAllSessions();
        for (Player player : Bukkit.getOnlinePlayers()) {
            suppressor.remove(player);
        }
    }

    ToolStats resolveTool(Player player) {
        return calculator.resolveTool(player, config);
    }

    List<String> resolveToolCandidates(Player player) {
        return toolResolver.candidates(player.getInventory().getItemInMainHand(), config);
    }

    List<String> resolveBlockCandidates(Block block) {
        return blockResolver.candidates(block, config);
    }

    MiningCalculator calculator() {
        return calculator;
    }

    MiningBuffResolver buffResolver() {
        return buffResolver;
    }

    void handleAnimation(PlayerAnimationEvent event) {
        if (!config.enabled() || shouldBypass(event.getPlayer())) return;

        Player player = event.getPlayer();
        MiningSession session = sessionManager.getSession(player.getUniqueId());
        if (session != null) return;

        // Prevent auto-mining when just placing a block
        long lastPlace = placementCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (System.currentTimeMillis() - lastPlace < 250) {
            return;
        }

        // Facilitate "pre-mining" and seamless regrow:
        Block target = rayTraceTarget(player);
        if (target == null) return;

        MiningRule rule = config.findBlockRule(blockResolver.candidates(target, config));
        if (rule != null || regenerationManager.isRegenerating(target.getLocation())) {
            sessionManager.startSession(player, target);
        }
    }

    void handleBlockDamage(BlockDamageEvent event) {
        if (!config.enabled() || shouldBypass(event.getPlayer())) return;

        Block block = event.getBlock();
        Player player = event.getPlayer();
        
        // Ensure player is within reach for the event to matter
        double reach = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).getValue();
        if (player.getLocation().distanceSquared(block.getLocation().add(0.5, 0.5, 0.5)) > Math.pow(reach + 1.25, 2)) {
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
        MiningSession existing = sessionManager.getSession(player.getUniqueId());
        if (existing != null && existing.sameBlock(block)) {
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

    void handleBlockBreak(BlockBreakEvent event) {
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

    void handleCancelledBreakVisual(BlockBreakEvent event) {
        Block block = event.getBlock();
        MiningRule rule = config.findBlockRule(blockResolver.candidates(block, config));
        if (isControlledBlock(rule)) {
            resetClientBlock(event.getPlayer(), block, true);
        }
    }

    void handleBlockDamageAbort(BlockDamageAbortEvent event) {
        Block block = event.getBlock();
        if (sessionManager.isTransitioning(BlockKey.from(block))) {
            return; // Ignore client aborts during regrow
        }

        MiningSession session = sessionManager.getSession(event.getPlayer().getUniqueId());
        if (session != null && session.sameBlock(block)) {
            clearSession(event.getPlayer());
        }
    }

    void handleQuit(PlayerQuitEvent event) {
        clearSession(event.getPlayer());
        placementCooldowns.remove(event.getPlayer().getUniqueId());
    }

    void handleChangedWorld(PlayerChangedWorldEvent event) {
        clearSession(event.getPlayer());
    }

    private void tickSessions() {
        Iterator<MiningSession> iterator = sessionManager.getActiveSessions().values().iterator();
        while (iterator.hasNext()) {
            MiningSession session = iterator.next();
            Player player = Bukkit.getPlayer(session.playerId());
            if (player == null || !player.isOnline() || shouldBypass(player)) {
                iterator.remove();
                continue;
            }

            Block block = session.block();
            if (!isCloseEnough(player, block) || !isStillTargeting(player, session)) {
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

            if (isRegen) {
                continue; // Wait for regrowth
            }

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
                List<ItemStack> drops;
                if (!rule.customDrops().isEmpty()) {
                    drops = new java.util.ArrayList<>();
                    for (MiningRule.DropRule dropRule : rule.customDrops()) {
                        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(dropRule.material());
                        if (mat != null) {
                            int amount = dropRule.min();
                            if (dropRule.max() > dropRule.min()) {
                                amount = java.util.concurrent.ThreadLocalRandom.current().nextInt(dropRule.min(), dropRule.max() + 1);
                            }
                            if (amount > 0) drops.add(new ItemStack(mat, amount));
                        }
                    }
                } else {
                    drops = new java.util.ArrayList<>(block.getDrops(player.getInventory().getItemInMainHand(), player));
                }

                MiningBuffTotals buffs = buffResolver.resolve(player, config);
                fortuneManager.applyFortune(player, block, drops, buffs);
                playBreakEffects(block, originalData);

                boolean useAutoPickup = rule.autoPickupOverride() != null ? rule.autoPickupOverride() : config.autoPickup();

                if (useAutoPickup) {
                    for (ItemStack drop : drops) {
                        Map<Integer, ItemStack> overflow = player.getInventory().addItem(drop);
                        for (ItemStack item : overflow.values()) {
                            popItem(player.getLocation(), item);
                        }
                    }
                } else {
                    for (ItemStack drop : drops) {
                        popItem(block.getLocation(), drop);
                    }
                }

                if (rule.canRegenerate()) {
                    if (useAutoPickup) block.setType(org.bukkit.Material.AIR, false);
                    regenerationManager.queueRegeneration(block, originalData, rule, () -> {
                        BlockKey key = BlockKey.from(block);
                        sessionManager.markTransitioning(key);
                        for (MiningSession s : sessionManager.getActiveSessions().values()) {
                            if (s.blockKey().equals(key)) {
                                Player p = Bukkit.getPlayer(s.playerId());
                                if (p != null && p.isOnline()) {
                                    resetClientBlock(p, block, false);
                                    sessionManager.startSession(p, block);
                                }
                            }
                        }
                        Bukkit.getScheduler().runTaskLater(plugin, () -> sessionManager.unmarkTransitioning(key), 2L);
                    });
                } else {
                    block.setType(org.bukkit.Material.AIR, true);
                }
                
                clearSession(player);
            } else if (player.breakBlock(block)) {
                playBreakEffects(block, originalData);
            }
        } finally {
            sessionManager.unmarkCompleting(player.getUniqueId());
        }
    }

    private boolean shouldBypass(Player player) {
        if (!config.bypassCreative()) return false;
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }

    private boolean isControlledBlock(MiningRule rule) {
        return rule != null || !config.controlConfiguredBlocksOnly();
    }

    private boolean isCloseEnough(Player player, Block block) {
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
            player.getEyeLocation(), 
            player.getLocation().getDirection(), 
            reach, 
            FluidCollisionMode.NEVER, 
            false // include passable blocks (crops)
        );
        return result != null ? result.getHitBlock() : null;
    }

    private void clearSession(Player player) {
        MiningSession removed = sessionManager.getSession(player.getUniqueId());
        if (removed != null) {
            sessionManager.endSession(player);
            clearAnimation(player, removed);
        }
    }

    private void clearAllSessions() {
        for (MiningSession session : sessionManager.getActiveSessions().values()) {
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
        double x = loc.getX() + (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 0.5) + 0.25;
        double y = loc.getY() + (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 0.5) + 0.25;
        double z = loc.getZ() + (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 0.5) + 0.25;
        
        Location spawnLoc = new Location(loc.getWorld(), x, y, z);
        org.bukkit.entity.Item dropped = loc.getWorld().dropItem(spawnLoc, item);
        
        double vx = (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 0.1) - 0.05;
        double vy = 0.2;
        double vz = (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 0.1) - 0.05;
        
        dropped.setVelocity(new org.bukkit.util.Vector(vx, vy, vz));
    }

    private void playBreakEffects(Block block, BlockData blockData) {
        // playEffect(STEP_SOUND) is the internal Minecraft way to play 
        // the standard block break sound AND particles for a specific block data.
        block.getWorld().playEffect(block.getLocation(), org.bukkit.Effect.STEP_SOUND, blockData);
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
