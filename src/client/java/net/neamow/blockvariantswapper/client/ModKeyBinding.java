package net.neamow.blockvariantswapper.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.neamow.blockvariantswapper.BlockVariantSwapper;
import org.lwjgl.glfw.GLFW;

// Registers the mod's key bindings
public class ModKeyBinding {
    // Our own keybind category shown in the controls screen
    private static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(BlockVariantSwapper.id("general"));

    // The modifier the player holds while scrolling to cycle block variants
    // Held-state binding (checked via isDown()), default Left Alt
    public static KeyMapping swapKey;

    public static void register() {
        swapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.block-variant-swapper.swap_variant",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT,
                CATEGORY
        ));
    }
}
