package dev.matanyah.bedrockcompat.client;

import dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackRuntime;
import net.fabricmc.api.ClientModInitializer;

public final class BedrockCompatClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		dev.matanyah.bedrockcompat.client.behavior.BehaviorClientSession.register();
		dev.matanyah.bedrockcompat.client.block.NativeBlockModels.register();
		dev.matanyah.bedrockcompat.client.ui.json.NativeJsonUi.register();
		dev.matanyah.bedrockcompat.client.environment.NativeEnvironment.register();
		dev.matanyah.bedrockcompat.client.entity.ArmorStandClientState.register();
		dev.matanyah.bedrockcompat.client.entity.NativeEntityResources.register();
		dev.matanyah.bedrockcompat.client.particle.NativeParticles.register();
		var gameDirectory = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
		BedrockOptionsState.initialize(gameDirectory.resolve("options.txt"));
		VirtualCompatibilityPackRuntime.initialize(gameDirectory);
	}
}
