package com.terratier.custommining;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class MiningEventHandler implements Listener {
    private final MiningService service;

    MiningEventHandler(MiningService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        // Detect arm swings to facilitate "pre-mining" and seamless regrow
        service.handleAnimation(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        service.handleBlockDamage(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        service.handleBlockBreak(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCancelledBlockBreakVisual(BlockBreakEvent event) {
        if (!event.isCancelled()) return;
        service.handleCancelledBreakVisual(event);
    }

    @EventHandler
    public void onBlockDamageAbort(BlockDamageAbortEvent event) {
        service.handleBlockDamageAbort(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.handleQuit(event);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        service.handleChangedWorld(event);
    }
}
