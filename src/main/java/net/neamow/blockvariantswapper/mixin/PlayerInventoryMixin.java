package net.neamow.blockvariantswapper.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neamow.blockvariantswapper.BlockVariantManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

// Mixin for the player Inventory
// Makes item pickup variant-aware, so a picked-up base block merges into a hotbar stack that is
// currently displayed as one of its variants, and keeps variants confined to the hotbar
@Mixin(Inventory.class)
public abstract class PlayerInventoryMixin implements Container {

    // Live view of the 36 main inventory slots (27 storage + 9 hotbar)
    private NonNullList<ItemStack> blockvariantswapper$items() {
        return ((Inventory) (Object) this).getNonEquipmentItems();
    }

    // Each tick, convert any variant sitting outside the hotbar back to its base item
    // Safety net for things that bypass slots (e.g. /give)
    @Inject(method = "tick", at = @At("HEAD"))
    private void blockvariantswapper$revertVariantsOutsideHotbar(CallbackInfo ci) {
        NonNullList<ItemStack> items = this.blockvariantswapper$items();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            Item item = stack.getItem();
            if (BlockVariantManager.isVariant(item) && !Inventory.isHotbarSlot(i)) {
                Item original = BlockVariantManager.getOriginalItem(item);
                items.set(i, stack.transmuteCopy(original, stack.getCount()));
            }
        }
    }

    // Override the vanilla slot-search used on item pickup so a base block can stack
    // with a hotbar/offhand stack currently showing one of its variants
    @Inject(method = "getSlotWithRemainingSpace", at = @At("HEAD"), cancellable = true)
    private void blockvariantswapper$variantAwareSlotSearch(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        Inventory inventory = (Inventory) (Object) this;
        if (this.blockvariantswapper$canMergeWithVariant(inventory.getItem(inventory.getSelectedSlot()), stack)) {
            cir.setReturnValue(inventory.getSelectedSlot());
        } else if (this.blockvariantswapper$canMergeWithVariant(inventory.getItem(Inventory.SLOT_OFFHAND), stack)) {
            cir.setReturnValue(Inventory.SLOT_OFFHAND);
        } else {
            NonNullList<ItemStack> items = this.blockvariantswapper$items();
            for (int i = 0; i < items.size(); i++) {
                if (this.blockvariantswapper$canMergeWithVariant(items.get(i), stack)) {
                    cir.setReturnValue(i);
                    return;
                }
            }
            cir.setReturnValue(-1);
        }
    }

    // Check if two stacks can merge considering block variants
    // Treats a base block and any of its variants as stackable, as long as components match
    private boolean blockvariantswapper$canMergeWithVariant(ItemStack existingStack, ItemStack stack) {
        if (existingStack.isEmpty() || !existingStack.isStackable() || existingStack.getCount() >= existingStack.getMaxStackSize()) {
            return false;
        }

        Item existingOriginal = BlockVariantManager.getOriginalItem(existingStack.getItem());
        Item stackOriginal = BlockVariantManager.getOriginalItem(stack.getItem());

        if (existingOriginal != stackOriginal) {
            return false;
        }

        // Only merge when custom component data matches, so renamed/enchanted stacks stay separate
        return Objects.equals(existingStack.getComponentsPatch(), stack.getComponentsPatch());
    }
}
