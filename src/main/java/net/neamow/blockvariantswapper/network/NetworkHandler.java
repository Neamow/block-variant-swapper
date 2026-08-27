package net.neamow.blockvariantswapper.network;

import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neamow.blockvariantswapper.BlockVariantManager;
import net.neamow.blockvariantswapper.BlockVariantSwapper;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

// Server-side networking logic
// Pick-block is handled by a server mixin (ServerGamePacketListenerImplMixin), not a custom packet
public class NetworkHandler {

    // Called from the mod bus RegisterPayloadHandlersEvent to register packets and handlers
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BlockVariantSwapper.MOD_ID).versioned("1");
        registrar.playToServer(CycleVariantPayload.TYPE, CycleVariantPayload.CODEC, NetworkHandler::handleCycle);
    }

    // Server-side handler for the variant cycle packet
    private static void handleCycle(CycleVariantPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ServerPlayer player = (ServerPlayer) context.player();
                ItemStack stack = player.getMainHandItem();
                if (stack.isEmpty()) return;

                // Find the base item for the variant group
                Item originalItem = BlockVariantManager.getOriginalItem(stack.getItem());

                List<Item> variants = BlockVariantManager.getVariants(originalItem);
                if (variants.isEmpty()) return;

                // Determine the next variant in the list based on scroll direction
                int currentIndex = variants.indexOf(stack.getItem());
                if (currentIndex == -1) currentIndex = 0;

                int nextIndex = (currentIndex - payload.direction()) % variants.size();
                if (nextIndex < 0) nextIndex += variants.size();

                Item nextItem = variants.get(nextIndex);
                if (nextItem == stack.getItem()) return;

                // Create the new variant stack, preserving components (enchants, renames, etc.)
                ItemStack newStack = stack.transmuteCopy(nextItem, stack.getCount());

                Inventory inventory = player.getInventory();
                int slot = inventory.getSelectedSlot();
                inventory.setItem(slot, newStack);

                // Sync the changed slot to the client for immediate visual feedback
                player.connection.send(new ClientboundSetPlayerInventoryPacket(slot, newStack));
            } catch (Exception e) {
                BlockVariantSwapper.LOGGER.error("Error handling variant cycle packet", e);
            }
        });
    }
}
