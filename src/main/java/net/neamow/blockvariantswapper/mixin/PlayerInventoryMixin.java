package net.neamow.blockvariantswapper.mixin;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
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

// BLOCK VARIANT SWAPPER
// Mixin for PlayerInventory
// Makes item pickup variant-aware, so a picked-up base block merges into a hotbar
// stack that is currently displayed as one of its variants, and keeps variants confined to the hotbar
@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin implements Inventory {

    @Shadow @Final public DefaultedList<ItemStack> main;
    @Shadow public int selectedSlot;

    @Shadow public abstract ItemStack getStack(int slot);

    // Vanilla index of the offhand slot within PlayerInventory
    @Unique
    private static final int BLOCKVARIANTSWAPPER$OFFHAND_SLOT = 40;

    // Ensure that items in the inventory which are variants get converted back to their base item if they are not in the hotbar
    // This maintains the "variants only exist in the hotbar" logic
    @Inject(method = "updateItems", at = @At("HEAD"))
    private void blockvariantswapper$revertVariantsOutsideHotbar(CallbackInfo ci) {
        PlayerInventory inventory = (PlayerInventory) (Object) this;
        for (int i = 0; i < inventory.main.size(); i++) {
            ItemStack stack = inventory.main.get(i);
            Item item = stack.getItem();
            if (BlockVariantManager.isVariant(item) && !PlayerInventory.isValidHotbarIndex(i)) {
                Item original = BlockVariantManager.getOriginalItem(item);
                ItemStack newStack = stack.copyComponentsToNewStack(original, stack.getCount());
                inventory.main.set(i, newStack);
            }
        }
    }

    // Override the vanilla slot-search used on item pickup so that variants can stack
    // with their base item (or other variants that share the same base)
    @Inject(method = "getOccupiedSlotWithRoomForStack", at = @At("HEAD"), cancellable = true)
    private void blockvariantswapper$variantAwareSlotSearch(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (this.blockvariantswapper$canMergeWithVariant(this.getStack(this.selectedSlot), stack)) {
            cir.setReturnValue(this.selectedSlot);
        } else if (this.blockvariantswapper$canMergeWithVariant(this.getStack(BLOCKVARIANTSWAPPER$OFFHAND_SLOT), stack)) {
            cir.setReturnValue(BLOCKVARIANTSWAPPER$OFFHAND_SLOT);
        } else {
            PlayerInventory inventory = (PlayerInventory) (Object) this;
            for (int i = 0; i < inventory.main.size(); i++) {
                if (this.blockvariantswapper$canMergeWithVariant(inventory.main.get(i), stack)) {
                    cir.setReturnValue(i);
                    return;
                }
            }
            cir.setReturnValue(-1);
        }
    }

    // Helper to check if two stacks can merge, considering block variants.
    // This is a superset of vanilla's canStackAddMore: it treats a base block and any
    // of its variants as stackable together, as long as their components match.
    @Unique
    private boolean blockvariantswapper$canMergeWithVariant(ItemStack existingStack, ItemStack stack) {
        if (existingStack.isEmpty() || !existingStack.isStackable() || existingStack.getCount() >= existingStack.getMaxCount()) {
            return false;
        }

        Item existingOriginal = BlockVariantManager.getOriginalItem(existingStack.getItem());
        Item stackOriginal = BlockVariantManager.getOriginalItem(stack.getItem());

        if (existingOriginal != stackOriginal) {
            return false;
        }

        // Only merge when the components are compatible, so custom-named/enchanted stacks stay separate
        return Objects.equals(existingStack.getComponents(), stack.getComponents());
    }
}
