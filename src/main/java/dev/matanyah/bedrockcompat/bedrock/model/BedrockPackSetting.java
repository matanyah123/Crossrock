package dev.matanyah.bedrockcompat.bedrock.model;

import java.util.List;

public sealed interface BedrockPackSetting permits BedrockPackSetting.Label, BedrockPackSetting.Toggle,
	BedrockPackSetting.Slider, BedrockPackSetting.Dropdown, BedrockPackSetting.Multiselect {
	String text();

	record Label(String text) implements BedrockPackSetting {
	}

	record Toggle(String name, String text, boolean defaultValue) implements BedrockPackSetting {
	}

	record Slider(String name, String text, double minimum, double maximum, double step,
		double defaultValue) implements BedrockPackSetting {
	}

	record Dropdown(String name, String text, List<Option> options, String defaultValue)
		implements BedrockPackSetting {
		public Dropdown {
			options = List.copyOf(options);
		}
	}

	record Multiselect(String name, String text, List<Option> options, List<String> defaults) implements BedrockPackSetting {
		public Multiselect { options = List.copyOf(options); defaults = List.copyOf(defaults); }
	}

	record Option(String name, String text) {
	}
}
