package dev.matanyah.bedrockcompat.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

final class AddonErrorsScreen extends Screen {
	private final Screen parent;
	private final List<AddonDiagnostic> diagnostics;
	private ErrorList list;

	AddonErrorsScreen(Screen parent, List<AddonDiagnostic> diagnostics) {
		super(Component.translatable("bedrock_compat.errors.title"));
		this.parent = parent;
		this.diagnostics = List.copyOf(diagnostics);
	}

	@Override
	protected void init() {
		list = addRenderableWidget(new ErrorList(minecraft, width, height - 68, 32, 48));
		list.setDiagnostics(diagnostics, font);
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
			.bounds(width / 2 - 75, height - 28, 150, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}

	private static final class ErrorList extends ObjectSelectionList<ErrorEntry> {
		private ErrorList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
			super(minecraft, width, height, y, itemHeight);
			centerListVertically = false;
		}

		private void setDiagnostics(List<AddonDiagnostic> diagnostics, Font font) {
			clearEntries();
			for (AddonDiagnostic diagnostic : diagnostics) addEntry(new ErrorEntry(font, diagnostic));
		}

		@Override
		public int getRowWidth() {
			return Math.min(600, getWidth() - 32);
		}
	}

	private static final class ErrorEntry extends ObjectSelectionList.Entry<ErrorEntry> {
		private final Font font;
		private final AddonDiagnostic diagnostic;

		private ErrorEntry(Font font, AddonDiagnostic diagnostic) {
			this.font = font;
			this.diagnostic = diagnostic;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			int x = getContentX() + 4;
			int y = getContentY() + 3;
			int availableWidth = getContentWidth() - 8;
			graphics.text(font, fit(diagnostic.packName(), availableWidth), x, y, 0xFFFF5555);
			graphics.text(font, fit(diagnostic.message(), availableWidth), x, y + 13, 0xFFFFFFFF);
			graphics.text(font, fit(diagnostic.source(), availableWidth), x, y + 26, 0xFFAAAAAA);
		}

		private String fit(String value, int width) {
			if (font.width(value) <= width) return value;
			return font.plainSubstrByWidth(value, Math.max(0, width - font.width("…"))) + "…";
		}

		@Override
		public Component getNarration() {
			return Component.literal(diagnostic.packName() + ", " + diagnostic.message() + ", " + diagnostic.source());
		}
	}
}
