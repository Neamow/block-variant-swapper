package net.neamow.blockvariantswapper.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neamow.blockvariantswapper.BlockVariantManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Mixin for the Slot class
// Whenever an item is placed into a slot, this reverts block variants back to their base block unless the target is a player hotbar or offhand slot
// This is the catch-all that keeps variants out of the main inventory and every storage container (chests, shulkers, etc.)
//
// NOTE: Slot has two indices for some reason:
// 1. The public `index` field is the slot's position within its *menu* (e.g. 36-44 for the hotbar in InventoryMenu)
// 2. The `getContainerSlot()` returns the index within the backing *container* (0-8 for the hotbar in the player Inventory)
// We must test the container index against Inventory.isHotbarSlot, which expects container indices
// Using the menu `index` here is wrong and would revert the held item on the client
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Shadow public abstract int getContainerSlot();

    @Shadow public Container container;

    // Vanilla index of the offhand slot within the player Inventory
    @Unique
    private static final int BLOCKVARIANTSWAPPER$OFFHAND_SLOT = 40;

    // Intercepts any attempt to place an item into a slot.
    @ModifyVariable(method = "set", at = @At("HEAD"), argsOnly = true)
    private ItemStack blockvariantswapper$revertVariantOutsideHotbar(ItemStack stack) {
        Item item = stack.getItem();
        if (BlockVariantManager.isVariant(item)) {
            boolean shouldRevert = true;

            // Don't revert if the target slot is in the player's hotbar or offhand
            if (this.container instanceof Inventory) {
                int containerSlot = this.getContainerSlot();
                if (Inventory.isHotbarSlot(containerSlot) || containerSlot == BLOCKVARIANTSWAPPER$OFFHAND_SLOT) {
                    shouldRevert = false;
                }
            }

            // Anywhere else (main inventory, chests, shulkers, etc.) revert to the base block
            if (shouldRevert) {
                Item original = BlockVariantManager.getOriginalItem(item);
                stack = stack.transmuteCopy(original, stack.getCount());
            }
        }

        return stack;
    }
}
