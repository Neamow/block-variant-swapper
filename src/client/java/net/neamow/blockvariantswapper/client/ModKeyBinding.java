package net.neamow.blockvariantswapper.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.neamow.blockvariantswapper.BlockVariantSwapper;

// Registers the mod's key bindings
public class ModKeyBinding {
    // Our own keybind category shown in the controls screen
    private static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(BlockVariantSwapper.id("general"));

    // Default swap key, resolved by name so we don't need the GLFW library on the compile classpath
    private static final int DEFAULT_KEY = InputConstants.getKey("key.keyboard.left.alt").getValue();

    // The modifier the player holds while scrolling to cycle block variants
    // Held-state binding (checked via isDown()), default Left Alt
    public static KeyMapping swapKey;

    public static void register() {
        swapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.block-variant-swapper.swap_variant",
                InputConstants.Type.KEYBOARD,
                DEFAULT_KEY,
                CATEGORY
        ));
    }
}
