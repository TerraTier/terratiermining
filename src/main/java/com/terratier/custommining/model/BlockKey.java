package com.terratier.custommining.model;

import java.util.UUID;
import org.bukkit.block.Block;

public record BlockKey(UUID worldId, int x, int y, int z) {
    public static BlockKey from(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
}
