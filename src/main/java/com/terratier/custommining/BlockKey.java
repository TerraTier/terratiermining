package com.terratier.custommining;

import java.util.UUID;
import org.bukkit.block.Block;

record BlockKey(UUID worldId, int x, int y, int z) {
    static BlockKey from(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
}
