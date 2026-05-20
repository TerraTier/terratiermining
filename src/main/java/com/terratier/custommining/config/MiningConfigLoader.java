package com.terratier.custommining.config;

import com.terratier.custommining.model.BuffSourceType;
import com.terratier.custommining.model.ToolType;
import com.terratier.custommining.util.Ids;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

/**
 * Loads and builds {@link MiningConfig} instances.
 */
public final class MiningConfigLoader {
    private final Plugin plugin;

    public MiningConfigLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public MiningConfig load() {
        FileConfiguration config = plugin.getConfig();

        boolean enabled = config.getBoolean("settings.enabled", true);
        boolean configuredOnly = config.getBoolean("settings.control-configured-blocks-only", false);
        boolean bypassCreative = config.getBoolean("settings.bypass-creative", true);
        boolean suppressVanillaMining = config.getBoolean("settings.suppress-vanilla-mining", true);
        boolean autoPickup = config.getBoolean("settings.auto-pickup", true);
        int defaultRegenDelay = Math.max(0, config.getInt("settings.default-regeneration-delay", 100));
        double baseSpeed = Math.max(0.0, config.getDouble("settings.base-mining-speed", 1.0));
        int breakCooldownMs = config.getInt("settings.break-cooldown-ms", 200);

        List<NamespacedKey> pdcKeys = loadPdcKeys(config);
        DefinitionSet definitions = loadDefinitions(defaultRegenDelay);
        Map<String, List<String>> fortuneMappings = loadFortuneMappings(config);

        return new MiningConfig(
            enabled,
            configuredOnly,
            bypassCreative,
            suppressVanillaMining,
            autoPickup,
            defaultRegenDelay,
            baseSpeed,
            breakCooldownMs,
            pdcKeys,
            definitions.customBlockIdentities(),
            definitions.blockRules(),
            definitions.toolRules(),
            definitions.buffRules(),
            fortuneMappings
        );
    }

    private List<NamespacedKey> loadPdcKeys(FileConfiguration config) {
        List<NamespacedKey> pdcKeys = new ArrayList<>();
        for (String key : config.getStringList("settings.custom-item-pdc-keys")) {
            NamespacedKey namespacedKey = NamespacedKey.fromString(key);
            if (namespacedKey != null) {
                pdcKeys.add(namespacedKey);
            } else {
                plugin.getLogger().warning("Ignoring invalid PDC key: " + key);
            }
        }
        return pdcKeys;
    }

    private Map<String, List<String>> loadFortuneMappings(FileConfiguration config) {
        Map<String, List<String>> mappings = new HashMap<>();
        
        ConfigurationSection fortuneSection = config.getConfigurationSection("fortune-mappings");
        if (fortuneSection != null) {
            for (String attr : fortuneSection.getKeys(false)) {
                mappings.put(attr, fortuneSection.getStringList(attr));
            }
        }
        
        File fortuneFile = new File(plugin.getDataFolder(), "fortune.yml");
        if (fortuneFile.exists()) {
            YamlConfiguration fortuneConfig = YamlConfiguration.loadConfiguration(fortuneFile);
            ConfigurationSection section = fortuneConfig.getConfigurationSection("fortune-mappings");
            if (section != null) {
                for (String attr : section.getKeys(false)) {
                    mappings.put(attr, section.getStringList(attr));
                }
            }
        }
        return mappings;
    }

    private DefinitionSet loadDefinitions(int defaultRegenDelay) {
        List<BlockIdentityRule> customBlockIdentities = new ArrayList<>();
        Map<String, MiningRule> blockRules = new LinkedHashMap<>();
        Map<String, ToolRule> toolRules = new HashMap<>();
        Map<String, MiningBuffRule> buffRules = new HashMap<>();

        Path definitionsDir = plugin.getDataFolder().toPath().resolve("definitions");
        if (Files.exists(definitionsDir)) {
            try (var stream = Files.walk(definitionsDir)) {
                stream.filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                    .forEach(path -> {
                        FileConfiguration definitions = YamlConfiguration.loadConfiguration(path.toFile());
                        loadDefinitionsFromFile(definitions, customBlockIdentities, blockRules, toolRules, buffRules, defaultRegenDelay);
                    });
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to load mining definitions: " + e.getMessage());
            }
        }

        return new DefinitionSet(customBlockIdentities, blockRules, toolRules, buffRules);
    }

