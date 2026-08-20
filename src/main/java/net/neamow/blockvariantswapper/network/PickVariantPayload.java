package net.neamow.blockvariantswapper.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.neamow.blockvariantswapper.BlockVariantSwapper;

// BLOCK VARIANT SWAPPER
// Sent from client to server when the player uses pick-block (middle click) on a block whose
// variant family they already own. The server pulls the owned family member from `sourceSlot`,
// converts it to the picked variant, and places it in the hotbar `targetSlot`.
public record PickVariantPayload(int sourceSlot, int targetSlot, Identifier variantItemId) implements CustomPayload {

    public static final CustomPayload.Id<PickVariantPayload> ID = new CustomPayload.Id<>(Identifier.of(BlockVariantSwapper.MOD_ID, "pick_variant"));

    public static final PacketCodec<RegistryByteBuf, PickVariantPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.INTEGER, PickVariantPayload::sourceSlot,
        PacketCodecs.INTEGER, PickVariantPayload::targetSlot,
        Identifier.PACKET_CODEC, PickVariantPayload::variantItemId,
        PickVariantPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
