package net.neamow.blockvariantswapper.client;

// Simple state container for client-side animations
// Tracks scroll input for visual feedback (item icons shifting up and down)
public class BlockVariantSwapperClientState {
    public static float scrollAnimation = 0.0f;

    // Updates the scroll animation state when the player scrolls the mouse wheel
    public static void onScroll(int direction) {
        scrollAnimation = (float) direction;
    }
}