    private void loadDefinitionsFromFile(
        FileConfiguration config,
        List<BlockIdentityRule> blockIdentities,
        Map<String, MiningRule> blockRules,
        Map<String, ToolRule> toolRules,
        Map<String, MiningBuffRule> buffRules,
        int defaultRegenDelay
    ) {
        ConfigurationSection blockSection = config.getConfigurationSection("blocks");
        if (blockSection != null) {
            for (String id : blockSection.getKeys(false)) {
                ConfigurationSection entry = blockSection.getConfigurationSection(id);
                if (entry != null) {
                    if (entry.contains("material") || entry.contains("block-data-contains")) {
                        addBlockIdentity(blockIdentities, id, entry);
                    }
                    addBlockRule(blockRules, id, entry, defaultRegenDelay);
                }
            }
        }

        ConfigurationSection toolSection = config.getConfigurationSection("tools");
        if (toolSection != null) {
            for (String id : toolSection.getKeys(false)) {
                ConfigurationSection entry = toolSection.getConfigurationSection(id);
                if (entry != null) {
                    addToolRule(toolRules, id, entry);
                }
            }
        }

        ConfigurationSection buffSection = config.getConfigurationSection("buffs");
        if (buffSection != null) {
            for (String id : buffSection.getKeys(false)) {
                ConfigurationSection entry = buffSection.getConfigurationSection(id);
                if (entry != null) {
                    addBuffRule(buffRules, id, entry);
                }
            }
        }
    }

    private void addBlockIdentity(List<BlockIdentityRule> rules, String id, ConfigurationSection entry) {
        String material = Ids.normalize(entry.getString("material", ""));
        List<String> contains = entry.getStringList("block-data-contains").stream()
            .map(value -> value.toLowerCase().trim())
            .filter(value -> !value.isEmpty())
            .toList();

        rules.add(new BlockIdentityRule(id, material, contains));
    }

    private void addBlockRule(Map<String, MiningRule> blockRules, String rawId, ConfigurationSection entry, int defaultRegenDelay) {
        String id = Ids.normalize(rawId);
        double strength = Math.max(0.01, firstDouble(entry, 1.0, "strength", "block-strength", "block_strength"));
        ToolType requiredTool = ToolType.fromConfig(entry.getString("tool", "any"), ToolType.ANY);
        int minTier = Math.max(0, firstInt(entry, 0, "min-tier", "min_tier", "tier"));
        boolean canRegen = entry.getBoolean("can-regenerate", false) || entry.getBoolean("can_regenerate", false);
        
        int regenDelay = entry.contains("regeneration-delay") ? entry.getInt("regeneration-delay") : 
                        entry.contains("regeneration_delay") ? entry.getInt("regeneration_delay") :
                        entry.contains("regen-delay") ? entry.getInt("regen-delay") : defaultRegenDelay;
        
        String regenPlaceholder = entry.getString("regeneration-placeholder", entry.getString("regeneration_placeholder"));
        
        List<MiningRule.LootTable> tables = new ArrayList<>();
        if (entry.contains("drops")) {
            List<?> dropsList = entry.getList("drops");
            if (dropsList != null && !dropsList.isEmpty() && dropsList.get(0) instanceof Map) {
                // New format: list of tables
                for (Map<?, ?> tableMap : entry.getMapList("drops")) {
                    parseTable(tableMap).ifPresent(tables::add);
                }
            } else if (entry.isConfigurationSection("drops")) {
                // Old format: single table as section
                parseTable(entry.getConfigurationSection("drops").getValues(false)).ifPresent(tables::add);
            } else if (entry.isList("drops")) {
                // Old format: simple list of strings
                List<MiningRule.DropRule> pool = new ArrayList<>();
                for (String rawDrop : entry.getStringList("drops")) {
                    MiningRule.DropRule.fromConfig(rawDrop).ifPresent(pool::add);
                }
                tables.add(new MiningRule.LootTable(MiningRule.DropStrategy.POOLED, 1, 1, pool));
            }
        }
        
        Boolean autoPickupOverride = null;
        if (entry.contains("auto-pickup")) {
            autoPickupOverride = entry.getBoolean("auto-pickup");
        } else if (entry.contains("auto_pickup")) {
            autoPickupOverride = entry.getBoolean("auto_pickup");
        }

        blockRules.put(id, new MiningRule(id, strength, requiredTool, minTier, canRegen, regenDelay, regenPlaceholder, tables, autoPickupOverride));
    }

