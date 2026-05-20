package com.terratier.custommining;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class VanillaMiningSuppressor {
    private final NamespacedKey suppressionKey;
    private final AttributeModifier suppressionModifier;
    private final BlockIdentityResolver blockResolver;

    VanillaMiningSuppressor(Plugin plugin, BlockIdentityResolver blockResolver) {
        this.suppressionKey = new NamespacedKey(plugin, "vanilla_mining_suppression");
        this.suppressionModifier = new AttributeModifier(
            suppressionKey,
            -1.0,
            AttributeModifier.Operation.MULTIPLY_SCALAR_1
        );
        this.blockResolver = blockResolver;
    }

    NamespacedKey getKey() {
        return suppressionKey;
    }

    void tick(MiningConfig config) {
        if (!config.enabled() || !config.suppressVanillaMining()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                remove(player);
            }
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (shouldBypass(player, config)) {
                remove(player);
                continue;
            }

            if (!config.controlConfiguredBlocksOnly()) {
                apply(player);
                continue;
            }

            double reach = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).getValue();
            Block target = player.getTargetBlockExact((int) Math.ceil(reach));
            if (target == null) {
                remove(player);
                continue;
            }

            MiningRule rule = config.findBlockRule(blockResolver.candidates(target, config));
            if (rule != null || !config.controlConfiguredBlocksOnly()) {
                apply(player);
            } else {
                remove(player);
            }
        }
    }

    void apply(Player player) {
        AttributeInstance instance = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (instance != null && instance.getModifier(suppressionKey) == null) {
            instance.addTransientModifier(suppressionModifier);
        }
    }

    void remove(Player player) {
        AttributeInstance instance = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (instance != null && instance.getModifier(suppressionKey) != null) {
            instance.removeModifier(suppressionKey);
        }
    }

    private boolean shouldBypass(Player player, MiningConfig config) {
        if (!config.bypassCreative()) return false;
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }
}
