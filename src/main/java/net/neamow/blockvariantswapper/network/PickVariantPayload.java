package net.neamow.blockvariantswapper.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neamow.blockvariantswapper.BlockVariantSwapper;

// Sent from client to server when the player uses pick-block (middle click) on a block whose variant family they already own
// The server pulls the owned family member from `sourceSlot`, converts it to the picked variant, and places it in the hotbar `targetSlot`
public record PickVariantPayload(int sourceSlot, int targetSlot, ResourceLocation variantItemId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PickVariantPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BlockVariantSwapper.MOD_ID, "pick_variant"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PickVariantPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, PickVariantPayload::sourceSlot,
        ByteBufCodecs.VAR_INT, PickVariantPayload::targetSlot,
        ResourceLocation.STREAM_CODEC, PickVariantPayload::variantItemId,
        PickVariantPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
