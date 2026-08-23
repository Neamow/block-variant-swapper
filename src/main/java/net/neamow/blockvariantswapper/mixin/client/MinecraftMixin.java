package net.neamow.blockvariantswapper.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neamow.blockvariantswapper.BlockVariantManager;
import net.neamow.blockvariantswapper.network.PickVariantPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin for Minecraft to make survival "Pick Block" variant-aware
// If the exact picked item isn't in the inventory, but the picked block belongs to a variant family
// the player owns a member of, we pull that member and convert it to the picked variant
//
// Inject right after vanilla resolves the picked item's slot and use MixinExtras @Local to grab the picked stack and the inventory by type/ordinal,
// which is robust against local-variable-table reordering (a plain LocalCapture broke here because the decompiled local order differs from Yarn's)
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Shadow public LocalPlayer player;

    @Inject(
        method = "pickBlock",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;findSlotMatchingItem(Lnet/minecraft/world/item/ItemStack;)I", shift = At.Shift.AFTER)
    )
    private void blockvariantswapper$pickVariantFromInventory(CallbackInfo ci,
                                                              @Local Inventory inventory,
                                                              @Local(ordinal = 0) ItemStack itemStack) {
        // Creative mode grabs the exact block itself; only augment survival pick-block
        if (this.player == null || this.player.getAbilities().instabuild) return;

        // If the exact picked item is already somewhere in the inventory, let vanilla handle it
        if (inventory.findSlotMatchingItem(itemStack) != -1) return;

        Item pickedItem = itemStack.getItem();
        Item base = BlockVariantManager.getOriginalItem(pickedItem);

        // Only proceed if the picked block is part of a known variant family
        if (BlockVariantManager.getVariants(base).isEmpty()) return;

        // Find an owned family member: prefer the currently selected slot, then anywhere else
        int sourceSlot = -1;
        if (blockvariantswapper$isFamilyMember(inventory.getItem(inventory.selected), base)) {
            sourceSlot = inventory.selected;
        } else {
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                if (blockvariantswapper$isFamilyMember(inventory.getItem(i), base)) {
                    sourceSlot = i;
                    break;
                }
            }
        }
        if (sourceSlot == -1) return; // nothing in the family to pick from

        Minecraft client = (Minecraft) (Object) this;
        // Only act if the server can actually handle the packet (e.g. it has the mod)
        if (client.getConnection() == null || !client.getConnection().hasChannel(PickVariantPayload.TYPE)) {
            return;
        }

        int targetSlot = Inventory.isHotbarSlot(sourceSlot)
            ? sourceSlot
            : inventory.getSuitableHotbarSlot();

        // The client owns the selected slot (as in vanilla pick-block); the server owns the contents
        inventory.selected = targetSlot;
        PacketDistributor.sendToServer(new PickVariantPayload(sourceSlot, targetSlot, BuiltInRegistries.ITEM.getKey(pickedItem)));
    }

    @Unique
    private boolean blockvariantswapper$isFamilyMember(ItemStack stack, Item base) {
        return !stack.isEmpty() && BlockVariantManager.getOriginalItem(stack.getItem()) == base;
    }
}
