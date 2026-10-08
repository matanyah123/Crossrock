package dev.matanyah.eef.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class EefModMenu implements ModMenuApi {
	@Override public ConfigScreenFactory<?> getModConfigScreenFactory() { return EefConfigScreen::new; }
}
