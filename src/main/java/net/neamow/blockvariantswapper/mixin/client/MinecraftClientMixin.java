package net.neamow.blockvariantswapper.mixin.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.HitResult;
import net.neamow.blockvariantswapper.BlockVariantManager;
import net.neamow.blockvariantswapper.network.PickVariantPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

// BLOCK VARIANT SWAPPER
// Mixin for MinecraftClient to make survival "Pick Block" variant-aware.
// If the exact picked item isn't in the inventory, but the picked block belongs to a variant
// family the player owns a member of, we pull that member and convert it to the picked variant.
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    // Injected right after vanilla looks up the picked item's slot. At this point `bl` is the
    // creative flag, `itemStack` is the picked item, and `playerInventory` is the player's inventory.
    @Inject(
        method = "doItemPick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;getSlotWithStack(Lnet/minecraft/item/ItemStack;)I", shift = At.Shift.AFTER),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void blockvariantswapper$pickVariantFromInventory(CallbackInfo ci, boolean bl, BlockEntity blockEntity, ItemStack itemStack, HitResult.Type type, PlayerInventory playerInventory) {
        // Creative already grabs the exact block; nothing to do
        if (bl) return;

        // If the exact picked item is already somewhere in the inventory, let vanilla handle it
        // (select the hotbar slot, or swap it up from the main inventory)
        if (playerInventory.getSlotWithStack(itemStack) != -1) return;

        Item pickedItem = itemStack.getItem();
        Item base = BlockVariantManager.getOriginalItem(pickedItem);

        // Only proceed if the picked block is part of a known variant family
        if (BlockVariantManager.getVariants(base).isEmpty()) return;

        // Find an owned family member: prefer the currently selected slot, then anywhere else
        int sourceSlot = -1;
        if (blockvariantswapper$isFamilyMember(playerInventory.getStack(playerInventory.selectedSlot), base)) {
            sourceSlot = playerInventory.selectedSlot;
        } else {
            for (int i = 0; i < playerInventory.size(); i++) {
                if (blockvariantswapper$isFamilyMember(playerInventory.getStack(i), base)) {
                    sourceSlot = i;
                    break;
                }
            }
        }
        if (sourceSlot == -1) return; // nothing in the family to pick from

        // Decide the destination hotbar slot. If the source is already in the hotbar, convert it in
        // place; otherwise pick a swappable hotbar slot (same choice vanilla makes for pick-from-inventory).
        int targetSlot = PlayerInventory.isValidHotbarIndex(sourceSlot)
            ? sourceSlot
            : playerInventory.getSwappableHotbarSlot();

        // The client owns the selected slot (as in vanilla pick-block); the server owns the contents.
        playerInventory.selectedSlot = targetSlot;
        ClientPlayNetworking.send(new PickVariantPayload(sourceSlot, targetSlot, Registries.ITEM.getId(pickedItem)));
    }

    @Unique
    private boolean blockvariantswapper$isFamilyMember(ItemStack stack, Item base) {
        return !stack.isEmpty() && BlockVariantManager.getOriginalItem(stack.getItem()) == base;
    }
}
