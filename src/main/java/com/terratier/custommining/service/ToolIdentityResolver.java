package com.terratier.custommining.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.terratier.custommining.config.MiningConfig;
import com.terratier.custommining.config.ToolRule;
import com.terratier.custommining.model.ToolStats;
import com.terratier.custommining.model.ToolType;
import com.terratier.custommining.util.Ids;
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

public final class ToolIdentityResolver {
    private static final NamespacedKey ATTRIBUTES_KEY = NamespacedKey.fromString("terratier:attributes");
    private static final NamespacedKey LEGACY_ATTRIBUTES_JSON_KEY = NamespacedKey.fromString("terratier:attributes_json");
    private static final NamespacedKey TIER_KEY = NamespacedKey.fromString("terratier:tier");

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
        Integer pdcTier = readPdcTier(item);

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

    private Integer readPdcTier(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();

        Integer directTier = readInteger(data, TIER_KEY);
        if (directTier != null) {
            return directTier;
        }

        String json = data.get(ATTRIBUTES_KEY, PersistentDataType.STRING);
        if (json == null || json.isBlank()) {
            json = data.get(LEGACY_ATTRIBUTES_JSON_KEY, PersistentDataType.STRING);
        }
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            JsonElement root = JsonParser.parseString(json);
            JsonArray entries = new JsonArray();
            if (root.isJsonArray()) {
                entries = root.getAsJsonArray();
            } else if (root.isJsonObject()) {
                entries.add(root.getAsJsonObject());
            } else {
                return null;
            }

            for (JsonElement entry : entries) {
                if (!entry.isJsonObject()) {
                    continue;
                }

                Integer tier = parseTierAttribute(entry.getAsJsonObject());
                if (tier != null) {
                    return tier;
                }
            }
        } catch (JsonSyntaxException ignored) {
            return null;
        }

        return null;
    }

    private Integer parseTierAttribute(JsonObject object) {
        String attribute = firstString(object, "attribute", "id", "key", "name");
        if (!"tier".equals(Ids.normalizeAttribute(attribute))) {
            return null;
        }

        Double value = firstNumber(object, "value", "amount", "modifier");
        if (value == null) {
            return null;
        }

        return Math.max(0, value.intValue());
    }

    private Integer readInteger(PersistentDataContainer data, NamespacedKey key) {
        if (key == null) {
            return null;
        }

        Integer intValue = data.get(key, PersistentDataType.INTEGER);
        if (intValue != null) {
            return Math.max(0, intValue);
        }
        Long longValue = data.get(key, PersistentDataType.LONG);
        if (longValue != null) {
            return Math.max(0, longValue.intValue());
        }
        Double doubleValue = data.get(key, PersistentDataType.DOUBLE);
        if (doubleValue != null) {
            return Math.max(0, doubleValue.intValue());
        }
        Float floatValue = data.get(key, PersistentDataType.FLOAT);
        if (floatValue != null) {
            return Math.max(0, floatValue.intValue());
        }
        String stringValue = data.get(key, PersistentDataType.STRING);
        if (stringValue != null) {
            try {
                return Math.max(0, Integer.parseInt(stringValue.trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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
}
