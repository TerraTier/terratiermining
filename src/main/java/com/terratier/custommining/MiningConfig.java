package com.terratier.custommining;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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

final class MiningConfig {
    private final boolean enabled;
    private final boolean controlConfiguredBlocksOnly;
    private final boolean bypassCreative;
    private final boolean suppressVanillaMining;
    private final boolean autoPickup;
    private final int defaultRegenerationDelay;
    private final double baseMiningSpeed;
    private final List<NamespacedKey> customItemPdcKeys;
    private final List<BlockIdentityRule> customBlockIdentities;
    private final Map<String, MiningRule> blockRules;
    private final Map<String, ToolRule> toolRules;
    private final Map<String, MiningBuffRule> buffRules;
    private final Map<String, List<String>> fortuneMappings;

    private MiningConfig(
        boolean enabled,
        boolean controlConfiguredBlocksOnly,
        boolean bypassCreative,
        boolean suppressVanillaMining,
        boolean autoPickup,
        int defaultRegenerationDelay,
        double baseMiningSpeed,
        List<NamespacedKey> customItemPdcKeys,
        List<BlockIdentityRule> customBlockIdentities,
        Map<String, MiningRule> blockRules,
        Map<String, ToolRule> toolRules,
        Map<String, MiningBuffRule> buffRules,
        Map<String, List<String>> fortuneMappings
    ) {
        this.enabled = enabled;
        this.controlConfiguredBlocksOnly = controlConfiguredBlocksOnly;
        this.bypassCreative = bypassCreative;
        this.suppressVanillaMining = suppressVanillaMining;
        this.autoPickup = autoPickup;
        this.defaultRegenerationDelay = defaultRegenerationDelay;
        this.baseMiningSpeed = baseMiningSpeed;
        this.customItemPdcKeys = List.copyOf(customItemPdcKeys);
        this.customBlockIdentities = List.copyOf(customBlockIdentities);
        this.blockRules = Map.copyOf(blockRules);
        this.toolRules = Map.copyOf(toolRules);
        this.buffRules = Map.copyOf(buffRules);
        this.fortuneMappings = Map.copyOf(fortuneMappings);
    }

    static MiningConfig load(Plugin plugin) {
        FileConfiguration config = plugin.getConfig();

        boolean enabled = config.getBoolean("settings.enabled", true);
        boolean configuredOnly = config.getBoolean("settings.control-configured-blocks-only", false);
        boolean bypassCreative = config.getBoolean("settings.bypass-creative", true);
        boolean suppressVanillaMining = config.getBoolean("settings.suppress-vanilla-mining", true);
        boolean autoPickup = config.getBoolean("settings.auto-pickup", true);
        int defaultRegenDelay = Math.max(0, config.getInt("settings.default-regeneration-delay", 100));
        double baseSpeed = Math.max(0.0, config.getDouble("settings.base-mining-speed", 1.0));

        List<NamespacedKey> pdcKeys = new ArrayList<>();
        for (String key : config.getStringList("settings.custom-item-pdc-keys")) {
            NamespacedKey namespacedKey = NamespacedKey.fromString(key);
            if (namespacedKey != null) {
                pdcKeys.add(namespacedKey);
            } else {
                plugin.getLogger().warning("Ignoring invalid PDC key: " + key);
            }
        }

        DefinitionSet definitions = loadDefinitions(plugin, defaultRegenDelay);

        Map<String, List<String>> fortuneMappings = new HashMap<>();
        
        ConfigurationSection fortuneSection = config.getConfigurationSection("fortune-mappings");
        if (fortuneSection != null) {
            for (String attr : fortuneSection.getKeys(false)) {
                fortuneMappings.put(attr, fortuneSection.getStringList(attr));
            }
        }
        
        File fortuneFile = new File(plugin.getDataFolder(), "fortune.yml");
        if (fortuneFile.exists()) {
            YamlConfiguration fortuneConfig = YamlConfiguration.loadConfiguration(fortuneFile);
            ConfigurationSection section = fortuneConfig.getConfigurationSection("fortune-mappings");
            if (section != null) {
                for (String attr : section.getKeys(false)) {
                    fortuneMappings.put(attr, section.getStringList(attr));
                }
            }
        }

        return new MiningConfig(
            enabled,
            configuredOnly,
            bypassCreative,
            suppressVanillaMining,
            autoPickup,
            defaultRegenDelay,
            baseSpeed,
            pdcKeys,
            definitions.customBlockIdentities(),
            definitions.blockRules(),
            definitions.toolRules(),
            definitions.buffRules(),
            fortuneMappings
        );
    }

