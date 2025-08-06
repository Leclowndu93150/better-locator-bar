package com.leclowndu93150.better_locator_bar;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointTransmitter;

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
        // Only create connection if the receiver is the owner of this waypoint
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
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(uuid, icon, pos));
        }

        @Override
        public void disconnect() {
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(uuid));
        }

        @Override
        public void update() {
            // Don't send update packets - just send add packets to ensure the waypoint exists
            // This prevents the NullPointerException when the client doesn't have the waypoint yet
            this.receiver.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(uuid, icon, pos));
        }

        @Override
        public boolean isBroken() {
            double d = Math.min(range, receiver.getAttributeValue(Attributes.WAYPOINT_RECEIVE_RANGE));
            return receiver.distanceToSqr(pos.getCenter()) >= d * d;
        }
    }
}