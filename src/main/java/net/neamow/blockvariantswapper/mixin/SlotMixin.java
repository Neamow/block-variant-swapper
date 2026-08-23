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
@Mixin(Slot.class)
public class SlotMixin {
    @Shadow public int index;
    @Shadow public Container container;

    // Vanilla index of the offhand slot within Inventory
    @Unique
    private static final int BLOCKVARIANTSWAPPER$OFFHAND_SLOT = 40;

    // Intercepts any attempt to place an item into a slot
    @ModifyVariable(method = "set", at = @At("HEAD"), argsOnly = true)
    private ItemStack blockvariantswapper$revertVariantOutsideHotbar(ItemStack stack) {
        Item item = stack.getItem();
        if (BlockVariantManager.isVariant(item)) {
            boolean shouldRevert = true;

            // Don't revert if the target slot is in the player's hotbar or offhand
            if (this.container instanceof Inventory) {
                if (Inventory.isHotbarSlot(this.index) || this.index == BLOCKVARIANTSWAPPER$OFFHAND_SLOT) {
                    shouldRevert = false;
                }
            }

            // Anywhere else (main inventory, chests, shulkers, etc.) revert to the base block
            if (shouldRevert) {
                net.neamow.blockvariantswapper.BlockVariantSwapper.LOGGER.info(
                    "[debug] SlotMixin reverting variant {} in container={} index={}",
                    item, this.container == null ? "null" : this.container.getClass().getSimpleName(), this.index);
                Item original = BlockVariantManager.getOriginalItem(item);
                stack = stack.transmuteCopy(original, stack.getCount());
            }
        }

        return stack;
    }
}
