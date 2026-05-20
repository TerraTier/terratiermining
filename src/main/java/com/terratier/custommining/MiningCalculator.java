package com.terratier.custommining;

import org.bukkit.entity.Player;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.NamespacedKey;

final class MiningCalculator {
    private final ToolIdentityResolver toolResolver;
    private final MiningBuffResolver buffResolver;
    private final NamespacedKey vanillaSuppressionKey;

    MiningCalculator(ToolIdentityResolver toolResolver, MiningBuffResolver buffResolver, NamespacedKey vanillaSuppressionKey) {
        this.toolResolver = toolResolver;
        this.buffResolver = buffResolver;
        this.vanillaSuppressionKey = vanillaSuppressionKey;
    }

    ToolStats resolveTool(Player player, MiningConfig config) {
        double baseSpeed = readBreakSpeedAttribute(player, config.baseMiningSpeed());
        ToolStats baseTool = toolResolver.resolve(player.getInventory().getItemInMainHand(), config, baseSpeed);
        MiningBuffTotals buffs = buffResolver.resolve(player, config);
        
        if (!buffs.hasBuffs()) {
            return baseTool;
        }

        // Scalable attribute resolution using prefix helpers
        double speedAdd = buffs.getTotalAdd("mining_speed") + buffs.getTotalAdd("speed");
        double speedMultiplier = buffs.getTotalMultiplier("mining_speed") * buffs.getTotalMultiplier("speed");
        
        double speed = Math.max(0.0, (baseTool.speed() + speedAdd) * speedMultiplier);
        return new ToolStats(baseTool.id(), speed, baseTool.type(), baseTool.tier(), baseTool.source() + buffs.describe());
    }

    private double readBreakSpeedAttribute(Player player, double fallback) {
        AttributeInstance instance = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (instance == null) {
            return fallback;
        }
        return Math.max(0.0, attributeValueWithoutSuppression(instance));
    }

    private double attributeValueWithoutSuppression(AttributeInstance instance) {
        double base = instance.getBaseValue();
        double value = base;

        // Op 0: Addition
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (vanillaSuppressionKey.equals(modifier.getKey())) continue;
            if (modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                value += modifier.getAmount();
            }
        }

        // Op 1: Scalar Addition
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (vanillaSuppressionKey.equals(modifier.getKey())) continue;
            if (modifier.getOperation() == AttributeModifier.Operation.ADD_SCALAR) {
                value += base * modifier.getAmount();
            }
        }

        // Op 2: Multiplicative
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (vanillaSuppressionKey.equals(modifier.getKey())) continue;
            if (modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1) {
                value *= (1.0 + modifier.getAmount());
            }
        }

        return value;
    }
}
