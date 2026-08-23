package net.neamow.blockvariantswapper.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neamow.blockvariantswapper.BlockVariantSwapper;

// Network packet sent from client to server when the player uses the scroll wheel to cycle block variants
public record CycleVariantPayload(int direction) implements CustomPacketPayload {

    // Unique identifier for this packet type
    public static final CustomPacketPayload.Type<CycleVariantPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BlockVariantSwapper.MOD_ID, "cycle_variant"));

    // Codec to serialize/deserialize the packet data (just the scroll direction integer)
    public static final StreamCodec<RegistryFriendlyByteBuf, CycleVariantPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, CycleVariantPayload::direction,
        CycleVariantPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
