package net.neamow.blockvariantswapper.mixin;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.neamow.blockvariantswapper.BlockVariantManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// BLOCK VARIANT SWAPPER
// Mixin for the Slot class.
// Whenever an item is placed into a slot, this reverts block variants back to their
// base block unless the target is a player hotbar or offhand slot. This is the catch-all
// that keeps variants out of the main inventory and every storage container.
@Mixin(Slot.class)
public class SlotMixin {
    @Shadow public int index;
    @Shadow public Inventory inventory;

    // Vanilla index of the offhand slot within PlayerInventory
    @Unique
    private static final int BLOCKVARIANTSWAPPER$OFFHAND_SLOT = 40;

    // Intercepts any attempt to place an item into a slot
    @ModifyVariable(method = "setStack", at = @At("HEAD"), argsOnly = true)
    private ItemStack blockvariantswapper$revertVariantOutsideHotbar(ItemStack stack) {
        Item item = stack.getItem();
        if (BlockVariantManager.isVariant(item)) {
            boolean shouldRevert = true;

            // Don't revert if the target slot is in the player's hotbar or offhand
            if (this.inventory instanceof PlayerInventory) {
                if (PlayerInventory.isValidHotbarIndex(this.index) || this.index == BLOCKVARIANTSWAPPER$OFFHAND_SLOT) {
                    shouldRevert = false;
                }
            }

            // Anywhere else (main inventory, chests, shulkers, etc.) revert to the base block
            if (shouldRevert) {
                Item original = BlockVariantManager.getOriginalItem(item);
                stack = stack.copyComponentsToNewStack(original, stack.getCount());
            }
        }

        return stack;
    }
}
