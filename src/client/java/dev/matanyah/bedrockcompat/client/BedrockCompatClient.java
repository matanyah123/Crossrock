package dev.matanyah.bedrockcompat.client;

import dev.matanyah.bedrockcompat.bedrock.discovery.BedrockPackRepository;
import dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackRuntime;
import dev.matanyah.bedrockcompat.client.ui.AddonsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public final class BedrockCompatClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		dev.matanyah.bedrockcompat.client.entity.ArmorStandClientState.register();
		dev.matanyah.bedrockcompat.client.entity.NativeEntityResources.register();
		var gameDirectory = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
		BedrockOptionsState.initialize(gameDirectory.resolve("options.txt"));
		VirtualCompatibilityPackRuntime.initialize(gameDirectory);
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (!(screen instanceof TitleScreen)) return;
			int buttonWidth = 100;
			int x = Math.max(4, width - buttonWidth - 4);
			Screens.getWidgets(screen).add(Button.builder(Component.translatable("bedrock_compat.title.addons"), button -> {
				BedrockPackRepository repository = new BedrockPackRepository(minecraft.gameDirectory.toPath());
				minecraft.setScreenAndShow(new AddonsScreen(screen, repository));
			}).bounds(x, 4, buttonWidth, 20).build());
		});
	}
}
