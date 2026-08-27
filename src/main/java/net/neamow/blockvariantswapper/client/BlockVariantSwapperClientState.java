package net.neamow.blockvariantswapper.client;

// Simple state container for client-side animations
// Tracks scroll input for visual feedback (item icons shifting up and down)
public class BlockVariantSwapperClientState {
    public static float scrollAnimation = 0.0f;

    // Sets the animation value based on scroll direction (up or down)
    public static void onScroll(int direction) {
        scrollAnimation = (float) direction;
    }
}
