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
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
	private ChoiceList choices;

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
		choices=null;
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
            list.addControl(Component.translatable("bedrock_compat.config.subpack"),choiceButton(pack.subpacks().stream().map(s->new BedrockPackSetting.Option(s.folderName(),s.name())).toList(),
                initial.folderName(),value->selectedSubpack=value));
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
                list.addControl(Component.literal(label(option.text(),option.name())),Checkbox.builder(Component.empty(),font).selected(selected.contains(option.name())).onValueChange((button, enabled) -> {
                        List<String> updated = new ArrayList<>();
                        Object current = values.get(multi.name());
                        List<?> existing = current instanceof List<?> items ? items : multi.defaults();
                        for (BedrockPackSetting.Option candidate : multi.options())
                            if (candidate.name().equals(option.name()) ? enabled : existing.contains(candidate.name())) updated.add(candidate.name());
                        values.put(multi.name(), List.copyOf(updated));
                    }).build());
            }
        } else if (setting instanceof BedrockPackSetting.Toggle toggle) {
			boolean initial = value(toggle.name(), Boolean.class, toggle.defaultValue());
            list.addControl(Component.literal(label(toggle.text(), toggle.name())),Checkbox.builder(Component.empty(),font).selected(initial)
                .onValueChange((button,current)->values.put(toggle.name(),current)).build());
		} else if (setting instanceof BedrockPackSetting.Slider slider) {
			double initial = value(slider.name(), Number.class, slider.defaultValue()).doubleValue();
			list.addControl(Component.literal(label(slider.text(),slider.name())),new SettingSlider(slider, initial));
		} else if (setting instanceof BedrockPackSetting.Dropdown dropdown && !dropdown.options().isEmpty()) {
			String selected = value(dropdown.name(), String.class, dropdown.defaultValue());
            list.addControl(Component.literal(label(dropdown.text(),dropdown.name())),choiceButton(dropdown.options(),selected,current->values.put(dropdown.name(),current)));
		}
	}

    private Button choiceButton(List<BedrockPackSetting.Option> options,String initial,java.util.function.Consumer<String> changed) {
        var selected=options.stream().filter(o->o.name().equals(initial)).findFirst().orElse(options.getFirst());
        String[] current={selected.name()};
        return Button.builder(Component.literal(label(selected.text(),selected.name())+" ▾"),button->{
            closeChoices();
            int h=Math.min(120,options.size()*24+4);
            int y=button.getBottom()+2;
            if(y+h>height-32)y=Math.max(100,button.getY()-h-2);
            choices=addRenderableWidget(new ChoiceList(button.getX(),y,button.getWidth(),h,options,current[0],
                option->{current[0]=option.name();changed.accept(option.name());button.setMessage(Component.literal(label(option.text(),option.name())+" ▾"));closeChoices();setFocused(button);}));
            setFocused(choices);
        }).build();
    }
    private void closeChoices(){if(choices!=null){removeWidget(choices);choices=null;}}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        return choices!=null?choices.mouseScrolled(x,y,horizontal,vertical):super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        return choices!=null?choices.mouseReleased(event):super.mouseReleased(event);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double x,double y) {
        return choices!=null?choices.mouseDragged(event,x,y):super.mouseDragged(event,x,y);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        if(choices!=null){
            if(choices.isMouseOver(event.x(),event.y()))return choices.mouseClicked(event,doubleClick);
            closeChoices();return true;
        }
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if(choices!=null){
            if(event.key()==256){closeChoices();setFocused(list);return true;}
            if(event.key()==264||event.key()==265){choices.moveSelection(event.key()==264?1:-1);return true;}
            if((event.key()==257||event.key()==335)&&choices.getSelected()!=null){choices.getSelected().choose();return true;}
            return choices.keyPressed(event);
        }
        return super.keyPressed(event);
    }
    private final class ChoiceList extends ObjectSelectionList<ChoiceEntry> {
        ChoiceList(int x,int y,int width,int height,List<BedrockPackSetting.Option> options,String selected,java.util.function.Consumer<BedrockPackSetting.Option> changed){
            super(BedrockPackConfigScreen.this.minecraft,width,height,y,24);centerListVertically=false;
            updateSizeAndPosition(width,height,x,y);
            for(var option:options)addEntry(new ChoiceEntry(option,changed));
            setSelected(children().stream().filter(e->e.option.name().equals(selected)).findFirst().orElse(children().getFirst()));
            scrollToEntry(getSelected());
        }
        @Override public int getRowWidth(){return getWidth()-12;}
        private void moveSelection(int delta){
            int index=Mth.clamp(children().indexOf(getSelected())+delta,0,children().size()-1);
            setSelected(children().get(index));scrollToEntry(getSelected());
        }
        @Override protected void extractListBackground(GuiGraphicsExtractor graphics){graphics.fill(getX(),getY(),getRight(),getBottom(),0xFF202020);}
    }
    private final class ChoiceEntry extends ObjectSelectionList.Entry<ChoiceEntry> {
        private final BedrockPackSetting.Option option;
        private final java.util.function.Consumer<BedrockPackSetting.Option> changed;
        ChoiceEntry(BedrockPackSetting.Option option,java.util.function.Consumer<BedrockPackSetting.Option> changed){this.option=option;this.changed=changed;}
        void choose(){changed.accept(option);}
        @Override public Component getNarration(){return Component.literal(label(option.text(),option.name()));}
        @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){if(event.button()!=0)return false;choose();return true;}
        @Override public void extractContent(GuiGraphicsExtractor graphics,int x,int y,boolean hovered,float partial){
            graphics.text(font,fit(label(option.text(),option.name()),getContentWidth()),getContentX(),getContentY()+6,0xFFFFFFFF);
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
			setMessage(Component.literal(format(actual)));
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
        private void addControl(Component title,AbstractWidget widget){addEntry(new ConfigEntry(widget,title,minecraft.font));}

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
        private final StringWidget labelWidget;
		private Component tooltipMessage;
		private Tooltip tooltip;

		private ConfigEntry(AbstractWidget widget) {
			this(widget,null,null);
		}
        private ConfigEntry(AbstractWidget widget,Component label,Font font) {
			this.widget = widget;
            labelWidget=label==null?null:new StringWidget(label,font);
            if(labelWidget!=null)labelWidget.setTooltip(Tooltip.create(label));
			updateTooltip();
		}

		private void updateTooltip() {
            Component message=labelWidget==null?widget.getMessage():labelWidget.getMessage().copy()
                .append(widget.getMessage().getString().isEmpty()?Component.empty():Component.literal(": ").append(widget.getMessage()));
			if (!message.equals(tooltipMessage)) {
				tooltipMessage = message.copy();
				tooltip = Tooltip.create(tooltipMessage);
			}
			// Keep the full label and current choice available when either is truncated.
			widget.setTooltip(tooltip);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int controlWidth=labelWidget==null?getContentWidth():widget instanceof Checkbox?20:Math.min(220,getContentWidth()/2);
			widget.setRectangle(controlWidth, 20, getContentX()+getContentWidth()-controlWidth, getContentY() + 2);
            if(labelWidget!=null){
                labelWidget.setRectangle(getContentWidth()-controlWidth-12,20,getContentX(),getContentY()+2);
                labelWidget.extractRenderState(graphics,mouseX,mouseY,partialTick);
            }
			updateTooltip();
			widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return labelWidget==null?List.of(widget):List.of(widget,labelWidget);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return labelWidget==null?List.of(widget):List.of(labelWidget,widget);
		}
	}
}
