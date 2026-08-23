package net.neamow.blockvariantswapper.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

// Keybindings
// The swap key is a held-state binding (checked via isDown()), default Left Alt: the player holds it and scrolls to cycle block variants
public class ModKeyBinding {
    public static final KeyMapping SWAP_KEY = new KeyMapping(
            "key.blockvariantswapper.swap_variant",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            "category.blockvariantswapper.general"
    );
}
