package com.terratier.custommining;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

final class MiningSessionManager {
    private final Map<UUID, MiningSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, BlockKey> completingBreaks = new ConcurrentHashMap<>();
    private final Set<BlockKey> transitioningBlocks = Collections.synchronizedSet(new HashSet<>());

    MiningSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    void startSession(Player player, Block block) {
        sessions.put(player.getUniqueId(), new MiningSession(player, block));
    }

    void endSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    void clearAll() {
        sessions.clear();
        completingBreaks.clear();
        transitioningBlocks.clear();
    }

    Map<UUID, MiningSession> getActiveSessions() {
        return sessions;
    }

    void markCompleting(UUID playerId, BlockKey blockKey) {
        completingBreaks.put(playerId, blockKey);
    }

    void unmarkCompleting(UUID playerId) {
        completingBreaks.remove(playerId);
    }

    boolean isCompleting(UUID playerId, BlockKey blockKey) {
        BlockKey active = completingBreaks.get(playerId);
        return active != null && active.equals(blockKey);
    }

    void markTransitioning(BlockKey blockKey) {
        transitioningBlocks.add(blockKey);
    }

    void unmarkTransitioning(BlockKey blockKey) {
        transitioningBlocks.remove(blockKey);
    }

    boolean isTransitioning(BlockKey blockKey) {
        return transitioningBlocks.contains(blockKey);
    }
}
