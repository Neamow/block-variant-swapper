package net.neamow.blockvariantswapper.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neamow.blockvariantswapper.BlockVariantSwapper;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

// Registers the mod's key bindings
public class ModKeyBinding {
    // Our own keybind category shown in the controls screen
    private static final KeyMapping.Category CATEGORY =
        new KeyMapping.Category(Identifier.fromNamespaceAndPath(BlockVariantSwapper.MOD_ID, "general"));

    // The modifier the player holds while scrolling to cycle block variants
    // Held-state binding (checked via isDown()), default Left Alt
    public static KeyMapping swapKey;

    public static void register(RegisterKeyMappingsEvent event) {
        // Register the custom category first
        event.registerCategory(CATEGORY);

        swapKey = new KeyMapping(
                "key.blockvariantswapper.swap_variant",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT,
                CATEGORY
        );
        event.register(swapKey);
    }
}
