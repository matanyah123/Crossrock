package dev.matanyah.eef.client;

import dev.matanyah.eef.EefConfig;
import dev.matanyah.eef.EefRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

/** EEF settings: static client toggles on the right and a scrollable trigger editor on the left. */
public final class EefConfigScreen extends Screen {
	private final Screen parent;
	private TriggerList triggerList;
	public EefConfigScreen(Screen parent) { super(Component.literal("Entity Event Features")); this.parent = parent; }

	@Override protected void init() {
		int rightX = Math.max(620, (int)(width * 0.63));
		int leftWidth = rightX - 28;
		triggerList = new TriggerList(minecraft, leftWidth, height - 72, 44, height - 20);
		for (EefRule rule : EntityEventFeaturesClient.CONFIG.orderedRules(EntityEventFeaturesClient.RULE_ENGINE.rules()))
			triggerList.addTrigger(new TriggerEntry(triggerList, rule));
		addRenderableWidget(triggerList);
		int rightWidth = Math.max(220, width - rightX - 28);
		addRenderableWidget(Button.builder(subtitleLabel(), pressed -> {
			boolean enabled = EntityEventFeaturesClient.CONFIG.toggleSubtitles();
			if (!enabled) EefSubtitleOverlay.clear(); pressed.setMessage(subtitleLabel());
		}).bounds(rightX, 62, rightWidth, 22).build());
		addRenderableWidget(Button.builder(bubbleLabel(), pressed -> {
			boolean enabled = EntityEventFeaturesClient.CONFIG.toggleSpeechBubbles();
			if (!enabled) EefSpeechBubbleOverlay.clear(); pressed.setMessage(bubbleLabel());
		}).bounds(rightX, 92, rightWidth, 22).build());
		addRenderableWidget(new GraceSlider(rightX, 130, rightWidth));
		addRenderableWidget(Button.builder(chattinessLabel(), pressed -> {
			EntityEventFeaturesClient.CONFIG.cycleChattiness();
			EntityEventFeaturesClient.RULE_ENGINE.setCooldownMultiplier(EntityEventFeaturesClient.CONFIG.cooldownMultiplier());
			pressed.setMessage(chattinessLabel());
		}).bounds(rightX, 166, rightWidth, 22).build());
		addRenderableWidget(Button.builder(rareLinesLabel(), pressed -> {
			EntityEventFeaturesClient.CONFIG.cycleRareLines(); pressed.setMessage(rareLinesLabel());
		}).bounds(rightX, 196, rightWidth, 22).build());
	}

