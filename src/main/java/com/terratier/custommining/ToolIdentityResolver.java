package com.terratier.custommining;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

final class ToolIdentityResolver {
    List<String> candidates(ItemStack item, MiningConfig config) {
        Set<String> ids = new LinkedHashSet<>();

        if (item == null || item.getType().isAir()) {
            ids.add("hand");
            ids.add("minecraft:air");
            return new ArrayList<>(ids);
        }

        Material material = item.getType();
        String materialId = material.getKey().asString();

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer data = meta.getPersistentDataContainer();
            for (NamespacedKey key : config.customItemPdcKeys()) {
                String value = data.get(key, PersistentDataType.STRING);
                if (value != null && !value.isBlank()) {
                    ids.add(Ids.normalize(value));
                }
            }

            if (meta.hasItemModel()) {
                ids.add(meta.getItemModel().asString());
            }

            if (meta.hasCustomModelData()) {
                ids.add(materialId + "#" + meta.getCustomModelData());
            }
        }

        ids.add(materialId);
        return new ArrayList<>(ids);
    }

    ToolStats resolve(ItemStack item, MiningConfig config, double attributeSpeed) {
        List<String> candidateIds = candidates(item, config);
        ToolRule rule = config.findToolRule(candidateIds);
        ToolType inferredType = inferType(item);
        int inferredTier = inferTier(item);
        double speed = Math.max(config.baseMiningSpeed(), attributeSpeed);
        String id = candidateIds.isEmpty() ? "hand" : candidateIds.get(0);
        String source = "attribute/base";

        if (rule != null) {
            id = rule.id();
            inferredType = rule.type();
            inferredTier = rule.tier();
            if (rule.speed() != null) {
                speed = rule.speed();
                source = "config";
            } else {
                source = "config type + attribute/base";
            }
        }

        return new ToolStats(id, speed, inferredType, inferredTier, source);
    }

    private ToolType inferType(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return ToolType.HAND;
        }

        String name = item.getType().name().toLowerCase(Locale.ROOT);
        if (name.endsWith("_pickaxe")) {
            return ToolType.PICKAXE;
        }
        if (name.endsWith("_axe")) {
            return ToolType.AXE;
        }
        if (name.endsWith("_shovel")) {
            return ToolType.SHOVEL;
        }
        if (name.endsWith("_hoe")) {
            return ToolType.HOE;
        }
        if (name.equals("shears")) {
            return ToolType.SHEARS;
        }
        if (name.endsWith("_sword")) {
            return ToolType.SWORD;
        }
        return ToolType.HAND;
    }

    private int inferTier(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }

        String name = item.getType().name().toLowerCase(Locale.ROOT);
        if (name.startsWith("netherite_")) {
            return 5;
        }
        if (name.startsWith("diamond_")) {
            return 4;
        }
        if (name.startsWith("iron_")) {
            return 3;
        }
        if (name.startsWith("stone_")) {
            return 2;
        }
        if (name.startsWith("wooden_") || name.startsWith("golden_")) {
            return 1;
        }
        return 0;
    }
}
