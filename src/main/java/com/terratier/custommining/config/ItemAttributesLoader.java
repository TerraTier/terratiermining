package com.terratier.custommining.config;

import com.terratier.custommining.model.TerraTierItemAttribute;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Loads and manages item attribute mappings from item-attributes.yml.
 */
public final class ItemAttributesLoader {
    private final Plugin plugin;

    public ItemAttributesLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public ItemAttributesConfig load() {
        Map<String, List<TerraTierItemAttribute>> attributesByItemId = new HashMap<>();
        try {
            File configFile = new File(plugin.getDataFolder(), "item-attributes.yml");
            if (!configFile.exists()) {
                plugin.saveResource("item-attributes.yml", false);
            }
            
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

            for (String itemId : yaml.getKeys(false)) {
                if (itemId.startsWith("#") || itemId.startsWith("//")) continue;

                ConfigurationSection itemSection = yaml.getConfigurationSection(itemId);
                if (itemSection == null) continue;

                List<TerraTierItemAttribute> attrs = new ArrayList<>();
                List<Map<?, ?>> attributesList = itemSection.getMapList("attributes");
                for (Map<?, ?> attrMap : attributesList) {
                    String attrName = stringValue(attrMap.get("attribute"));
                    Number val = numberValue(attrMap.get("value"));
                    String slot = stringValue(attrMap.get("slot"));
                    String source = stringValue(attrMap.get("source"));

                    if (attrName == null || attrName.isBlank() || val == null) continue;

                    attrs.add(new TerraTierItemAttribute(attrName, val.doubleValue(), source, slot));
                }

                if (!attrs.isEmpty()) {
                    attributesByItemId.put(itemId, attrs);
                }
            }

            plugin.getLogger().info("[TerraTier] Loaded " + attributesByItemId.size() + " items from item-attributes.yml");
        } catch (Exception ex) {
            plugin.getLogger().warning("[TerraTier] Failed to load item-attributes.yml: " + ex.getMessage());
        }
        return new ItemAttributesConfig(attributesByItemId);
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static Number numberValue(Object value) {
        if (value instanceof Number number) return number;
        if (value instanceof String string) {
            try { return Double.parseDouble(string.trim()); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }
}
