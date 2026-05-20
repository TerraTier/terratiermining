package com.terratier.custommining.config;

import com.terratier.custommining.model.ToolStats;
import com.terratier.custommining.model.ToolType;
import java.util.List;
import java.util.Optional;

public record MiningRule(
    String id, 
    double strength, 
    ToolType requiredTool, 
    int minTier, 
    boolean canRegenerate, 
    int regenerationDelay,
    String regenerationPlaceholder,
    List<DropRule> customDrops,
    Boolean autoPickupOverride
) {
    public MiningRule(String id, double strength, ToolType requiredTool, int minTier) {
        this(id, strength, requiredTool, minTier, false, 0, null, List.of(), null);
    }

    public boolean canHarvest(ToolStats tool) {
        return requiredTool.accepts(tool.type()) && tool.tier() >= minTier;
    }

    public String requirementText() {
        if (requiredTool() == ToolType.ANY && minTier <= 0) {
            return "any tool";
        }
        if (requiredTool() == ToolType.ANY) {
            return "tier " + minTier + "+";
        }
        return requiredTool.displayName() + " tier " + minTier + "+";
    }

    public record DropRule(String material, int min, int max) {
        public static Optional<DropRule> fromConfig(String input) {
            if (input == null || input.isBlank()) return Optional.empty();
            
            String[] parts = input.split(":");
            String mat = parts[0].trim();
            int min = 1;
            int max = 1;

            if (parts.length > 1) {
                String range = parts[1].trim();
                if (range.contains("-")) {
                    String[] rangeParts = range.split("-");
                    try {
                        min = Integer.parseInt(rangeParts[0].trim());
                        max = Integer.parseInt(rangeParts[1].trim());
                    } catch (NumberFormatException ignored) {}
                } else {
                    try {
                        min = max = Integer.parseInt(range);
                    } catch (NumberFormatException ignored) {}
                }
            }
            
            return Optional.of(new DropRule(mat, min, max));
        }
    }
}
