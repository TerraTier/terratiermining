package com.terratier.custommining.service;

import com.terratier.custommining.model.MiningBuffTotals;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class FortuneManager {
    private final Map<String, List<String>> fortuneMappings = new HashMap<>();

    public void updateMappings(Map<String, List<String>> mappings) {
        this.fortuneMappings.clear();
        this.fortuneMappings.putAll(mappings);
    }

    public void applyFortune(Player player, Block block, List<ItemStack> drops, MiningBuffTotals buffs) {
        String blockId = block.getType().getKey().toString();
        
        // 1. Calculate Multiplier from specific mappings
        double totalMultiplier = 1.0;
        
        // Add global fortune
        totalMultiplier += buffs.get("fortune", 0.0);

        for (Map.Entry<String, List<String>> entry : fortuneMappings.entrySet()) {
            String fortuneAttr = entry.getKey();
            List<String> affectedBlocks = entry.getValue();

            if (affectedBlocks.contains(blockId) || affectedBlocks.contains(block.getType().name().toLowerCase())) {
                totalMultiplier += buffs.get(fortuneAttr, 0.0);
            }
        }

        if (totalMultiplier > 1.0) {
            for (ItemStack item : drops) {
                // 1:1 Multiplier logic: 2 fortune = 2x drops, 1.5 fortune = 1.5x drops (rounded)
                int originalAmount = item.getAmount();
                int newAmount = (int) Math.round(originalAmount * totalMultiplier);
                if (newAmount > originalAmount) {
                    item.setAmount(newAmount);
                }
            }
        }
    }

    // Removing old calculateMultiplier method as we use 1:1 now
}
