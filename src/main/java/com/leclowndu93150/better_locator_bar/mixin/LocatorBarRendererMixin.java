package com.leclowndu93150.better_locator_bar.mixin;

import com.leclowndu93150.better_locator_bar.ContextualBarRendererInterface;
import com.leclowndu93150.better_locator_bar.LodestoneWaypointStyles;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.contextualbar.LocatorBarRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.WaypointStyle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.Waypoint;
import org.spongepowered.asm.mixin.*;

@Mixin(LocatorBarRenderer.class)
public class LocatorBarRendererMixin {
    
    @Shadow @Final private Minecraft minecraft;
    
    /**
     * @author better_locator_bar
     * @reason Override waypoint rendering to use custom lodestone sprites
     */
    @Overwrite
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        int i = ((ContextualBarRendererInterface)this).callTop(this.minecraft.getWindow());
        Level level = this.minecraft.cameraEntity.level();
        assert this.minecraft.player != null;
        this.minecraft.player.connection.getWaypointManager().forEachWaypoint(this.minecraft.cameraEntity, (waypoint) -> {
            if (!(Boolean)waypoint.id().left().map((playerId) -> playerId.equals(this.minecraft.cameraEntity.getUUID())).orElse(false)) {
                double d0 = waypoint.yawAngleToCamera(level, this.minecraft.gameRenderer.getMainCamera());
                if (!(d0 <= -61.0D) && !(d0 > 60.0D)) {
                    int j = Mth.ceil((float)(guiGraphics.guiWidth() - 9) / 2.0F);
                    Waypoint.Icon waypoint$icon = waypoint.icon();

                    ResourceLocation resourcelocation;
                    if (waypoint$icon.style.equals(LodestoneWaypointStyles.LODESTONE)) {
                        float f = Mth.sqrt((float)waypoint.distanceSquared(this.minecraft.cameraEntity));
                        resourcelocation = betterLocatorBar$getLodestoneSprite(f);
                    } else {
                        WaypointStyle waypointstyle = this.minecraft.getWaypointStyles().get(waypoint$icon.style);
                        float f = Mth.sqrt((float)waypoint.distanceSquared(this.minecraft.cameraEntity));
                        resourcelocation = waypointstyle.sprite(f);
                    }
                    
                    int k = waypoint$icon.color.orElseGet(() -> waypoint.id().map((playerId) -> ARGB.setBrightness(ARGB.color(255, playerId.hashCode()), 0.9F), (uuid) -> ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), 0.9F)));
                    int l = (int)(d0 * 173.0D / 2.0D / 60.0D);
                    guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, resourcelocation, j + l, i - 2, 9, 9, k);
                    TrackedWaypoint.PitchDirection trackedwaypoint$pitchdirection = waypoint.pitchDirectionToCamera(level, this.minecraft.gameRenderer);
                    if (trackedwaypoint$pitchdirection != TrackedWaypoint.PitchDirection.NONE) {
                        int i1;
                        ResourceLocation resourcelocation1;
                        if (trackedwaypoint$pitchdirection == TrackedWaypoint.PitchDirection.DOWN) {
                            i1 = 6;
                            resourcelocation1 = ResourceLocation.withDefaultNamespace("hud/locator_bar_arrow_down");
                        } else {
                            i1 = -6;
                            resourcelocation1 = ResourceLocation.withDefaultNamespace("hud/locator_bar_arrow_up");
                        }

                        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, resourcelocation1, j + l + 1, i + i1, 7, 5);
                    }
                }
            }
        });
    }
    
    @Unique
    private ResourceLocation betterLocatorBar$getLodestoneSprite(float distance) {
        if (distance <= 64.0f) {
            return ResourceLocation.fromNamespaceAndPath("better_locator_bar", "hud/locator_bar_dot/lodestone_1");
        } else if (distance <= 128.0f) {
            return ResourceLocation.fromNamespaceAndPath("better_locator_bar", "hud/locator_bar_dot/lodestone_2");
        } else if (distance <= 256.0f) {
            return ResourceLocation.fromNamespaceAndPath("better_locator_bar", "hud/locator_bar_dot/lodestone_3");
        } else {
            return ResourceLocation.fromNamespaceAndPath("better_locator_bar", "hud/locator_bar_dot/lodestone_4");
        }
    }
    
}