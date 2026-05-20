package com.terratier.custommining;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

final class MiningSession {
    private final UUID playerId;
    private final World world;
    private final int x;
    private final int y;
    private final int z;
    private final String initialBlockData;
    private double progress;

    MiningSession(Player player, Block block) {
        this.playerId = player.getUniqueId();
        this.world = block.getWorld();
        this.x = block.getX();
        this.y = block.getY();
        this.z = block.getZ();
        this.initialBlockData = block.getBlockData().getAsString(false);
    }

    UUID playerId() {
        return playerId;
    }

    Block block() {
        return world.getBlockAt(x, y, z);
    }

    Location animationLocation() {
        return new Location(world, x, y, z);
    }

    boolean sameBlock(Block block) {
        return block.getWorld().equals(world) && block.getX() == x && block.getY() == y && block.getZ() == z;
    }

    BlockKey blockKey() {
        return new BlockKey(world.getUID(), x, y, z);
    }

    boolean blockStillMatches() {
        return true; // We now track by location and check state in Service
    }

    double progress() {
        return progress;
    }

    void addProgress(double amount) {
        progress = Math.min(1.0, progress + Math.max(0.0, amount));
    }

    void resetProgress() {
        progress = 0.0;
    }
}
