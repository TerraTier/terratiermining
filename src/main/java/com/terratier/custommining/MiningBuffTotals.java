package com.terratier.custommining;

import java.util.HashMap;
import java.util.Map;

record MiningBuffTotals(Map<String, Double> attributes, int sources, Map<BuffSourceType, MiningBuffTotals> sourceBreakdown) {
    MiningBuffTotals(Map<String, Double> attributes, int sources) {
        this(attributes, sources, new HashMap<>());
    }

    static MiningBuffTotals none() {
        return new MiningBuffTotals(new HashMap<>(), 0, new HashMap<>());
    }

    boolean hasBuffs() {
        return !attributes.isEmpty();
    }

    double get(String key, double fallback) {
        return attributes.getOrDefault(key, fallback);
    }

    MiningBuffTotals add(String key, double value, boolean multiply) {
        Map<String, Double> next = new HashMap<>(attributes);
        if (multiply) {
            next.put(key, next.getOrDefault(key, 1.0) * Math.max(0.0, value));
        } else {
            next.put(key, next.getOrDefault(key, 0.0) + value);
        }
        return new MiningBuffTotals(next, sources + 1, sourceBreakdown);
    }

    double getTotalAdd(String prefix) {
        double total = 0.0;
        for (Map.Entry<String, Double> entry : attributes.entrySet()) {
            if (entry.getKey().startsWith(prefix) && !isMultiplier(entry.getKey())) {
                total += entry.getValue();
            }
        }
        return total;
    }

    double getTotalMultiplier(String prefix) {
        double total = 1.0;
        for (Map.Entry<String, Double> entry : attributes.entrySet()) {
            if (entry.getKey().startsWith(prefix) && isMultiplier(entry.getKey())) {
                total *= entry.getValue();
            }
        }
        return total;
    }

    private boolean isMultiplier(String key) {
        return key.endsWith("_multiplier") || key.endsWith("-multiplier");
    }

    MiningBuffTotals merge(MiningBuffTotals other) {
        if (other == null || !other.hasBuffs()) {
            return this;
        }
        
        Map<String, Double> next = new HashMap<>(attributes);
        other.attributes.forEach((key, value) -> {
            boolean multiply = key.endsWith("_multiplier") || key.endsWith("-multiplier");
            if (multiply) {
                next.put(key, next.getOrDefault(key, 1.0) * value);
            } else {
                next.put(key, next.getOrDefault(key, 0.0) + value);
            }
        });
        
        // Merge breakdowns
        Map<BuffSourceType, MiningBuffTotals> nextBreakdown = new HashMap<>(sourceBreakdown);
        other.sourceBreakdown.forEach((type, totals) -> {
            nextBreakdown.put(type, nextBreakdown.getOrDefault(type, none()).merge(totals));
        });

        return new MiningBuffTotals(next, sources + other.sources, nextBreakdown);
    }

    String describe() {
        if (!hasBuffs()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" + buffs(");
        attributes.forEach((key, value) -> {
            boolean multiply = key.endsWith("_multiplier") || key.endsWith("-multiplier");
            if (multiply && value == 1.0) return;
            if (!multiply && value == 0.0) return;
            
            sb.append(key).append(": ").append(trim(value)).append(", ");
        });
        sb.append(sources).append(" source").append(sources == 1 ? "" : "s").append(")");
        return sb.toString();
    }

    private String trim(double value) {
        if (Math.rint(value) == value) {
            return Integer.toString((int) value);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
