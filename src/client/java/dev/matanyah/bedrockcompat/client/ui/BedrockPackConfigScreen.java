package dev.matanyah.bedrockcompat.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.matanyah.bedrockcompat.BedrockCompat;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackConfiguration;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackSetting;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockSubpack;
import dev.matanyah.bedrockcompat.client.BedrockOptionsState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BedrockPackConfigScreen extends Screen {
	private static final Identifier DEFAULT_ICON = Identifier.withDefaultNamespace("textures/misc/unknown_pack.png");
	private static final int PANEL_PADDING = 12;
	private final Screen parent;
	private final String packId;
	private final BedrockPack pack;
	private final Map<String, Object> values = new LinkedHashMap<>();
	private String selectedSubpack;
	private Identifier icon = DEFAULT_ICON;
	private boolean ownsIcon;
	private ConfigList list;
	private boolean documentation;

	public BedrockPackConfigScreen(Screen parent, String packId, BedrockPack pack) {
		super(Component.translatable("bedrock_compat.config.title", pack.displayName()));
		this.parent = parent;
		this.packId = packId;
		this.pack = pack;
		BedrockPackConfiguration configuration = BedrockOptionsState.configuration(packId, pack);
		selectedSubpack = configuration.selectedSubpack().orElse(null);
		values.putAll(configuration.settingValues());
	}

	@Override
	protected void init() {
		loadIcon();
		int panelWidth = panelWidth();
		int x = (width - panelWidth) / 2;
		list = addRenderableWidget(new ConfigList(minecraft, panelWidth - 12, Math.max(30, height - 138)));
		list.updateSizeAndPosition(panelWidth - 12, Math.max(30, height - 138), x + 6, 100);

        addRenderableWidget(Button.builder(Component.literal("Settings"), button -> { documentation = false; rebuildWidgets(); })
            .tooltip(Tooltip.create(Component.literal("Configure this pack's subpacks and settings.")))
            .bounds(x + 6, 74, (panelWidth - 16) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Description & Docs"), button -> { documentation = true; rebuildWidgets(); })
            .tooltip(Tooltip.create(Component.literal("Read this pack's description and documentation.")))
            .bounds(x + panelWidth / 2 + 2, 74, (panelWidth - 16) / 2, 20).build());
        if (documentation) {
            for (String line : wrap(pack.description(), panelWidth - 32)) list.addLabel(Component.literal(line));
            for (BedrockPackSetting setting : pack.settings()) if (setting instanceof BedrockPackSetting.Label) addSetting(setting);
        } else {
		if (!pack.subpacks().isEmpty()) {
			BedrockSubpack initial = pack.subpacks().stream().filter(value -> value.folderName().equals(selectedSubpack))
				.findFirst().orElse(pack.subpacks().getLast());
			CycleButton<BedrockSubpack> button = CycleButton.builder(value -> Component.literal(value.name()), initial)
				.withValues(pack.subpacks()).create(Component.translatable("bedrock_compat.config.subpack"),
					(cycle, value) -> selectedSubpack = value.folderName());
			list.addControl(button);
		}

		for (BedrockPackSetting setting : pack.settings()) if (!(setting instanceof BedrockPackSetting.Label)) addSetting(setting);
		if (pack.subpacks().isEmpty() && pack.settings().isEmpty()) {
			list.addLabel(Component.translatable("bedrock_compat.config.none").withStyle(ChatFormatting.GRAY));
		}
        }
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> saveAndClose())
			.bounds(width / 2 - 75, height - 28, 150, 20).build());
	}

	private void addSetting(BedrockPackSetting setting) {
		if (setting instanceof BedrockPackSetting.Label label) {
			for (String line : wrap(label.text(), Math.max(1, panelWidth() - 32))) {
				list.addLabel(Component.literal(line).withStyle(ChatFormatting.GRAY));
			}
		} else if (setting instanceof BedrockPackSetting.Multiselect multi) {
            list.addLabel(Component.literal(label(multi.text(), multi.name())));
            Object stored = values.get(multi.name());
            List<?> selected = stored instanceof List<?> items ? items : multi.defaults();
            for (BedrockPackSetting.Option option : multi.options()) {
                list.addControl(CycleButton.onOffBuilder(selected.contains(option.name())).create(
                    Component.literal(label(option.text(), option.name())), (button, enabled) -> {
                        List<String> updated = new ArrayList<>();
                        Object current = values.get(multi.name());
                        List<?> existing = current instanceof List<?> items ? items : multi.defaults();
                        for (BedrockPackSetting.Option candidate : multi.options())
                            if (candidate.name().equals(option.name()) ? enabled : existing.contains(candidate.name())) updated.add(candidate.name());
                        values.put(multi.name(), List.copyOf(updated));
                    }));
            }
        } else if (setting instanceof BedrockPackSetting.Toggle toggle) {
			boolean initial = value(toggle.name(), Boolean.class, toggle.defaultValue());
			list.addControl(CycleButton.onOffBuilder(initial).create(Component.literal(label(toggle.text(), toggle.name())),
				(button, current) -> values.put(toggle.name(), current)));
		} else if (setting instanceof BedrockPackSetting.Slider slider) {
			double initial = value(slider.name(), Number.class, slider.defaultValue()).doubleValue();
			list.addControl(new SettingSlider(slider, initial));
		} else if (setting instanceof BedrockPackSetting.Dropdown dropdown && !dropdown.options().isEmpty()) {
			String selected = value(dropdown.name(), String.class, dropdown.defaultValue());
			BedrockPackSetting.Option initial = dropdown.options().stream().filter(option -> option.name().equals(selected))
				.findFirst().orElse(dropdown.options().getFirst());
			list.addControl(CycleButton.builder(option -> Component.literal(label(option.text(), option.name())), initial)
				.withValues(dropdown.options()).create(Component.literal(label(dropdown.text(), dropdown.name())),
					(button, current) -> values.put(dropdown.name(), current.name())));
		}
	}

	private <T> T value(String name, Class<T> type, T fallback) {
		Object value = values.get(name);
		return type.isInstance(value) ? type.cast(value) : fallback;
	}

	private void saveAndClose() {
		BedrockOptionsState.setConfiguration(packId,
			new BedrockPackConfiguration(Optional.ofNullable(selectedSubpack), values));
		minecraft.setScreenAndShow(parent);
	}

	@Override
	public void onClose() {
		saveAndClose();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int panelWidth = panelWidth();
		int x = (width - panelWidth) / 2;
		graphics.fill(x, 8, x + panelWidth, height - 32, 0xD0101010);
		graphics.fill(x, 8, x + panelWidth, 9, 0xFFFFFFFF);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int panelWidth = panelWidth();
		int x = (width - panelWidth) / 2 + 6;
		graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, 16, 0, 0, 48, 48, 48, 48);
		graphics.text(font, fit(pack.displayName(), panelWidth - 68), x + 56, 17, 0xFFFFFFFF);

		graphics.text(font, fit(pack.type().displayName() + " • v" + pack.version(), panelWidth - 68),
			x + 56, 45, 0xFF808080);
	}

	@Override
	public void removed() {
		if (ownsIcon) minecraft.getTextureManager().release(icon);
		ownsIcon = false;
	}

	private void loadIcon() {
		if (ownsIcon || pack.icon().isEmpty()) return;
		NativeImage image = null;
		DynamicTexture texture = null;
		try {
			image = NativeImage.read(pack.icon().orElseThrow());
			texture = new DynamicTexture(() -> pack.displayName() + " config", image);
			image = null;
			icon = Identifier.fromNamespaceAndPath(BedrockCompat.MOD_ID, "config_icon/" + Integer.toHexString(packId.hashCode()));
			minecraft.getTextureManager().register(icon, texture);
			ownsIcon = true;
		} catch (IOException | RuntimeException exception) {
			if (texture != null) texture.close();
			else if (image != null) image.close();
			BedrockCompat.LOGGER.warn("Could not decode configuration icon for {}", pack.displayName(), exception);
		}
	}

	private int panelWidth() {
		return Math.max(1, width - PANEL_PADDING * 2);
	}

	private String fit(String value, int availableWidth) {
		if (font.width(value) <= availableWidth) return value;
		return font.plainSubstrByWidth(value, Math.max(0, availableWidth - font.width("…"))) + "…";
	}

	private List<String> wrap(String value, int availableWidth) {
		availableWidth = Math.max(1, availableWidth);
		List<String> lines = new ArrayList<>();
		for (String paragraph : value.split("\\R", -1)) {
			String remaining = paragraph.trim();
			if (remaining.isEmpty()) {
				lines.add("");
				continue;
			}
			while (font.width(remaining) > availableWidth) {
				String head = font.plainSubstrByWidth(remaining, availableWidth);
				int split = head.lastIndexOf(' ');
				if (split > 0) head = head.substring(0, split);
				if (head.isEmpty()) head = remaining.substring(0, 1);
				lines.add(head);
				remaining = remaining.substring(head.length()).stripLeading();
			}
			if (!remaining.isEmpty()) lines.add(remaining);
		}
		return lines;
	}

	private static String label(String text, String fallback) {
		return text == null || text.isBlank() ? fallback : text;
	}

	private final class SettingSlider extends AbstractSliderButton {
		private final BedrockPackSetting.Slider setting;

		private SettingSlider(BedrockPackSetting.Slider setting, double current) {
			super(0, 0, 100, 20, Component.empty(), normalized(setting, current));
			this.setting = setting;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			double actual = actualValue();
			setMessage(Component.literal(label(setting.text(), setting.name()) + ": " + format(actual)));
		}

		@Override
		protected void applyValue() {
			double actual = actualValue();
			values.put(setting.name(), actual);
			updateMessage();
		}

		private double actualValue() {
			double range = setting.maximum() - setting.minimum();
			double raw = setting.minimum() + value * range;
			if (setting.step() > 0) raw = setting.minimum() + Math.round((raw - setting.minimum()) / setting.step()) * setting.step();
			return Mth.clamp(raw, setting.minimum(), setting.maximum());
		}

		private static double normalized(BedrockPackSetting.Slider setting, double current) {
			double range = setting.maximum() - setting.minimum();
			return range <= 0 ? 0 : Mth.clamp((current - setting.minimum()) / range, 0, 1);
		}

		private static String format(double value) {
			return value == Math.rint(value) ? Long.toString(Math.round(value)) : String.format(java.util.Locale.ROOT, "%.2f", value);
		}
	}

	private static final class ConfigList extends ContainerObjectSelectionList<ConfigEntry> {
		private ConfigList(Minecraft minecraft, int width, int height) {
			super(minecraft, width, height, 76, 24);
			centerListVertically = false;
		}

		private void addControl(AbstractWidget widget) {
			addEntry(new ConfigEntry(widget));
		}

		private void addLabel(Component text) {
			addEntry(new ConfigEntry(new StringWidget(text, minecraft.font)));
		}

		@Override
		public int getRowWidth() {
			return getWidth() - 8;
		}
	}

	private static final class ConfigEntry extends ContainerObjectSelectionList.Entry<ConfigEntry> {
		private final AbstractWidget widget;
		private Component tooltipMessage;
		private Tooltip tooltip;

		private ConfigEntry(AbstractWidget widget) {
			this.widget = widget;
			updateTooltip();
		}

		private void updateTooltip() {
			if (!widget.getMessage().equals(tooltipMessage)) {
				tooltipMessage = widget.getMessage().copy();
				tooltip = Tooltip.create(tooltipMessage);
			}
			// Cycle buttons can clear their tooltip when their selected value changes.
			widget.setTooltip(tooltip);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			widget.setRectangle(getContentWidth(), 20, getContentX(), getContentY() + 2);
			updateTooltip();
			widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(widget);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(widget);
		}
	}
}
