package net.neamow.blockvariantswapper.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neamow.blockvariantswapper.BlockVariantManager;
import net.neamow.blockvariantswapper.BlockVariantSwapper;

import java.util.List;

// Server-side logic
// Note: pick-block is variant-aware too, but that is handled by a server mixin on the vanilla pick-item packet handler
// rather than a custom packet (see ServerGamePacketListenerImplMixin)
public class NetworkHandler {

    // Register the custom packet (payload) and its listener
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(CycleVariantPayload.TYPE, CycleVariantPayload.CODEC);

        // Handlers run on the server thread from client-sent packets
        // We never trust client input to behave, so any exception is caught and logged rather than allowed to crash the server tick
        ServerPlayNetworking.registerGlobalReceiver(CycleVariantPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                try {
                    handleCycle(context.player(), payload.direction());
                } catch (Exception e) {
                    BlockVariantSwapper.LOGGER.error("Error handling variant cycle packet", e);
                }
            });
        });
    }

    // Cycle through the block variants of the selected hotbar slot block
    private static void handleCycle(ServerPlayer player, int direction) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        // Figure out the base item for the variant group
        Item originalItem = BlockVariantManager.getOriginalItem(stack.getItem());

        List<Item> variants = BlockVariantManager.getVariants(originalItem);
        if (variants.isEmpty()) return;

        // Find the current item's position in the variant list and get the next one
        int currentIndex = variants.indexOf(stack.getItem());
        if (currentIndex == -1) currentIndex = 0;

        int nextIndex = (currentIndex - direction) % variants.size();
        if (nextIndex < 0) nextIndex += variants.size();

        Item nextItem = variants.get(nextIndex);
        if (nextItem == stack.getItem()) return;

        // Create a new item stack for the new variant, preserving components
        ItemStack newStack = stack.transmuteCopy(nextItem, stack.getCount());

        Inventory inventory = player.getInventory();
        inventory.setItem(inventory.getSelectedSlot(), newStack);
    }
}
