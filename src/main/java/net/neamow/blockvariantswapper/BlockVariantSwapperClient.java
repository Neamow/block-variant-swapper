package net.neamow.blockvariantswapper;

import net.fabricmc.api.ClientModInitializer;
import net.neamow.blockvariantswapper.client.ModKeyBinding;

public class BlockVariantSwapperClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModKeyBinding.register();
    }
}
