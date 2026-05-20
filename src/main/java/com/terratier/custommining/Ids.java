package com.terratier.custommining;

import java.util.Locale;

final class Ids {
    private Ids() {
    }

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }

        String value = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (value.isEmpty()) {
            return "";
        }
        if (value.equals("hand") || value.contains(":") || value.contains("#")) {
            return value;
        }
        return "minecraft:" + value;
    }

    static String normalizeAttribute(String attribute) {
        if (attribute == null) {
            return "";
        }
        return attribute.trim()
            .toLowerCase(Locale.ROOT)
            .replace('-', '_')
            .replace("terratier:", "");
    }
}
