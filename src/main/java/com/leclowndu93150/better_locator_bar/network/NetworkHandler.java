package com.leclowndu93150.better_locator_bar.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {
    
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1.0");

        registrar.playToServer(ModPresentPacket.TYPE, ModPresentPacket.STREAM_CODEC, NetworkHandler::handleModPresent);
    }
    
    private static void handleModPresent(ModPresentPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ModdedPlayerTracker.markPlayerAsModded(serverPlayer);
            }
        });
    }
    
    public static void sendModPresentToServer() {
        PacketDistributor.sendToServer(new ModPresentPacket());
    }
}