package com.terratier.custommining.service;

import com.terratier.custommining.config.MiningConfig;
import com.terratier.custommining.config.ToolRule;
import com.terratier.custommining.model.ToolStats;
import com.terratier.custommining.model.ToolType;
import com.terratier.custommining.util.Ids;
import com.terratier.stats.api.StatSourceType;
import com.terratier.stats.api.StatTotals;
import com.terratier.stats.api.TerraTierStatsApi;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ToolIdentityResolver {
    private final TerraTierStatsApi statsApi;

    public ToolIdentityResolver(TerraTierStatsApi statsApi) {
        this.statsApi = statsApi;
    }

    public List<String> candidates(ItemStack item, MiningConfig config) {
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

    public ToolStats resolve(ItemStack item, MiningConfig config, double attributeSpeed) {
        List<String> candidateIds = candidates(item, config);
        ToolRule rule = config.findToolRule(candidateIds);
        ToolType inferredType = inferType(item);
        int inferredTier = inferTier(item);
        double speed = Math.max(config.baseMiningSpeed(), attributeSpeed);
        String id = candidateIds.isEmpty() ? "hand" : candidateIds.get(0);
        String source = "attribute/base";
        Integer pdcTier = readStatTier(item, candidateIds);

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

        if (pdcTier != null) {
            inferredTier = pdcTier;
            source = source + " + pdc tier";
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

    private Integer readStatTier(ItemStack item, List<String> candidateIds) {
        if (statsApi == null || item == null || item.getType().isAir()) {
            return null;
        }

        StatTotals totals = statsApi.aggregate(
            statsApi.resolveItemStats(item, candidateIds, StatSourceType.HELD, EquipmentSlot.HAND)
        );
        double tier = totals.get("tier", Double.NaN);
        if (Double.isNaN(tier)) {
            return null;
        }
        return Math.max(0, (int) tier);
    }
}
