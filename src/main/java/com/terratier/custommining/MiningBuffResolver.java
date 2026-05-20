package com.terratier.custommining;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

final class MiningBuffResolver {
    private static final NamespacedKey ATTRIBUTES_KEY = NamespacedKey.fromString("terratier:attributes");
    private static final NamespacedKey LEGACY_ATTRIBUTES_JSON_KEY = NamespacedKey.fromString("terratier:attributes_json");
    private static final NamespacedKey MINING_SPEED_KEY = NamespacedKey.fromString("terratier:mining_speed");
    private static final NamespacedKey MINING_SPEED_ADD_KEY = NamespacedKey.fromString("terratier:mining_speed_add");
    private static final NamespacedKey MINING_SPEED_MULTIPLIER_KEY = NamespacedKey.fromString("terratier:mining_speed_multiplier");

    private final ToolIdentityResolver identityResolver;
    private final Plugin plugin;
    private ItemAttributesConfig itemAttributesConfig;

    MiningBuffResolver(
        ToolIdentityResolver identityResolver,
        Plugin plugin,
        ItemAttributesConfig itemAttributesConfig
    ) {
        this.identityResolver = identityResolver;
        this.plugin = plugin;
        this.itemAttributesConfig = itemAttributesConfig;
    }

    void updateItemAttributesConfig(ItemAttributesConfig itemAttributesConfig) {
        this.itemAttributesConfig = itemAttributesConfig;
    }

    private final Map<UUID, CachedTotals> resolutionCache = new HashMap<>();
    private long lastCacheTick = -1;

    MiningBuffTotals resolve(Player player, MiningConfig config) {
        long currentTick = Bukkit.getCurrentTick();
        if (currentTick != lastCacheTick) {
            resolutionCache.clear();
            lastCacheTick = currentTick;
        }

        CachedTotals cached = resolutionCache.get(player.getUniqueId());
        if (cached != null) {
            return cached.totals;
        }

        PlayerInventory inventory = player.getInventory();
        MiningBuffTotals totals = MiningBuffTotals.none();
        
        // Track sources for breakdown
        Map<BuffSourceType, MiningBuffTotals> sourceBreakdown = new HashMap<>();

        totals = addWithSource(totals, inventory.getItemInMainHand(), BuffSourceType.HELD, EquipmentSlot.HAND, config, sourceBreakdown);
        totals = addWithSource(totals, inventory.getItemInOffHand(), BuffSourceType.OFFHAND, EquipmentSlot.OFF_HAND, config, sourceBreakdown);
        totals = addWithSource(totals, inventory.getHelmet(), BuffSourceType.ARMOR, EquipmentSlot.HEAD, config, sourceBreakdown);
        totals = addWithSource(totals, inventory.getChestplate(), BuffSourceType.ARMOR, EquipmentSlot.CHEST, config, sourceBreakdown);
        totals = addWithSource(totals, inventory.getLeggings(), BuffSourceType.ARMOR, EquipmentSlot.LEGS, config, sourceBreakdown);
        totals = addWithSource(totals, inventory.getBoots(), BuffSourceType.ARMOR, EquipmentSlot.FEET, config, sourceBreakdown);

        ItemStack[] storage = inventory.getStorageContents();
        int heldSlot = inventory.getHeldItemSlot();
        for (int slot = 0; slot < storage.length; slot++) {
            if (slot != heldSlot) {
                totals = addWithSource(totals, storage[slot], BuffSourceType.INVENTORY, null, config, sourceBreakdown);
            }
        }

        totals = new MiningBuffTotals(totals.attributes(), totals.sources(), sourceBreakdown);
        resolutionCache.put(player.getUniqueId(), new CachedTotals(totals));
        return totals;
    }

