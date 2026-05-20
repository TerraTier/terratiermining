package com.terratier.custommining.config;

import com.terratier.custommining.model.BuffSourceType;
import java.util.Map;
import java.util.Set;
import org.bukkit.inventory.EquipmentSlot;

public record MiningBuffRule(
    String id,
    Set<BuffSourceType> applies,
    Set<EquipmentSlot> slots,
    Map<String, Double> attributes
) {
    public boolean appliesTo(BuffSourceType sourceType, EquipmentSlot slot) {
        if (!applies.isEmpty() && !applies.contains(sourceType)) {
            return false;
        }
        return slots.isEmpty() || (slot != null && slots.contains(slot));
    }
}
