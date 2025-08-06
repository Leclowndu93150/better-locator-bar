package com.leclowndu93150.better_locator_bar;

import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.WaypointStyle;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@Mod(BetterLocatorBar.MODID)
public class BetterLocatorBar {
    public static final String MODID = "better_locator_bar";
    private final LodestoneCompassTracker tracker = new LodestoneCompassTracker();

    public BetterLocatorBar(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        tracker.onPlayerTick(event.getEntity());
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        tracker.onPlayerJoin(event.getEntity());
    }
}