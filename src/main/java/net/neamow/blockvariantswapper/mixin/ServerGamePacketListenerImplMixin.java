package net.neamow.blockvariantswapper.mixin;

import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neamow.blockvariantswapper.BlockVariantManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Makes Survival pick-block variant-aware
// Pick-block is server-authoritative: the client asks the server to pick a block,
// and the server resolves the item and puts it in the hotbar via tryPickItem
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow public ServerPlayer player;

    @Inject(method = "tryPickItem", at = @At("HEAD"), cancellable = true)
    private void blockvariantswapper$pickVariant(ItemStack itemStack, CallbackInfo ci) {
        // Creative already grabs the exact block; let vanilla handle it
        if (this.player.hasInfiniteMaterials()) return;

        Inventory inventory = this.player.getInventory();

        // If the exact picked item is already owned, let vanilla select/swap it normally
        if (inventory.findSlotMatchingItem(itemStack) != -1) return;

        Item pickedItem = itemStack.getItem();
        Item base = BlockVariantManager.getOriginalItem(pickedItem);

        // Only proceed if the picked block is part of a known variant family
        if (BlockVariantManager.getVariants(base).isEmpty()) return;

        // Find an owned family member: prefer the currently selected slot, then anywhere else
        int sourceSlot = -1;
        if (blockvariantswapper$isFamilyMember(inventory.getItem(inventory.getSelectedSlot()), base)) {
            sourceSlot = inventory.getSelectedSlot();
        } else {
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                if (blockvariantswapper$isFamilyMember(inventory.getItem(i), base)) {
                    sourceSlot = i;
                    break;
                }
            }
        }
        if (sourceSlot == -1) return;

        ItemStack sourceStack = inventory.getItem(sourceSlot);
        // Convert the owned family member into the picked variant, preserving components and count
        ItemStack variantStack = sourceStack.transmuteCopy(pickedItem, sourceStack.getCount());

        if (Inventory.isHotbarSlot(sourceSlot)) {
            // Already in the hotbar: convert in place and select it
            inventory.setItem(sourceSlot, variantStack);
            inventory.setSelectedSlot(sourceSlot);
        } else {
            // In the main inventory: move it into a suitable hotbar slot, then convert
            inventory.setItem(sourceSlot, variantStack);
            inventory.pickSlot(sourceSlot);
        }

        // Sync the new selected slot and inventory contents to the client
        this.player.connection.send(new ClientboundSetHeldSlotPacket(inventory.getSelectedSlot()));
        this.player.inventoryMenu.broadcastChanges();

        // We fully handled the pick; skip vanilla's tryPickItem
        ci.cancel();
    }

    private boolean blockvariantswapper$isFamilyMember(ItemStack stack, Item base) {
        return !stack.isEmpty() && BlockVariantManager.getOriginalItem(stack.getItem()) == base;
    }
}
