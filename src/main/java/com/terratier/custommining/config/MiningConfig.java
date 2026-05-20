package com.terratier.custommining.config;

import java.util.List;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Immutable configuration for the mining system.
 */
public final class MiningConfig {
    private final boolean enabled;
    private final boolean controlConfiguredBlocksOnly;
    private final boolean bypassCreative;
    private final boolean suppressVanillaMining;
    private final boolean autoPickup;
    private final int defaultRegenerationDelay;
    private final double baseMiningSpeed;
    private final int breakCooldownMs;
    private final List<NamespacedKey> customItemPdcKeys;
    private final List<BlockIdentityRule> customBlockIdentities;
    private final Map<String, MiningRule> blockRules;
    private final Map<String, ToolRule> toolRules;
    private final Map<String, MiningBuffRule> buffRules;
    private final Map<String, List<String>> fortuneMappings;

    public MiningConfig(
        boolean enabled,
        boolean controlConfiguredBlocksOnly,
        boolean bypassCreative,
        boolean suppressVanillaMining,
        boolean autoPickup,
        int defaultRegenerationDelay,
        double baseMiningSpeed,
        int breakCooldownMs,
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
        this.breakCooldownMs = breakCooldownMs;
        this.customItemPdcKeys = List.copyOf(customItemPdcKeys);
        this.customBlockIdentities = List.copyOf(customBlockIdentities);
        this.blockRules = Map.copyOf(blockRules);
        this.toolRules = Map.copyOf(toolRules);
        this.buffRules = Map.copyOf(buffRules);
        this.fortuneMappings = Map.copyOf(fortuneMappings);
    }

    public static MiningConfig load(Plugin plugin) {
        return new MiningConfigLoader(plugin).load();
    }

    public Map<String, List<String>> fortuneMappings() {
        return fortuneMappings;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean controlConfiguredBlocksOnly() {
        return controlConfiguredBlocksOnly;
    }

    public boolean bypassCreative() {
        return bypassCreative;
    }

    public boolean suppressVanillaMining() {
        return suppressVanillaMining;
    }

    public boolean autoPickup() {
        return autoPickup;
    }

    public int defaultRegenerationDelay() {
        return defaultRegenerationDelay;
    }

    public double baseMiningSpeed() {
        return baseMiningSpeed;
    }

    public int breakCooldownMs() {
        return breakCooldownMs;
    }

    public List<NamespacedKey> customItemPdcKeys() {
        return customItemPdcKeys;
    }

    public List<BlockIdentityRule> customBlockIdentities() {
        return customBlockIdentities;
    }

    public MiningRule findBlockRule(List<String> candidateIds) {
        for (String candidate : candidateIds) {
            MiningRule rule = blockRules.get(candidate);
            if (rule != null) return rule;
        }
        return null;
    }

    public ToolRule findToolRule(List<String> candidateIds) {
        for (String candidate : candidateIds) {
            ToolRule rule = toolRules.get(candidate);
            if (rule != null) return rule;
        }
        return null;
    }

    public MiningBuffRule findBuffRule(String candidateId) {
        return buffRules.get(candidateId);
    }
}
