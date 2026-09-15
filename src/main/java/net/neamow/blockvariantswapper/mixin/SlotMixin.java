package net.neamow.blockvariantswapper.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neamow.blockvariantswapper.BlockVariantManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Mixin for the Slot class
// Whenever an item is placed into a slot, this reverts block variants back to their base block unless the target is a player hotbar or offhand slot
// This is the catch-all that keeps variants out of the main inventory and every storage container
@Mixin(Slot.class)
public class SlotMixin {
    @Shadow @Final private int slot;
    @Shadow @Final public Container container;

    // Intercepts any attempt to place a stack into a slot
    @ModifyVariable(method = "set(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), argsOnly = true)
    private ItemStack blockvariantswapper$revertVariantOutsideHotbar(ItemStack stack) {
        Item item = stack.getItem();
        if (BlockVariantManager.isVariant(item)) {
            boolean shouldRevert = true;

            // Don't revert if the target slot is in the player's hotbar or offhand
            // We check the container-relative 'slot' field (0-8 = hotbar, 40 = offhand), not the menu-relative 'index' field
            // (same issue as in the 1.21.1 NeoForge port)
            if (this.container instanceof Inventory) {
                if (Inventory.isHotbarSlot(this.slot) || this.slot == Inventory.SLOT_OFFHAND) {
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
