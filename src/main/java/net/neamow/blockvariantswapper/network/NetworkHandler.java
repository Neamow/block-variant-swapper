package net.neamow.blockvariantswapper.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neamow.blockvariantswapper.BlockVariantManager;
import net.neamow.blockvariantswapper.BlockVariantSwapper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;

// Server-side logic; receives C2S payloads from the client (scroll to cycle, pick-block) and executes the authoritative inventory changes on the server
//
// On NeoForge, payloads are registered via RegisterPayloadHandlersEvent on the mod event bus
// Payload handlers run on the main game thread by default, so no manual thread hop is needed
public class NetworkHandler {

    // Called from the mod constructor; subscribes to the payload registration event
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NetworkHandler::registerPayloads);
    }

    @SubscribeEvent
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // Both payloads are client-to-server only
        registrar.playToServer(
            CycleVariantPayload.TYPE,
            CycleVariantPayload.STREAM_CODEC,
            NetworkHandler::onCycle
        );
        registrar.playToServer(
            PickVariantPayload.TYPE,
            PickVariantPayload.STREAM_CODEC,
            NetworkHandler::onPickVariant
        );
    }

    // We never trust client input to behave; any exception is caught and logged rather than allowed to crash the server tick
    private static void onCycle(CycleVariantPayload payload, IPayloadContext context) {
        try {
            if (context.player() instanceof ServerPlayer player) {
                handleCycle(player, payload.direction());
            }
        } catch (Exception e) {
            BlockVariantSwapper.LOGGER.error("Error handling variant cycle packet", e);
        }
    }

    private static void onPickVariant(PickVariantPayload payload, IPayloadContext context) {
        try {
            if (context.player() instanceof ServerPlayer player) {
                handlePickVariant(player, payload.sourceSlot(), payload.targetSlot(), payload.variantItemId());
            }
        } catch (Exception e) {
            BlockVariantSwapper.LOGGER.error("Error handling pick-variant packet", e);
        }
    }

    // Pull an owned family member from `sourceSlot`, convert it to the picked variant, and place it in the hotbar `targetSlot`
    // Any item displaced from the target slot is moved to the source slot, reverting to its base block if it happened to be a variant
    private static void handlePickVariant(ServerPlayer player, int sourceSlot, int targetSlot, ResourceLocation variantItemId) {
        Item variantItem = BuiltInRegistries.ITEM.get(variantItemId);
        if (variantItem == Items.AIR) return;

        Inventory inventory = player.getInventory();

        // Basic validation of the slots the client sent
        if (sourceSlot < 0 || sourceSlot >= inventory.getContainerSize()) return;
        if (!Inventory.isHotbarSlot(targetSlot)) return;

        ItemStack sourceStack = inventory.getItem(sourceSlot);
        if (sourceStack.isEmpty()) return;

        // The source must belong to the same variant family as the picked block
        if (BlockVariantManager.getOriginalItem(sourceStack.getItem()) != BlockVariantManager.getOriginalItem(variantItem)) {
            return;
        }

        // Convert the owned family member into the picked variant, preserving components and count
        ItemStack variantStack = sourceStack.transmuteCopy(variantItem, sourceStack.getCount());

        if (sourceSlot == targetSlot) {
            // Source is already the target hotbar slot: convert in place
            inventory.setItem(targetSlot, variantStack);
        } else {
            // Swap the converted stack into the hotbar and move the displaced item to the source slot
            ItemStack displaced = inventory.getItem(targetSlot);
            if (!displaced.isEmpty() && BlockVariantManager.isVariant(displaced.getItem())) {
                Item base = BlockVariantManager.getOriginalItem(displaced.getItem());
                displaced = displaced.transmuteCopy(base, displaced.getCount());
            }
            inventory.setItem(targetSlot, variantStack);
            inventory.setItem(sourceSlot, displaced);
        }

        inventory.setChanged();
        // Force a client resync (see handleCycle):
        // direct inventory writes bypass the container-menu change tracking that normally pushes slot updates to the client
        player.inventoryMenu.broadcastChanges();
    }

    // Cycles through the block variants of the selected hotbar slot block
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
        inventory.setItem(inventory.selected, newStack);

        // Writing directly to the inventory bypasses the player's container-menu change tracking, so
        // the client's copy of the slot never refreshes. Force a full resync so the held item updates.
        player.inventoryMenu.broadcastFullState();
    }
}
