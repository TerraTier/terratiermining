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
    List<LootTable> tables,
    Boolean autoPickupOverride
) {
    public MiningRule(String id, double strength, ToolType requiredTool, int minTier) {
        this(id, strength, requiredTool, minTier, false, 0, null, List.of(), null);
    }

    public enum DropStrategy {
        POOLED,      // OSRS Main: Pick one, rare items boosted by Luck
        INDEPENDENT, // OSRS Tertiary: Rolls each, flat boosted by Luck
        STATIC       // Always: Rolls each, NOT affected by Luck
    }

    public record LootTable(
        DropStrategy strategy,
        int minRolls,
        int maxRolls,
        List<DropRule> pool
    ) {}

    public record DropRule(String material, int min, int max, double value) {
        public static Optional<DropRule> fromConfig(String input) {
            if (input == null || input.isBlank()) return Optional.empty();

            // OSRS Format: material:amount:value (e.g. minecraft:iron_ore:1-3:1/128)
            // Strategy: Split by colon, but material might have a colon (minecraft:stone)

            String[] parts = input.split(":");
            if (parts.length == 0) return Optional.empty();

            String mat;
            String rangePart = "1";
            String valuePart = "1.0";

            if (input.contains("minecraft:") || input.contains("terratier:")) {
                // Namespaced material
                if (parts.length >= 4) {
                    // namespace:key:range:value
                    mat = parts[0] + ":" + parts[1];
                    rangePart = parts[2];
                    valuePart = parts[3];
                } else if (parts.length == 3) {
                    // namespace:key:range OR namespace:key:value? 
                    // Assume namespace:key:range if part 2 looks like a number or range
                    mat = parts[0] + ":" + parts[1];
                    if (parts[2].contains("-") || isInteger(parts[2])) {
                        rangePart = parts[2];
                    } else {
                        valuePart = parts[2];
                    }
                } else if (parts.length == 2) {
                    mat = parts[0] + ":" + parts[1];
                } else {
                    mat = parts[0];
                }
            } else {
                // No namespace
                mat = parts[0];
                if (parts.length >= 3) {
                    rangePart = parts[1];
                    valuePart = parts[2];
                } else if (parts.length == 2) {
                    if (parts[1].contains("-") || isInteger(parts[1])) {
                        rangePart = parts[1];
                    } else {
                        valuePart = parts[1];
                    }
                }
            }

            int min = 1;
            int max = 1;
            if (rangePart.contains("-")) {
                String[] rangeParts = rangePart.split("-");
                try {
                    min = Integer.parseInt(rangeParts[0].trim());
                    max = Integer.parseInt(rangeParts[1].trim());
                } catch (NumberFormatException ignored) {}
            } else {
                try {
                    min = max = Integer.parseInt(rangePart.trim());
                } catch (NumberFormatException ignored) {}
            }

            double value = 1.0;
            try {
                String valStr = valuePart.trim();
                if (valStr.contains("/")) {
                    String[] frac = valStr.split("/");
                    value = Double.parseDouble(frac[0].trim()) / Double.parseDouble(frac[1].trim());
                } else if (valStr.endsWith("%")) {
                    value = Double.parseDouble(valStr.substring(0, valStr.length() - 1)) / 100.0;
                } else {
                    value = Double.parseDouble(valStr);
                }
            } catch (NumberFormatException ignored) {}

            return Optional.of(new DropRule(mat.trim(), min, max, value));
        }

        private static boolean isInteger(String s) {
            try {
                Integer.parseInt(s.trim());
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
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
}