    private java.util.Optional<MiningRule.LootTable> parseTable(Map<?, ?> map) {
        Object strategyObj = map.get("strategy");
        String strategyStr = (strategyObj != null ? String.valueOf(strategyObj) : "POOLED").toUpperCase();
        
        if (strategyStr.equals("INDEPENDENT") || strategyStr.equals("ROLL_EACH")) strategyStr = "INDEPENDENT";
        else if (strategyStr.equals("WEIGHTED") || strategyStr.equals("PICK_ONE")) strategyStr = "POOLED";
        else if (strategyStr.equals("STATIC") || strategyStr.equals("FIXED")) strategyStr = "STATIC";
        else if (strategyStr.equals("ALL")) strategyStr = "POOLED";

        MiningRule.DropStrategy strategy;
        try {
            strategy = MiningRule.DropStrategy.valueOf(strategyStr);
        } catch (IllegalArgumentException e) {
            strategy = MiningRule.DropStrategy.POOLED;
        }

        int minRolls = 1;
        int maxRolls = 1;
        Object rollsObj = map.get("rolls");
        String rollsStr = rollsObj != null ? String.valueOf(rollsObj) : "1";
        
        if (rollsStr.contains("-")) {
            String[] parts = rollsStr.split("-");
            try {
                minRolls = Integer.parseInt(parts[0].trim());
                maxRolls = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {}
        } else {
            try {
                minRolls = maxRolls = Integer.parseInt(rollsStr);
            } catch (NumberFormatException ignored) {}
        }

        List<MiningRule.DropRule> pool = new ArrayList<>();
        Object poolObj = map.get("pool");
        if (poolObj instanceof List<?> list) {
            for (Object item : list) {
                MiningRule.DropRule.fromConfig(String.valueOf(item)).ifPresent(pool::add);
            }
        }

        if (pool.isEmpty()) return java.util.Optional.empty();
        return java.util.Optional.of(new MiningRule.LootTable(strategy, minRolls, maxRolls, pool));
    }

    private void addToolRule(Map<String, ToolRule> toolRules, String rawId, ConfigurationSection entry) {
        String id = Ids.normalize(rawId);
        Double speed = containsAny(entry, "speed", "mining-speed", "mining_speed")
            ? Math.max(0.0, firstDouble(entry, 0.0, "speed", "mining-speed", "mining_speed"))
            : null;
        ToolType type = ToolType.fromConfig(entry.getString("type", "hand"), ToolType.HAND);
        int tier = Math.max(0, entry.getInt("tier", 0));
        toolRules.put(id, new ToolRule(id, speed, type, tier));
    }

    private void addBuffRule(Map<String, MiningBuffRule> buffRules, String rawId, ConfigurationSection entry) {
        String id = Ids.normalize(rawId);
        Map<String, Double> attributes = new HashMap<>();
        
        Set<String> reservedKeys = Set.of("applies", "apply", "sources", "source", "slots", "slot");
        for (String key : entry.getKeys(false)) {
            if (reservedKeys.contains(key.toLowerCase())) {
                continue;
            }
            if (entry.isDouble(key) || entry.isInt(key)) {
                attributes.put(Ids.normalizeAttribute(key), entry.getDouble(key));
            }
        }

        Set<BuffSourceType> applies = readSet(entry, "applies", "apply", "sources", "source").stream()
            .map(s -> {
                try { return BuffSourceType.valueOf(s.toUpperCase()); } catch (Exception e) { return null; }
            })
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());

        Set<EquipmentSlot> slots = readSet(entry, "slots", "slot").stream()
            .map(s -> {
                try { return EquipmentSlot.valueOf(s.toUpperCase()); } catch (Exception e) { return null; }
            })
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());

        buffRules.put(id, new MiningBuffRule(id, applies, slots, attributes));
    }

    private List<String> readSet(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            if (section.isString(key)) {
                String val = section.getString(key);
                if (val != null && (val.equalsIgnoreCase("any") || val.equalsIgnoreCase("all"))) return List.of();
                return List.of(val);
            }
            if (section.isList(key)) {
                return section.getStringList(key);
            }
        }
        return List.of();
    }

    private double firstDouble(ConfigurationSection section, double fallback, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) return section.getDouble(key);
        }
        return fallback;
    }

    private int firstInt(ConfigurationSection section, int fallback, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) return section.getInt(key);
        }
        return fallback;
    }

    private boolean containsAny(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) return true;
        }
        return false;
    }

    public record DefinitionSet(
        List<BlockIdentityRule> customBlockIdentities,
        Map<String, MiningRule> blockRules,
        Map<String, ToolRule> toolRules,
        Map<String, MiningBuffRule> buffRules
    ) {}
}
