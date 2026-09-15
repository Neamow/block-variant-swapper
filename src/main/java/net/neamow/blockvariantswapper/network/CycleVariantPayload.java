package net.neamow.blockvariantswapper.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neamow.blockvariantswapper.BlockVariantSwapper;

// Network packet sent from client to server when the player scrolls to cycle block variants
public record CycleVariantPayload(int direction) implements CustomPacketPayload {

    // Unique type id for this packet
    public static final CustomPacketPayload.Type<CycleVariantPayload> TYPE =
        new CustomPacketPayload.Type<>(BlockVariantSwapper.id("cycle_variant"));

    // Codec to serialise/deserialise the scroll direction integer
    public static final StreamCodec<ByteBuf, CycleVariantPayload> CODEC =
        StreamCodec.composite(ByteBufCodecs.INT, CycleVariantPayload::direction, CycleVariantPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
