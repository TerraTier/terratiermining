package com.terratier.custommining.config;

import com.terratier.custommining.model.TerraTierItemAttribute;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bukkit.plugin.Plugin;

/**
 * Immutable mapping of item attributes.
 */
public final class ItemAttributesConfig {
    private final Map<String, List<TerraTierItemAttribute>> attributesByItemId;

    public ItemAttributesConfig(Map<String, List<TerraTierItemAttribute>> attributesByItemId) {
        this.attributesByItemId = Map.copyOf(attributesByItemId);
    }

    public static ItemAttributesConfig load(Plugin plugin) {
        return new ItemAttributesLoader(plugin).load();
    }

    public List<TerraTierItemAttribute> getAttributes(String itemId) {
        return attributesByItemId.getOrDefault(itemId, Collections.emptyList());
    }
}
