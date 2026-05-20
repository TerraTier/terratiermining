package com.terratier.custommining.model;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class MiningSession {
    private final UUID playerId;
    private final World world;
    private final int x;
    private final int y;
    private final int z;
    private double progress;

    public MiningSession(Player player, Block block) {
        this.playerId = player.getUniqueId();
        this.world = block.getWorld();
        this.x = block.getX();
        this.y = block.getY();
        this.z = block.getZ();
    }

    public UUID playerId() {
        return playerId;
    }

    public Block block() {
        return world.getBlockAt(x, y, z);
    }

    public Location animationLocation() {
        return new Location(world, x, y, z);
    }

    public boolean sameBlock(Block block) {
        return block.getWorld().equals(world) && block.getX() == x && block.getY() == y && block.getZ() == z;
    }

    public BlockKey blockKey() {
        return new BlockKey(world.getUID(), x, y, z);
    }

    public boolean blockStillMatches() {
        return true; // We now track by location and check state in Service
    }

    public double progress() {
        return progress;
    }

    public void addProgress(double amount) {
        progress = Math.min(1.0, progress + Math.max(0.0, amount));
    }

    public void resetProgress() {
        progress = 0.0;
    }
}
