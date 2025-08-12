package com.leclowndu93150.better_locator_bar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointTransmitter;
import com.leclowndu93150.better_locator_bar.network.ModdedPlayerTracker;

import java.util.Optional;
import java.util.UUID;

public class LodestoneWaypointTransmitter implements WaypointTransmitter {
    private final UUID uuid;
    private final BlockPos pos;
    private final Waypoint.Icon icon;
    private final int range;
    private final UUID ownerPlayerId;

    public LodestoneWaypointTransmitter(UUID uuid, BlockPos pos, Waypoint.Icon icon, int range, UUID ownerPlayerId) {
        this.uuid = uuid;
        this.pos = pos;
        this.icon = icon;
        this.range = range;
        this.ownerPlayerId = ownerPlayerId;
    }

    @Override
    public boolean isTransmittingWaypoint() {
        return true;
    }

    @Override
    public Optional<Connection> makeWaypointConnectionWith(ServerPlayer receiver) {
        if (receiver.getUUID().equals(ownerPlayerId)) {
            return Optional.of(new LodestoneConnection(receiver));
        }
        return Optional.empty();
    }

    @Override
    public Waypoint.Icon waypointIcon() {
        return icon;
    }

    public UUID getUUID() {
        return uuid;
    }

    public BlockPos getPos() {
        return pos;
    }

    public class LodestoneConnection implements WaypointTransmitter.Connection {
        private final ServerPlayer receiver;

        public LodestoneConnection(ServerPlayer receiver) {
            this.receiver = receiver;
        }

        @Override
        public void connect() {
            Waypoint.Icon iconToSend = ModdedPlayerTracker.hasModInstalled(receiver) ? icon : createUnmoddedIcon();
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(uuid, iconToSend, pos));
        }

        @Override
        public void disconnect() {
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(uuid));
        }

        @Override
        public void update() {
            Waypoint.Icon iconToSend = ModdedPlayerTracker.hasModInstalled(receiver) ? icon : createUnmoddedIcon();
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(uuid, iconToSend, pos));
        }

        @Override
        public boolean isBroken() {
            double d = Math.min(range, receiver.getAttributeValue(Attributes.WAYPOINT_RECEIVE_RANGE));
            return receiver.distanceToSqr(pos.getCenter()) >= d * d;
        }

        /**
         * Creates an icon for unmodded players that matches the color of their compass lore dot.
         * This ensures color consistency between the compass dot and the locator bar indicator.
         */
        private Waypoint.Icon createUnmoddedIcon() {
            Waypoint.Icon unmoddedIcon = new Waypoint.Icon();
            
            // Find the compass that points to this position to get its color
            GlobalPos targetPos = GlobalPos.of(receiver.level().dimension(), pos);
            ItemStack compass = findCompassForPosition(receiver, targetPos);
            
            if (compass != null) {
                // Use the same color logic as the compass lore
                int compassColor = getCompassColor(compass, targetPos, receiver);
                unmoddedIcon.color = Optional.of(compassColor);
            }
            
            return unmoddedIcon;
        }
        
        /**
         * Finds a compass in the player's inventory that points to the specified position.
         */
        private ItemStack findCompassForPosition(ServerPlayer player, GlobalPos targetPos) {
            // Check main hand
            ItemStack mainHand = player.getMainHandItem();
            if (isCompassPointingTo(mainHand, targetPos)) {
                return mainHand;
            }
            
            // Check offhand
            ItemStack offHand = player.getOffhandItem();
            if (isCompassPointingTo(offHand, targetPos)) {
                return offHand;
            }
            
            // Check inventory
            for (int i = 0; i < 36; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (isCompassPointingTo(stack, targetPos)) {
                    return stack;
                }
            }
            
            return null;
        }
        
        /**
         * Checks if a compass points to the specified position.
         */
        private boolean isCompassPointingTo(ItemStack stack, GlobalPos targetPos) {
            if (!stack.is(Items.COMPASS) || !stack.has(DataComponents.LODESTONE_TRACKER)) {
                return false;
            }
            
            LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
            if (tracker != null && tracker.target().isPresent()) {
                return tracker.target().get().equals(targetPos);
            }
            return false;
        }
        
        /**
         * Gets the color for a compass using the same logic as compass lore.
         * This is copied from LodestoneCompassTracker.getCompassColor() to maintain consistency.
         */
        private int getCompassColor(ItemStack compass, GlobalPos targetPos, ServerPlayer player) {
            // Check for TCC dyed compass colors first
            if (compass.has(DataComponents.ITEM_MODEL)) {
                String itemModel = compass.get(DataComponents.ITEM_MODEL).toString();
                if (itemModel.contains("tcc:dyed_compass/")) {
                    String colorName = itemModel.substring(itemModel.lastIndexOf("/") + 1);
                    Optional<Integer> color = getTCCCompassColor(colorName);
                    if (color.isPresent()) {
                        return color.get();
                    }
                }
            }

            // Use shared lodestone color if no TCC color
            try {
                LodestoneColorRegistry registry = LodestoneColorRegistry.get(player.getServer());
                Integer lodestoneColor = registry.getLodestoneColor(targetPos);
                if (lodestoneColor != null) {
                    return lodestoneColor;
                }
            } catch (Exception e) {
                // Fallback if registry access fails
            }

            // Default color
            return 0x55FFFF;
        }
        
        /**
         * Gets TCC compass color mapping.
         * This is copied from LodestoneCompassTracker.getTCCCompassColor() to maintain consistency.
         */
        private Optional<Integer> getTCCCompassColor(String colorName) {
            return switch (colorName) {
                case "white" -> Optional.of(0xF9FFFE);
                case "light_gray" -> Optional.of(0x9D9D97);
                case "gray" -> Optional.of(0x474F52);
                case "black" -> Optional.of(0x1D1D21);
                case "brown" -> Optional.of(0x835432);
                case "red" -> Optional.of(0xB02E26);
                case "orange" -> Optional.of(0xF9801D);
                case "yellow" -> Optional.of(0xFED83D);
                case "lime" -> Optional.of(0x80C71F);
                case "green" -> Optional.of(0x5E7C16);
                case "cyan" -> Optional.of(0x169C9C);
                case "light_blue" -> Optional.of(0x3AB3DA);
                case "blue" -> Optional.of(0x3C44AA);
                case "purple" -> Optional.of(0x8932B8);
                case "magenta" -> Optional.of(0xC74EBD);
                case "pink" -> Optional.of(0xF38BAA);
                default -> Optional.empty();
            };
        }
    }
}