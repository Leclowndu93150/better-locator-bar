package com.leclowndu93150.better_locator_bar;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import com.leclowndu93150.better_locator_bar.network.NetworkHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(BetterLocatorBar.MODID)
public class BetterLocatorBar {
    public static final String MODID = "better_locator_bar";
    private final LodestoneCompassTracker tracker = new LodestoneCompassTracker();

    public BetterLocatorBar(IEventBus modEventBus) {
        modEventBus.addListener(NetworkHandler::register);

        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener(this::onClientPlayerJoin);
        }
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        tracker.onPlayerTick(event.getEntity());
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        tracker.onPlayerJoin(event.getEntity());
    }
    
    private void onClientPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        NetworkHandler.sendModPresentToServer();
    }
}