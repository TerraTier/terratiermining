package com.terratier.custommining.service;

import com.terratier.custommining.config.MiningBuffRule;
import com.terratier.custommining.config.MiningConfig;
import com.terratier.custommining.model.BuffSourceType;
import com.terratier.custommining.model.MiningBuffTotals;
import com.terratier.custommining.util.Ids;
import com.terratier.stats.api.StatSourceType;
import com.terratier.stats.api.StatTotals;
import com.terratier.stats.api.TerraTierStatsApi;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class MiningBuffResolver {
    private final ToolIdentityResolver identityResolver;
    private final TerraTierStatsApi statsApi;
    private final Map<UUID, CachedTotals> resolutionCache = new HashMap<>();
    private long lastCacheTick = -1;

    public MiningBuffResolver(ToolIdentityResolver identityResolver, TerraTierStatsApi statsApi) {
        this.identityResolver = identityResolver;
        this.statsApi = statsApi;
    }

    public MiningBuffTotals resolve(Player player, MiningConfig config) {
        long currentTick = Bukkit.getCurrentTick();
        if (currentTick != lastCacheTick) {
            resolutionCache.clear();
            lastCacheTick = currentTick;
        }

        CachedTotals cached = resolutionCache.get(player.getUniqueId());
        if (cached != null) {
            return cached.totals;
        }

        StatTotals statTotals = statsApi.resolvePlayerStats(player, item -> identityResolver.candidates(item, config));
        MiningBuffTotals totals = convert(statTotals);
        totals = addLegacyMiningBuffRules(player, totals, config);

        resolutionCache.put(player.getUniqueId(), new CachedTotals(totals));
        return totals;
    }

    private MiningBuffTotals addLegacyMiningBuffRules(Player player, MiningBuffTotals totals, MiningConfig config) {
        PlayerInventory inventory = player.getInventory();
        Map<BuffSourceType, MiningBuffTotals> breakdown = new HashMap<>(totals.sourceBreakdown());

        totals = addLegacyItem(totals, breakdown, inventory.getItemInMainHand(), BuffSourceType.HELD, EquipmentSlot.HAND, config);
        totals = addLegacyItem(totals, breakdown, inventory.getItemInOffHand(), BuffSourceType.OFFHAND, EquipmentSlot.OFF_HAND, config);
        totals = addLegacyItem(totals, breakdown, inventory.getHelmet(), BuffSourceType.ARMOR, EquipmentSlot.HEAD, config);
        totals = addLegacyItem(totals, breakdown, inventory.getChestplate(), BuffSourceType.ARMOR, EquipmentSlot.CHEST, config);
        totals = addLegacyItem(totals, breakdown, inventory.getLeggings(), BuffSourceType.ARMOR, EquipmentSlot.LEGS, config);
        totals = addLegacyItem(totals, breakdown, inventory.getBoots(), BuffSourceType.ARMOR, EquipmentSlot.FEET, config);

        ItemStack[] storage = inventory.getStorageContents();
        int heldSlot = inventory.getHeldItemSlot();
        for (int slot = 0; slot < storage.length; slot++) {
            if (slot != heldSlot) {
                totals = addLegacyItem(totals, breakdown, storage[slot], BuffSourceType.INVENTORY, null, config);
            }
        }

        return new MiningBuffTotals(totals.attributes(), totals.sources(), breakdown);
    }

    private MiningBuffTotals addLegacyItem(
        MiningBuffTotals totals,
        Map<BuffSourceType, MiningBuffTotals> breakdown,
        ItemStack item,
        BuffSourceType sourceType,
        EquipmentSlot slot,
        MiningConfig config
    ) {
        MiningBuffTotals itemTotals = legacyItemTotals(item, sourceType, slot, config);
        if (!itemTotals.hasBuffs()) {
            return totals;
        }

        breakdown.put(sourceType, breakdown.getOrDefault(sourceType, MiningBuffTotals.none()).merge(itemTotals));
        return totals.merge(itemTotals);
    }

    private MiningBuffTotals legacyItemTotals(
        ItemStack item,
        BuffSourceType sourceType,
        EquipmentSlot slot,
        MiningConfig config
    ) {
        if (item == null || item.getType().isAir()) {
            return MiningBuffTotals.none();
        }

        MiningBuffTotals totals = MiningBuffTotals.none();
        for (String candidate : identityResolver.candidates(item, config)) {
            MiningBuffRule rule = config.findBuffRule(candidate);
            if (rule == null || !rule.appliesTo(sourceType, slot)) {
                continue;
            }

            for (Map.Entry<String, Double> entry : rule.attributes().entrySet()) {
                String key = Ids.normalizeAttribute(entry.getKey());
                boolean multiply = key.endsWith("_multiplier") || key.endsWith("-multiplier");
                totals = totals.add(key, entry.getValue(), multiply);
            }
            break;
        }
        return totals;
    }

    private MiningBuffTotals convert(StatTotals totals) {
        Map<BuffSourceType, MiningBuffTotals> breakdown = new HashMap<>();
        totals.sourceBreakdown().forEach((sourceType, sourceTotals) -> {
            breakdown.put(toBuffSourceType(sourceType), new MiningBuffTotals(new HashMap<>(sourceTotals.values()), sourceTotals.sources()));
        });
        return new MiningBuffTotals(new HashMap<>(totals.values()), totals.sources(), breakdown);
    }

    private BuffSourceType toBuffSourceType(StatSourceType sourceType) {
        return switch (sourceType) {
            case HELD -> BuffSourceType.HELD;
            case OFFHAND -> BuffSourceType.OFFHAND;
            case ARMOR -> BuffSourceType.ARMOR;
            case INVENTORY -> BuffSourceType.INVENTORY;
        };
    }

    private record CachedTotals(MiningBuffTotals totals) {
    }
}
