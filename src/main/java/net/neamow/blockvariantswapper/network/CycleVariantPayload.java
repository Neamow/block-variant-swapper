package net.neamow.blockvariantswapper.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.neamow.blockvariantswapper.BlockVariantSwapper;

// BLOCK VARIANT SWAPPER
// Network packet sent from client to server when the player uses the scroll wheel to cycle block variants
public record CycleVariantPayload(int direction) implements CustomPayload {

    // Unique identifier for this packet type
    public static final CustomPayload.Id<CycleVariantPayload> ID = new CustomPayload.Id<>(Identifier.of(BlockVariantSwapper.MOD_ID, "cycle_variant"));

    // Codec to serialize/deserialize the packet data (just the scroll direction integer)
    public static final PacketCodec<RegistryByteBuf, CycleVariantPayload> CODEC = PacketCodec.tuple(PacketCodecs.INTEGER, CycleVariantPayload::direction, CycleVariantPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
