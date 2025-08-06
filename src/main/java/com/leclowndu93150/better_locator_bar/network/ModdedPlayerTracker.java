package com.leclowndu93150.better_locator_bar.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "better_locator_bar")
public class ModdedPlayerTracker {
    private static final Set<UUID> MODDED_PLAYERS = ConcurrentHashMap.newKeySet();
    
    public static void markPlayerAsModded(ServerPlayer player) {
        MODDED_PLAYERS.add(player.getUUID());
    }
    
    public static boolean hasModInstalled(ServerPlayer player) {
        return MODDED_PLAYERS.contains(player.getUUID());
    }
    
    public static boolean hasModInstalled(UUID playerUUID) {
        return MODDED_PLAYERS.contains(playerUUID);
    }
    
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            MODDED_PLAYERS.remove(serverPlayer.getUUID());
        }
    }
}