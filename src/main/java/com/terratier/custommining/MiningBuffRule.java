package com.terratier.custommining;

import java.util.Map;
import java.util.Set;
import org.bukkit.inventory.EquipmentSlot;

record MiningBuffRule(
    String id,
    Set<BuffSourceType> applies,
    Set<EquipmentSlot> slots,
    Map<String, Double> attributes
) {
    boolean appliesTo(BuffSourceType sourceType, EquipmentSlot slot) {
        if (!applies.isEmpty() && !applies.contains(sourceType)) {
            return false;
        }
        return slots.isEmpty() || (slot != null && slots.contains(slot));
    }
}
