package net.neamow.blockvariantswapper;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neamow.blockvariantswapper.network.NetworkHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockVariantSwapper implements ModInitializer {
	public static final String MOD_ID = "block-variant-swapper";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Block Variant Swapper");

		// Set up the network handling for server-client communication
		NetworkHandler.register();

		// Ensure the user config directory exists so players have a place to add overrides
		// Defaults live in the jar and are loaded at reload time; no files are generated here
		BlockVariantConfig.createConfigDirectory();

		// Register a data reload listener to initialise the BlockVariantManager after all registries are populated,
		// on both client and server (world load and /reload)
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.fromNamespaceAndPath(BlockVariantSwapper.MOD_ID, "block_variant_loader");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				BlockVariantManager.initialize();
			}
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
