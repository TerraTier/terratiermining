package com.terratier.custommining.service;

import com.terratier.custommining.model.BlockKey;
import com.terratier.custommining.model.MiningSession;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.block.Block;

/**
 * Registry for active mining sessions and block states.
 */
public final class MiningSessionManager {
    private final Map<UUID, MiningSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, BlockKey> completingBlocks = new ConcurrentHashMap<>();
    private final Set<BlockKey> transitioningBlocks = Collections.synchronizedSet(new HashSet<>());

    public void startSession(org.bukkit.entity.Player player, Block block) {
        sessions.put(player.getUniqueId(), new MiningSession(player, block));
    }

    public MiningSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public MiningSession endSession(org.bukkit.entity.Player player) {
        return sessions.remove(player.getUniqueId());
    }

    public boolean hasSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public boolean isSessionActive(UUID playerId, Block block) {
        MiningSession existing = sessions.get(playerId);
        return existing != null && existing.sameBlock(block);
    }

    public Collection<MiningSession> getActiveSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    public void clearAll() {
        sessions.clear();
        completingBlocks.clear();
        transitioningBlocks.clear();
    }

    // --- Completion State ---

    public void markCompleting(UUID playerId, BlockKey blockKey) {
        completingBlocks.put(playerId, blockKey);
    }

    public void unmarkCompleting(UUID playerId) {
        completingBlocks.remove(playerId);
    }

    public boolean isCompleting(UUID playerId, BlockKey blockKey) {
        BlockKey key = completingBlocks.get(playerId);
        return key != null && key.equals(blockKey);
    }

    // --- Transition State ---

    public void markTransitioning(BlockKey blockKey) {
        transitioningBlocks.add(blockKey);
    }

    public void unmarkTransitioning(BlockKey blockKey) {
        transitioningBlocks.remove(blockKey);
    }

    public boolean isTransitioning(BlockKey blockKey) {
        return transitioningBlocks.contains(blockKey);
    }
}
