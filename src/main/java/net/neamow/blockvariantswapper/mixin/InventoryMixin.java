package net.neamow.blockvariantswapper.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neamow.blockvariantswapper.BlockVariantManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

// Mixin for the player Inventory
// Makes item pickup variant-aware, so a picked-up base block merges into a hotbar stack that is
// currently displayed as one of its variants, and keeps variants confined to the hotbar
@Mixin(Inventory.class)
public abstract class InventoryMixin implements Container {

    @Shadow @Final public NonNullList<ItemStack> items;
    @Shadow public int selected;

    @Shadow public abstract ItemStack getItem(int slot);

    // Vanilla index of the offhand slot within Inventory
    @Unique
    private static final int BLOCKVARIANTSWAPPER$OFFHAND_SLOT = 40;

    // Revert any variant sitting outside the hotbar in the main inventory
    // This is the per-tick safety net for things that bypass slots (e.g. /give); runs every inventory tick
    @Inject(method = "tick", at = @At("HEAD"))
    private void blockvariantswapper$revertVariantsOutsideHotbar(CallbackInfo ci) {
        for (int i = 0; i < this.items.size(); i++) {
            ItemStack stack = this.items.get(i);
            Item item = stack.getItem();
            if (BlockVariantManager.isVariant(item) && !Inventory.isHotbarSlot(i)) {
                net.neamow.blockvariantswapper.BlockVariantSwapper.LOGGER.info(
                    "[debug] InventoryMixin.tick reverting variant {} at index {}", item, i);
                Item original = BlockVariantManager.getOriginalItem(item);
                ItemStack newStack = stack.transmuteCopy(original, stack.getCount());
                this.items.set(i, newStack);
            }
        }
    }

    // Override the vanilla slot-search used on item pickup so that variants can stack with their base item (or other variants that share the same base)
    // Search order: selected -> offhand -> main
    @Inject(method = "getSlotWithRemainingSpace", at = @At("HEAD"), cancellable = true)
    private void blockvariantswapper$variantAwareSlotSearch(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (this.blockvariantswapper$canMergeWithVariant(this.getItem(this.selected), stack)) {
            cir.setReturnValue(this.selected);
        } else if (this.blockvariantswapper$canMergeWithVariant(this.getItem(BLOCKVARIANTSWAPPER$OFFHAND_SLOT), stack)) {
            cir.setReturnValue(BLOCKVARIANTSWAPPER$OFFHAND_SLOT);
        } else {
            for (int i = 0; i < this.items.size(); i++) {
                if (this.blockvariantswapper$canMergeWithVariant(this.items.get(i), stack)) {
                    cir.setReturnValue(i);
                    return;
                }
            }
            cir.setReturnValue(-1);
        }
    }

    // Helper to check if two stacks can merge, considering block variants
    // This is a superset of  vanilla's canStackAddMore: it treats a base block and any of its variants as stackable together,
    // as long as their components match (so custom-named/enchanted stacks stay separate)
    @Unique
    private boolean blockvariantswapper$canMergeWithVariant(ItemStack existingStack, ItemStack stack) {
        if (existingStack.isEmpty() || !existingStack.isStackable() || existingStack.getCount() >= existingStack.getMaxStackSize()) {
            return false;
        }

        Item existingOriginal = BlockVariantManager.getOriginalItem(existingStack.getItem());
        Item stackOriginal = BlockVariantManager.getOriginalItem(stack.getItem());

        if (existingOriginal != stackOriginal) {
            return false;
        }

        return Objects.equals(existingStack.getComponents(), stack.getComponents());
    }
}
