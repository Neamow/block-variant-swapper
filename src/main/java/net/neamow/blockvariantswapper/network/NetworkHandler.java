package net.neamow.blockvariantswapper.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.neamow.blockvariantswapper.BlockVariantManager;

import java.util.List;

// This class handles the server-side logic for the Block Variant Swapper.
// It receives packets from the client (e.g., when a player scrolls) and
// executes the corresponding actions on the server.
public class NetworkHandler {

    // Register the custom packets (payloads) & set up the listeners
    public static void register() {
        PayloadTypeRegistry.playC2S().register(CycleVariantPayload.ID, CycleVariantPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PickVariantPayload.ID, PickVariantPayload.CODEC);

        // Set up the listeners. When a packet is received,
        // the code inside the lambda is run on the server.

        // BLOCK VARIANT SWAPPER (scroll to cycle variants)
        ServerPlayNetworking.registerGlobalReceiver(CycleVariantPayload.ID, (payload, context) -> {
            context.server().execute(() -> handleCycle(context.player(), payload.direction()));
        });

        // BLOCK VARIANT SWAPPER (pick-block pulls a family member and converts it to the picked variant)
        ServerPlayNetworking.registerGlobalReceiver(PickVariantPayload.ID, (payload, context) -> {
            context.server().execute(() -> handlePickVariant(context.player(), payload.sourceSlot(), payload.targetSlot(), payload.variantItemId()));
        });
    }

    // BLOCK VARIANT SWAPPER
    // Pull an owned family member from `sourceSlot`, convert it to the picked variant, and place it in
    // the hotbar `targetSlot`. Any item displaced from the target slot is moved to the source slot,
    // reverting to its base block if it happened to be a variant (variants only live in hotbar/offhand).
    private static void handlePickVariant(ServerPlayerEntity player, int sourceSlot, int targetSlot, Identifier variantItemId) {
        Item variantItem = Registries.ITEM.get(variantItemId);
        if (variantItem == Items.AIR) return;

        PlayerInventory inventory = player.getInventory();

        // Basic validation of the slots the client sent
        if (sourceSlot < 0 || sourceSlot >= inventory.size()) return;
        if (!PlayerInventory.isValidHotbarIndex(targetSlot)) return;

        ItemStack sourceStack = inventory.getStack(sourceSlot);
        if (sourceStack.isEmpty()) return;

        // The source must belong to the same variant family as the picked block
        if (BlockVariantManager.getOriginalItem(sourceStack.getItem()) != BlockVariantManager.getOriginalItem(variantItem)) {
            return;
        }

        // Convert the owned family member into the picked variant, preserving components and count
        ItemStack variantStack = sourceStack.copyComponentsToNewStack(variantItem, sourceStack.getCount());

        if (sourceSlot == targetSlot) {
            // Source is already the target hotbar slot: convert in place
            inventory.setStack(targetSlot, variantStack);
        } else {
            // Swap the converted stack into the hotbar and move the displaced item to the source slot
            ItemStack displaced = inventory.getStack(targetSlot);
            if (!displaced.isEmpty() && BlockVariantManager.isVariant(displaced.getItem())) {
                Item base = BlockVariantManager.getOriginalItem(displaced.getItem());
                displaced = displaced.copyComponentsToNewStack(base, displaced.getCount());
            }
            inventory.setStack(targetSlot, variantStack);
            inventory.setStack(sourceSlot, displaced);
        }

        inventory.markDirty();
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
