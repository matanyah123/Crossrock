package dev.matanyah.bedrockcompat.client.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.matanyah.bedrockcompat.bedrock.discovery.BedrockPackRepository;
import dev.matanyah.bedrockcompat.client.ui.AddonsScreen;
import net.fabricmc.loader.api.FabricLoader;

public final class BedrockCompatModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new AddonsScreen(parent,
			new BedrockPackRepository(FabricLoader.getInstance().getGameDir()));
	}
}
