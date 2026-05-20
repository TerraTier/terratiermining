package com.terratier.custommining;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

final class BlockRegenerationManager {
    private final Plugin plugin;
    private final Set<Location> regeneratingLocations = Collections.synchronizedSet(new HashSet<>());

    BlockRegenerationManager(Plugin plugin) {
        this.plugin = plugin;
    }

    boolean isRegenerating(Location loc) {
        return regeneratingLocations.contains(loc);
    }

    void queueRegeneration(Block block, BlockData originalData, MiningRule rule, Runnable onComplete) {
        if (!rule.canRegenerate()) return;

        Location loc = block.getLocation().clone();
        regeneratingLocations.add(loc);

        BlockData placeholder = null;

        // 1. Try custom placeholder from config
        if (rule.regenerationPlaceholder() != null && !rule.regenerationPlaceholder().isBlank()) {
            try {
                placeholder = Bukkit.createBlockData(rule.regenerationPlaceholder());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid regeneration-placeholder for " + rule.id() + ": " + rule.regenerationPlaceholder());
            }
        }

        // 2. Fallback logic
        if (placeholder == null) {
            if (originalData instanceof Ageable) {
                Ageable baby = (Ageable) originalData.clone();
                baby.setAge(0);
                placeholder = baby;
            } else {
                placeholder = Bukkit.createBlockData(Material.BEDROCK);
            }
        }

        block.setBlockData(placeholder, false);

        int delay = rule.regenerationDelay();
        if (delay <= 0) {
            // Instant respawn
            block.setBlockData(originalData, false);
            regeneratingLocations.remove(loc);
            if (onComplete != null) onComplete.run();
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                // Restore original state
                Block currentBlock = loc.getBlock();
                currentBlock.setBlockData(originalData, false);
                regeneratingLocations.remove(loc);
                if (onComplete != null) onComplete.run();
            }
        }.runTaskLater(plugin, delay);
    }
}
