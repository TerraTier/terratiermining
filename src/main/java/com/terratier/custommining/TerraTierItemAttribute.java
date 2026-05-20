package com.terratier.custommining;

import java.util.Locale;
import org.bukkit.inventory.EquipmentSlot;

record TerraTierItemAttribute(String attribute, double value, String source, String slot) {
    static TerraTierItemAttribute inventory(String attribute, double value) {
        return new TerraTierItemAttribute(attribute, value, "inventory", null);
    }

    boolean appliesTo(BuffSourceType sourceType, EquipmentSlot equipmentSlot) {
        return sourceMatches(sourceType) && slotMatches(sourceType, equipmentSlot);
    }

    private boolean sourceMatches(BuffSourceType sourceType) {
        String normalized = normalize(source == null ? "equipped" : source);
        return switch (normalized) {
            case "all", "any" -> true;
            case "inventory", "bag", "passive" -> true;
            case "equipped", "equipment" -> sourceType != BuffSourceType.INVENTORY;
            case "held", "hand" -> sourceType == BuffSourceType.HELD || sourceType == BuffSourceType.OFFHAND;
            case "mainhand", "main_hand" -> sourceType == BuffSourceType.HELD;
            case "offhand", "off_hand" -> sourceType == BuffSourceType.OFFHAND;
            case "armor" -> sourceType == BuffSourceType.ARMOR;
            default -> sourceType != BuffSourceType.INVENTORY;
        };
    }

    private boolean slotMatches(BuffSourceType sourceType, EquipmentSlot equipmentSlot) {
        String normalized = normalize(slot);
        if (normalized.isEmpty() || normalized.equals("any")) {
            return true;
        }
        if (sourceType == BuffSourceType.INVENTORY && sourceMatchesInventory()) {
            return true;
        }

        return switch (normalized) {
            case "hand" -> equipmentSlot == EquipmentSlot.HAND || equipmentSlot == EquipmentSlot.OFF_HAND;
            case "mainhand", "main_hand" -> equipmentSlot == EquipmentSlot.HAND;
            case "offhand", "off_hand" -> equipmentSlot == EquipmentSlot.OFF_HAND;
            case "armor" -> sourceType == BuffSourceType.ARMOR;
            case "helmet", "head" -> equipmentSlot == EquipmentSlot.HEAD;
            case "chest", "chestplate", "body" -> equipmentSlot == EquipmentSlot.CHEST;
            case "legs", "leggings" -> equipmentSlot == EquipmentSlot.LEGS;
            case "feet", "boots" -> equipmentSlot == EquipmentSlot.FEET;
            default -> false;
        };
    }

    private boolean sourceMatchesInventory() {
        String normalized = normalize(source);
        return normalized.equals("inventory") || normalized.equals("bag") || normalized.equals("passive");
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