	@Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(font, Component.literal("Trigger rules"), 18, 24, 0xFFFFFFFF);
		graphics.text(font, Component.literal("Drag rows to reorder  •  Each trigger has its own frequency"), 18, 35, 0xFFB0B0B0);
		graphics.text(font, Component.literal("General settings"), Math.max(620, (int)(width * 0.63)), 34, 0xFFFFFFFF);
	}

	private static Component subtitleLabel() { return Component.literal("EEF Subtitles: " + (EntityEventFeaturesClient.CONFIG.subtitlesEnabled() ? "ON" : "OFF")); }
	private static Component bubbleLabel() { return Component.literal("EEF Speech Bubbles: " + (EntityEventFeaturesClient.CONFIG.speechBubblesEnabled() ? "ON" : "OFF")); }
	private static Component chattinessLabel() { return Component.literal("Villager Chattiness: " + switch (EntityEventFeaturesClient.CONFIG.chattiness()) {
		case MUTED -> "Muted"; case SHY -> "Shy"; case CHATTY -> "Chatty (Default)"; case SUPER_CHATTY -> "Super Chatty"; }); }
	private static Component rareLinesLabel() { return Component.literal("Rare Villager Lines: " + switch (EntityEventFeaturesClient.CONFIG.rareLines()) {
		case NEVER -> "Never"; case DEFAULT -> "Default"; case OFTEN -> "Often"; }); }

	private static final class GraceSlider extends AbstractSliderButton {
		private GraceSlider(int x, int y, int width) { super(x, y, width, 22, Component.empty(), normalized(EntityEventFeaturesClient.CONFIG.bubbleGraceDistance())); updateMessage(); }
		@Override protected void updateMessage() { setMessage(Component.literal("Off-screen bubble range: " + Math.round(actual()) + " blocks")); }
		@Override protected void applyValue() { EntityEventFeaturesClient.CONFIG.setBubbleGraceDistance(actual()); updateMessage(); }
		private double actual() { return Math.round(value * EefConfig.MAX_BUBBLE_GRACE_DISTANCE); }
		private static double normalized(double distance) { return Mth.clamp(distance / EefConfig.MAX_BUBBLE_GRACE_DISTANCE, 0.0, 1.0); }
	}

	private static final class TriggerList extends ObjectSelectionList<TriggerEntry> {
		private TriggerList(Minecraft minecraft, int width, int height, int top, int bottom) { super(minecraft, width, height, top, bottom); }
		private void addTrigger(TriggerEntry entry) { addEntry(entry); }
	}

	private static final class TriggerEntry extends ObjectSelectionList.Entry<TriggerEntry> {
		private final TriggerList owner;
		private final EefRule rule;
		private final Button enabledButton;
		private final Button importantButton;
		private final FrequencySlider frequencySlider;
		private boolean dragging;

		private TriggerEntry(TriggerList owner, EefRule rule) {
			this.owner = owner; this.rule = rule;
			enabledButton = Button.builder(Component.empty(), button -> {
				boolean enabled = EntityEventFeaturesClient.CONFIG.toggleTrigger(rule.id());
				button.setMessage(Component.literal(enabled ? "ON" : "OFF"));
			}).bounds(0, 0, 42, 20).build();
			importantButton = Button.builder(Component.empty(), button -> {
				boolean important = EntityEventFeaturesClient.CONFIG.toggleTriggerImportance(rule.id());
				button.setMessage(Component.literal(important ? "Important" : "Skip if busy"));
			}).bounds(0, 0, 92, 20).build();
			frequencySlider = new FrequencySlider(rule);
			refreshLabels();
		}
		private void refreshLabels() {
			enabledButton.setMessage(Component.literal(EntityEventFeaturesClient.CONFIG.triggerEnabled(rule) ? "ON" : "OFF"));
			importantButton.setMessage(Component.literal(EntityEventFeaturesClient.CONFIG.triggerImportant(rule) ? "Important" : "Skip if busy"));
		}
		private void layout() {
			int y = getContentY(); int x = getContentX();
			enabledButton.setX(x); enabledButton.setY(y + 20);
			frequencySlider.setX(x + 48); frequencySlider.setY(y + 20);
			importantButton.setX(x + 214); importantButton.setY(y + 20);
		}
		@Override public int getHeight() { return 44; }
		@Override public Component getNarration() { return Component.literal(displayName(rule)); }
		@Override public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
			layout();
			String title = trim(displayName(rule), 330);
			String event = rule.event().name().toLowerCase(Locale.ROOT).replace('_', ' ');
			graphics.text(Minecraft.getInstance().font, Component.literal(title), getContentX() + 48, getContentY() + 2, 0xFFFFFFFF);
			graphics.text(Minecraft.getInstance().font, Component.literal(event), getContentX() + 48, getContentY() + 13, 0xFF9E9E9E);
			enabledButton.extractRenderState(graphics, mouseX, mouseY, delta);
			frequencySlider.extractRenderState(graphics, mouseX, mouseY, delta);
			importantButton.extractRenderState(graphics, mouseX, mouseY, delta);
		}
		@Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			layout();
			if (enabledButton.mouseClicked(event, doubleClick) || frequencySlider.mouseClicked(event, doubleClick) || importantButton.mouseClicked(event, doubleClick)) return true;
			dragging = event.button() == 0; return dragging;
		}
		@Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
			if (frequencySlider.mouseDragged(event, dx, dy)) return true;
			if (!dragging || event.button() != 0) return false;
			int target = (int)Math.floor((event.y() - owner.getRowTop(0) + owner.scrollAmount()) / 44.0);
			if (target >= 0 && target < owner.children().size()) {
				TriggerEntry other = owner.children().get(target);
				if (other != this) EntityEventFeaturesClient.CONFIG.moveTriggerBefore(rule.id(), other.rule.id(), owner.children().stream().map(entry -> entry.rule).toList());
			}
			return true;
		}
		@Override public boolean mouseReleased(MouseButtonEvent event) { dragging = false; return frequencySlider.mouseReleased(event); }
		private static String displayName(EefRule rule) {
			String id = rule.id().replaceFirst("^(activity|trade|block|mob)_", "").replace('_', ' ');
			return id.isEmpty() ? rule.id() : Character.toUpperCase(id.charAt(0)) + id.substring(1) + "  [" + rule.event().name().toLowerCase(Locale.ROOT) + "]";
		}
		private static String trim(String value, int maxPixels) {
			var font = Minecraft.getInstance().font;
			if (font.width(value) <= maxPixels) return value;
			String suffix = "…";
			while (value.length() > 1 && font.width(value + suffix) > maxPixels) value = value.substring(0, value.length() - 1);
			return value + suffix;
		}
	}

	private static final class FrequencySlider extends AbstractSliderButton {
		private final EefRule rule;
		private FrequencySlider(EefRule rule) { super(0, 0, 160, 20, Component.empty(), normalized(EntityEventFeaturesClient.CONFIG.frequency(rule))); this.rule = rule; updateMessage(); }
		@Override protected void updateMessage() { setMessage(Component.literal("Every " + actual() + " time" + (actual() == 1 ? "" : "s"))); }
		@Override protected void applyValue() { EntityEventFeaturesClient.CONFIG.setTriggerFrequency(rule.id(), actual()); updateMessage(); }
		private int actual() { return 1 + (int)Math.round(value * 9.0); }
		private static double normalized(int frequency) { return Mth.clamp((frequency - 1) / 9.0, 0.0, 1.0); }
	}

	@Override public void onClose() { Minecraft.getInstance().setScreenAndShow(parent); }
}
