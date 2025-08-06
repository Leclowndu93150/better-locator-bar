package com.leclowndu93150.better_locator_bar;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.waypoints.WaypointStyleAsset;
import net.minecraft.world.waypoints.WaypointStyleAssets;

public class LodestoneWaypointStyles {
    public static final ResourceKey<WaypointStyleAsset> LODESTONE = ResourceKey.create(
        WaypointStyleAssets.ROOT_ID, 
        ResourceLocation.fromNamespaceAndPath(BetterLocatorBar.MODID, "lodestone")
    );

    public static final String LODESTONE_UUID_PREFIX = "lodestone_";
}