    private MiningBuffTotals addWithSource(
        MiningBuffTotals totals,
        ItemStack item,
        BuffSourceType sourceType,
        EquipmentSlot slot,
        MiningConfig config,
        Map<BuffSourceType, MiningBuffTotals> breakdown
    ) {
        MiningBuffTotals before = totals;
        MiningBuffTotals after = addItem(totals, item, sourceType, slot, config);
        
        if (after != before) {
            // Calculate what was added
            Map<String, Double> added = new HashMap<>();
            after.attributes().forEach((k, v) -> {
                double diff = v - before.attributes().getOrDefault(k, k.contains("multiplier") ? 1.0 : 0.0);
                if (diff != 0) added.put(k, diff);
            });
            
            if (!added.isEmpty()) {
                MiningBuffTotals sourceTotal = breakdown.getOrDefault(sourceType, MiningBuffTotals.none());
                breakdown.put(sourceType, sourceTotal.merge(new MiningBuffTotals(added, 1)));
            }
        }
        return after;
    }

    private record CachedTotals(MiningBuffTotals totals) {}

    private MiningBuffTotals addItem(
        MiningBuffTotals totals,
        ItemStack item,
        BuffSourceType sourceType,
        EquipmentSlot slot,
        MiningConfig config
    ) {
        if (item == null || item.getType().isAir()) {
            return totals;
        }

        AttributeAccumulator accumulator = new AttributeAccumulator();
        for (TerraTierItemAttribute attribute : readPdcAttributes(item)) {
            accumulator.addIfApplicable(attribute, sourceType, slot);
        }
        for (TerraTierItemAttribute attribute : readConfiguredAttributes(item, config)) {
            accumulator.addIfApplicable(attribute, sourceType, slot);
        }

        MiningBuffTotals updated = totals;
        if (accumulator.hasValues()) {
            for (Map.Entry<String, Double> entry : accumulator.attributes.entrySet()) {
                boolean multiply = entry.getKey().endsWith("_multiplier") || entry.getKey().endsWith("-multiplier");
                updated = updated.add(entry.getKey(), entry.getValue(), multiply);
            }
        }

        for (String candidate : identityResolver.candidates(item, config)) {
            MiningBuffRule rule = config.findBuffRule(candidate);
            if (rule != null && rule.appliesTo(sourceType, slot)) {
                for (Map.Entry<String, Double> entry : rule.attributes().entrySet()) {
                    boolean multiply = entry.getKey().endsWith("_multiplier") || entry.getKey().endsWith("-multiplier");
                    updated = updated.add(entry.getKey(), entry.getValue(), multiply);
                }
                break;
            }
        }

        return updated;
    }

