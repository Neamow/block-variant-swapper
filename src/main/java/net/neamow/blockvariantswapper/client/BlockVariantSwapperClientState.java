package net.neamow.blockvariantswapper.client;

// BLOCK VARIANT SWAPPER
// Simple state container for client-side animations
// Used to track scroll input for visual feedback (item icons shifting up and down)
public class BlockVariantSwapperClientState {
    public static float scrollAnimation = 0.0f;

    // Updates the scroll animation state when the player scrolls the mouse wheel
    public static void onScroll(int direction) {
        // Sets the animation value based on scroll direction (up or down)
        scrollAnimation = (float) direction;
    }
}
