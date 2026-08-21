package net.neamow.blockvariantswapper.mixin.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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

// BLOCK VARIANT SWAPPER
// Mixin for the Mouse class to handle scroll wheel input for block variant swapping
@Mixin(Mouse.class)
public class MouseMixin {
    @Shadow @Final private MinecraftClient client;

    // Intercepts the onMouseScroll method before it scrolls the player's hotbar
    @Inject(method = "onMouseScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;scrollInHotbar(D)V"), cancellable = true)
    private void onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        // If the swap key is held down and no screen is open, handle block variant swapping
        if (ModKeyBinding.swapKey.isPressed() && this.client.player != null && this.client.currentScreen == null) {
            PlayerInventory inventory = this.client.player.getInventory();
            ItemStack stack = inventory.getMainHandStack();

            if (stack.isEmpty()) return;

            // Determine the base item for the variant group
            Item originalItem = BlockVariantManager.getOriginalItem(stack.getItem());

            List<Item> variants = BlockVariantManager.getVariants(originalItem);
            if (variants.isEmpty() || variants.size() <= 1) return;

            // Only act if the server can actually handle the packet (e.g. it has the mod).
            // Otherwise let the vanilla hotbar scroll happen normally.
            if (!ClientPlayNetworking.canSend(CycleVariantPayload.ID)) return;

            // Send a packet to the server to cycle the variant
            int direction = (int) Math.signum(vertical);
            ClientPlayNetworking.send(new CycleVariantPayload(direction));

            // Trigger a client-side animation
            BlockVariantSwapperClientState.onScroll(direction);

            // Cancel the original hotbar scroll
            ci.cancel();
        }
    }
}