    private static DefinitionSet loadDefinitions(Plugin plugin, int defaultRegenDelay) {
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

    private static void loadDefinitionsFromFile(
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
                if (entry == null) {
                    continue;
                }

                if (entry.contains("material") || entry.contains("block-data-contains")) {
                    addBlockIdentity(blockIdentities, id, entry);
                }
                addBlockRule(blockRules, id, entry, defaultRegenDelay);
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

    private static void addBlockIdentity(List<BlockIdentityRule> rules, String id, ConfigurationSection entry) {
        String material = Ids.normalize(entry.getString("material", ""));
        List<String> contains = entry.getStringList("block-data-contains").stream()
            .map(value -> value.toLowerCase().trim())
            .filter(value -> !value.isEmpty())
            .toList();

        rules.add(new BlockIdentityRule(id, material, contains));
    }

    private static void addBlockRule(Map<String, MiningRule> blockRules, String rawId, ConfigurationSection entry, int defaultRegenDelay) {
        String id = Ids.normalize(rawId);
        double strength = Math.max(0.01, firstDouble(entry, 1.0, "strength", "block-strength", "block_strength"));
        ToolType requiredTool = ToolType.fromConfig(entry.getString("tool", "any"), ToolType.ANY);
        int minTier = Math.max(0, firstInt(entry, 0, "min-tier", "min_tier", "tier"));
        boolean canRegen = entry.getBoolean("can-regenerate", false) || entry.getBoolean("can_regenerate", false);
        
        int regenDelay = defaultRegenDelay;
        if (entry.contains("regeneration-delay") || entry.contains("regeneration_delay") || entry.contains("regen-delay")) {
            regenDelay = firstInt(entry, defaultRegenDelay, "regeneration-delay", "regeneration_delay", "regen-delay");
        }
        
        String regenPlaceholder = entry.getString("regeneration-placeholder", entry.getString("regeneration_placeholder"));
        
        List<MiningRule.DropRule> customDrops = new ArrayList<>();
        if (entry.contains("drops")) {
            for (String rawDrop : entry.getStringList("drops")) {
                MiningRule.DropRule.fromConfig(rawDrop).ifPresent(customDrops::add);
            }
        }
        
        Boolean autoPickupOverride = entry.contains("auto-pickup") ? entry.getBoolean("auto-pickup") : null;
        if (autoPickupOverride == null && entry.contains("auto_pickup")) {
            autoPickupOverride = entry.getBoolean("auto_pickup");
        }

        blockRules.put(id, new MiningRule(id, strength, requiredTool, minTier, canRegen, regenDelay, regenPlaceholder, customDrops, autoPickupOverride));
    }

    private static void addToolRule(Map<String, ToolRule> toolRules, String rawId, ConfigurationSection entry) {
        String id = Ids.normalize(rawId);
        Double speed = containsAny(entry, "speed", "mining-speed", "mining_speed")
            ? Math.max(0.0, firstDouble(entry, 0.0, "speed", "mining-speed", "mining_speed"))
            : null;
        ToolType type = ToolType.fromConfig(entry.getString("type", "hand"), ToolType.HAND);
        int tier = Math.max(0, entry.getInt("tier", 0));
        toolRules.put(id, new ToolRule(id, speed, type, tier));
    }

    Map<String, List<String>> fortuneMappings() {
        return fortuneMappings;
    }

    boolean enabled() {
        return enabled;
    }

    boolean controlConfiguredBlocksOnly() {
        return controlConfiguredBlocksOnly;
    }

    boolean bypassCreative() {
        return bypassCreative;
    }

    boolean suppressVanillaMining() {
        return suppressVanillaMining;
    }

    boolean autoPickup() {
        return autoPickup;
    }

    int defaultRegenerationDelay() {
        return defaultRegenerationDelay;
    }

    double baseMiningSpeed() {
        return baseMiningSpeed;
    }

    List<NamespacedKey> customItemPdcKeys() {
        return customItemPdcKeys;
    }

    List<BlockIdentityRule> customBlockIdentities() {
        return customBlockIdentities;
    }

    MiningRule findBlockRule(List<String> candidateIds) {
        for (String candidate : candidateIds) {
            MiningRule rule = blockRules.get(candidate);
            if (rule != null) {
                return rule;
            }
        }
        return null;
    }

    ToolRule findToolRule(List<String> candidateIds) {
        for (String candidate : candidateIds) {
            ToolRule rule = toolRules.get(candidate);
            if (rule != null) {
                return rule;
            }
        }
        return null;
    }

    MiningBuffRule findBuffRule(String candidateId) {
        return buffRules.get(candidateId);
    }

    private static void addBuffRule(Map<String, MiningBuffRule> buffRules, String rawId, ConfigurationSection entry) {
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

    private static List<String> readSet(ConfigurationSection section, String... keys) {
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

    private static double firstDouble(ConfigurationSection section, double fallback, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getDouble(key);
            }
        }
        return fallback;
    }

    private static int firstInt(ConfigurationSection section, int fallback, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) {
                return section.getInt(key);
            }
        }
        return fallback;
    }

    private static boolean containsAny(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            if (section.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private record DefinitionSet(
        List<BlockIdentityRule> customBlockIdentities,
        Map<String, MiningRule> blockRules,
        Map<String, ToolRule> toolRules,
        Map<String, MiningBuffRule> buffRules
    ) {
    }
}
