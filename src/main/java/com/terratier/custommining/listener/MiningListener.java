package com.terratier.custommining.listener;

import com.terratier.custommining.service.MiningService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles Bukkit events and routes them to the {@link MiningService}.
 */
public final class MiningListener implements Listener {
    private final MiningService service;

    public MiningListener(MiningService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockDamage(BlockDamageEvent event) {
        service.handleBlockDamage(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        service.handleBlockBreak(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakMonitor(BlockBreakEvent event) {
        service.handleCancelledBreakVisual(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDamageAbort(BlockDamageAbortEvent event) {
        service.handleBlockDamageAbort(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAnimation(PlayerAnimationEvent event) {
        service.handleAnimation(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        service.handleBlockPlace(event);
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
