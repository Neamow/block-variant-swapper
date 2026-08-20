package net.neamow.blockvariantswapper.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neamow.blockvariantswapper.BlockVariantManager;

import java.util.List;

// This class handles the server-side logic for the Block Variant Swapper.
// It receives packets from the client (e.g., when a player scrolls) and
// executes the corresponding actions on the server.
public class NetworkHandler {

    // Register the custom packet (payload) & set up the listener
    public static void register() {
        PayloadTypeRegistry.playC2S().register(CycleVariantPayload.ID, CycleVariantPayload.CODEC);

        // Set up the listener. When a packet is received,
        // the code inside the lambda is run on the server.

        // BLOCK VARIANT SWAPPER
        ServerPlayNetworking.registerGlobalReceiver(CycleVariantPayload.ID, (payload, context) -> {
            context.server().execute(() -> handleCycle(context.player(), payload.direction()));
        });
    }

    // BLOCK VARIANT SWAPPER
    // Cycles through the block variants of the selected hotbar slot block
    private static void handleCycle(ServerPlayerEntity player, int direction) {
        ItemStack stack = player.getMainHandStack();
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
        ItemStack newStack = stack.copyComponentsToNewStack(nextItem, stack.getCount());

        player.getInventory().setStack(player.getInventory().selectedSlot, newStack);
    }
}