    private List<TerraTierItemAttribute> readPdcAttributes(ItemStack item) {
        if (!item.hasItemMeta()) {
            return List.of();
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        List<TerraTierItemAttribute> attributes = new ArrayList<>();

        readNumber(data, MINING_SPEED_KEY)
            .ifPresent(value -> attributes.add(TerraTierItemAttribute.inventory("mining_speed", value)));
        readNumber(data, MINING_SPEED_ADD_KEY)
            .ifPresent(value -> attributes.add(TerraTierItemAttribute.inventory("mining_speed", value)));
        readNumber(data, MINING_SPEED_MULTIPLIER_KEY)
            .ifPresent(value -> attributes.add(TerraTierItemAttribute.inventory("mining_speed_multiplier", value)));

        String json = data.get(ATTRIBUTES_KEY, PersistentDataType.STRING);
        if (json == null || json.isBlank()) {
            json = data.get(LEGACY_ATTRIBUTES_JSON_KEY, PersistentDataType.STRING);
        }
        if (json != null && !json.isBlank()) {
            attributes.addAll(parseAttributesJson(json));
        }

        return attributes;
    }

    private List<TerraTierItemAttribute> readConfiguredAttributes(ItemStack item, MiningConfig config) {
        if (itemAttributesConfig == null) {
            return List.of();
        }

        List<TerraTierItemAttribute> attributes = new ArrayList<>();
        for (String candidate : identityResolver.candidates(item, config)) {
            attributes.addAll(itemAttributesConfig.getAttributes(candidate));
        }
        return attributes;
    }

    private List<TerraTierItemAttribute> parseAttributesJson(String json) {
        List<TerraTierItemAttribute> attributes = new ArrayList<>();

        try {
            JsonElement root = JsonParser.parseString(json);
            JsonArray entries = new JsonArray();
            if (root.isJsonArray()) {
                entries = root.getAsJsonArray();
            } else if (root.isJsonObject()) {
                entries.add(root.getAsJsonObject());
            } else {
                return List.of();
            }

            for (JsonElement entry : entries) {
                if (!entry.isJsonObject()) {
                    continue;
                }

                TerraTierItemAttribute attribute = parseAttribute(entry.getAsJsonObject());
                if (attribute != null) {
                    attributes.add(attribute);
                }
            }
        } catch (JsonSyntaxException exception) {
            plugin.getLogger().warning("Ignoring malformed terratier:attributes payload: " + exception.getMessage());
        }

        return attributes;
    }

    private TerraTierItemAttribute parseAttribute(JsonObject object) {
        String attribute = firstString(object, "attribute", "id", "key", "name");
        if (attribute == null || attribute.isBlank()) {
            return null;
        }

        Double value = firstNumber(object, "value", "amount", "modifier");
        if (value == null) {
            return null;
        }

        return new TerraTierItemAttribute(
            Ids.normalizeAttribute(attribute),
            value,
            firstString(object, "source", "applies", "apply"),
            firstString(object, "slot", "slots")
        );
    }

    private java.util.Optional<Double> readNumber(PersistentDataContainer data, NamespacedKey key) {
        if (key == null) {
            return java.util.Optional.empty();
        }

        Double doubleValue = data.get(key, PersistentDataType.DOUBLE);
        if (doubleValue != null) {
            return java.util.Optional.of(doubleValue);
        }
        Float floatValue = data.get(key, PersistentDataType.FLOAT);
        if (floatValue != null) {
            return java.util.Optional.of((double) floatValue);
        }
        Integer intValue = data.get(key, PersistentDataType.INTEGER);
        if (intValue != null) {
            return java.util.Optional.of((double) intValue);
        }
        Long longValue = data.get(key, PersistentDataType.LONG);
        if (longValue != null) {
            return java.util.Optional.of((double) longValue);
        }
        String stringValue = data.get(key, PersistentDataType.STRING);
        if (stringValue != null) {
            try {
                return java.util.Optional.of(Double.parseDouble(stringValue.trim()));
            } catch (NumberFormatException ignored) {
                return java.util.Optional.empty();
            }
        }
        return java.util.Optional.empty();
    }

    private static String firstString(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value == null || value.isJsonNull()) {
                continue;
            }
            if (value.isJsonArray() && !value.getAsJsonArray().isEmpty()) {
                return value.getAsJsonArray().get(0).getAsString();
            }
            return value.getAsString();
        }
        return null;
    }

    private static Double firstNumber(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value == null || value.isJsonNull()) {
                continue;
            }
            try {
                return value.getAsDouble();
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static final class AttributeAccumulator {
        private final Map<String, Double> attributes = new HashMap<>();

        private void addIfApplicable(TerraTierItemAttribute attribute, BuffSourceType sourceType, EquipmentSlot slot) {
            if (!attribute.appliesTo(sourceType, slot)) {
                return;
            }

            String key = Ids.normalizeAttribute(attribute.attribute());
            boolean multiply = key.endsWith("_multiplier") || key.endsWith("-multiplier");
            if (multiply) {
                attributes.put(key, attributes.getOrDefault(key, 1.0) * Math.max(0.0, attribute.value()));
            } else {
                attributes.put(key, attributes.getOrDefault(key, 0.0) + attribute.value());
            }
        }

        private boolean hasValues() {
            return !attributes.isEmpty();
        }
    }

}
