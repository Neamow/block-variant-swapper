package net.neamow.blockvariantswapper.mixin.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neamow.blockvariantswapper.BlockVariantManager;
import net.neamow.blockvariantswapper.client.BlockVariantSwapperClientState;
import net.neamow.blockvariantswapper.client.ModKeyBinding;
import net.neamow.blockvariantswapper.network.CycleVariantPayload;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Mixin for MouseHandler to intercept scroll-wheel input for block variant swapping
@Mixin(MouseHandler.class)
public class MouseMixin {
    @Shadow @Final private Minecraft minecraft;

    // Intercepts the scroll just before it changes the selected hotbar slot
    @Inject(
        method = "onScroll",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"),
        cancellable = true
    )
    private void onScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        // Only act if the swap key is held down
        if (ModKeyBinding.swapKey.isDown() && this.minecraft.player != null) {
            Inventory inventory = this.minecraft.player.getInventory();
            ItemStack stack = inventory.getSelectedItem();

            if (stack.isEmpty()) return;

            // Determine the base item for the variant group
            Item originalItem = BlockVariantManager.getOriginalItem(stack.getItem());

            List<Item> variants = BlockVariantManager.getVariants(originalItem);
            if (variants.isEmpty() || variants.size() <= 1) return;

            // Only act if the server can actually handle the packet (e.g. it has the mod)
            // Otherwise let the vanilla hotbar scroll happen normally
            if (!ClientPlayNetworking.canSend(CycleVariantPayload.TYPE)) return;

            // Send a packet to the server to cycle the variant
            int direction = (int) Math.signum(yoffset);
            ClientPlayNetworking.send(new CycleVariantPayload(direction));

            // Trigger a client-side animation
            BlockVariantSwapperClientState.onScroll(direction);

            // Cancel the vanilla hotbar scroll
            ci.cancel();
        }
    }
}
