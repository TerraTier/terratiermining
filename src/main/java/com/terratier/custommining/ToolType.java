package com.terratier.custommining;

import java.util.Locale;

enum ToolType {
    ANY,
    HAND,
    PICKAXE,
    AXE,
    SHOVEL,
    HOE,
    SHEARS,
    SWORD;

    static ToolType fromConfig(String raw, ToolType fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("NONE")) {
            return ANY;
        }

        try {
            return ToolType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    boolean accepts(ToolType actual) {
        if (this == ANY) {
            return true;
        }
        return this == actual;
    }

    String displayName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
