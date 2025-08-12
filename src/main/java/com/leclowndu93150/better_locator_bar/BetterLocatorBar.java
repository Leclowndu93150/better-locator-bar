package com.leclowndu93150.better_locator_bar;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import com.leclowndu93150.better_locator_bar.network.NetworkHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.block.Blocks;

@Mod(BetterLocatorBar.MODID)
public class BetterLocatorBar {
    public static final String MODID = "better_locator_bar";
    private final LodestoneCompassTracker tracker = new LodestoneCompassTracker();

    public BetterLocatorBar(IEventBus modEventBus) {
        modEventBus.addListener(NetworkHandler::register);

        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(this::onBlockBroken);

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
    
    private void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getPlacedBlock().is(Blocks.LODESTONE) && !event.getLevel().isClientSide()) {
            GlobalPos pos = GlobalPos.of(((ServerLevel)event.getLevel()).dimension(), event.getPos());
            LodestoneColorRegistry registry = LodestoneColorRegistry.get(event.getLevel().getServer());
            registry.assignColorToLodestone(pos);
            tracker.refreshWaypointsForLodestone(pos, event.getLevel().getServer());
        }
    }
    
    private void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getState().is(Blocks.LODESTONE) && !event.getLevel().isClientSide()) {
            GlobalPos brokenPos = GlobalPos.of(((net.minecraft.server.level.ServerLevel)event.getLevel()).dimension(), event.getPos());
            LodestoneColorRegistry registry = LodestoneColorRegistry.get(event.getLevel().getServer());
            registry.removeLodestone(brokenPos);
            tracker.updateCompassesForBrokenLodestone(brokenPos, event.getLevel().getServer());
        }
    }
}