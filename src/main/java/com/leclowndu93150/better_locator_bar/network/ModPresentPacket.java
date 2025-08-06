package com.leclowndu93150.better_locator_bar.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ModPresentPacket() implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("better_locator_bar", "mod_present");
    public static final Type<ModPresentPacket> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, ModPresentPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {},
        buf -> new ModPresentPacket()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}