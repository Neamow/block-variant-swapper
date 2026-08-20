package net.neamow.blockvariantswapper.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

// BLOCK VARIANT SWAPPER
// Registers the mod's key bindings
public class ModKeyBinding {
    // The modifier the player holds while scrolling to cycle block variants
    // Held-state binding (checked via isPressed()), default Left Alt
    public static KeyBinding swapKey;

    public static void register() {
        swapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.blockvariantswapper.swap_variant",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT,
                "category.blockvariantswapper.general"
        ));
    }
}
