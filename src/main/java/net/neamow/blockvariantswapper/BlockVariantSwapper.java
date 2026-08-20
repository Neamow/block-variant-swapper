package net.neamow.blockvariantswapper;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.neamow.blockvariantswapper.network.NetworkHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockVariantSwapper implements ModInitializer {
	public static final String MOD_ID = "blockvariantswapper";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Block Variant Swapper");

		// Set up the network handling for server-client communication
		NetworkHandler.register();

		// Ensure the user config directory exists so players have a place to add overrides.
		// Defaults live in the jar and are loaded at reload time; no files are generated here.
		BlockVariantConfig.createConfigDirectory();

		// Register a resource reload listener to initialise the BlockVariantManager.
		// This ensures it runs after all mods have registered their content, on both client and server.
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				// Unique identifier for this listener
				return Identifier.of(BlockVariantSwapper.MOD_ID, "block_variant_loader");
			}

			@Override
			public void reload(ResourceManager manager) {
				// This is the correct time to initialise, as all registries are populated
				BlockVariantManager.initialize();
			}
		});
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
