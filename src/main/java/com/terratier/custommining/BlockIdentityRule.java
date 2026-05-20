package com.terratier.custommining;

import java.util.List;
import org.bukkit.block.Block;

record BlockIdentityRule(String id, String materialId, List<String> blockDataContains) {
    boolean matches(Block block) {
        String actualMaterial = block.getType().getKey().asString();
        if (!materialId.isEmpty() && !materialId.equals(actualMaterial)) {
            return false;
        }
        if (blockDataContains.isEmpty()) {
            return true;
        }

        String blockData = block.getBlockData().getAsString(false).toLowerCase();
        for (String token : blockDataContains) {
            if (!blockData.contains(token)) {
                return false;
            }
        }
        return true;
    }
}
