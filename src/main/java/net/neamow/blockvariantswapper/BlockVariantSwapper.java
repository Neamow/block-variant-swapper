package net.neamow.blockvariantswapper;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neamow.blockvariantswapper.network.NetworkHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

// Main entrypoint, register network payloads on the mod event bus and ensures the (empty) user config directory exists
// Game-event listeners (block drops, the server-data reload that (re)builds the variant families)
// live in ModEvents, registered automatically via @EventBusSubscriber.
@Mod(BlockVariantSwapper.MOD_ID)
public class BlockVariantSwapper {
    public static final String MOD_ID = "blockvariantswapper";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BlockVariantSwapper(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing Block Variant Swapper");

        // Network payloads are registered on the mod event bus (RegisterPayloadHandlersEvent)
        NetworkHandler.register(modEventBus);

        // Ensure the user config directory exists so players have a place to add overrides
        // Defaults live in the jar and are loaded at reload time; no files are generated here
        BlockVariantConfig.createConfigDirectory();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
