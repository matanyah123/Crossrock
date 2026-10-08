package dev.matanyah.bedrockcompat.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.matanyah.bedrockcompat.BedrockCompat;
import dev.matanyah.bedrockcompat.bedrock.discovery.BedrockPackRepository;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPackIdentity;
import dev.matanyah.bedrockcompat.bedrock.model.PackLoadResult;
import dev.matanyah.bedrockcompat.client.BedrockOptionsState;
import dev.matanyah.bedrockcompat.client.discovery.AddonDirectoryWatcher;
import dev.matanyah.bedrockcompat.client.resource.VirtualCompatibilityPackRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class AddonsScreen extends Screen {
	private static final int LIST_WIDTH = 200;
	private static final int HEADER_HEIGHT = 66;
	private static final int FOOTER_HEIGHT = 33;
	private static final int ENTRY_HEIGHT = 36;
	private static final int RELOAD_DELAY_TICKS = 20;
	private static final Identifier DEFAULT_ICON = Identifier.withDefaultNamespace("textures/misc/unknown_pack.png");

	private final Screen parent;
	private final BedrockPackRepository repository;
	private final List<String> selectedIds = new ArrayList<>();
	private final List<Identifier> textureIds = new ArrayList<>();
	private final Map<String, Identifier> texturesByPack = new HashMap<>();
	private final List<AddonDiagnostic> transientDiagnostics = new ArrayList<>();
	private List<PackLoadResult> results = List.of();
	private List<AddonDiagnostic> diagnostics = List.of();
	private AddonList availableList;
	private AddonList selectedList;
	private EditBox search;
	private Button errorsButton;
	private AddonDirectoryWatcher watcher;
	private int reloadCountdown;

	public AddonsScreen(Screen parent, BedrockPackRepository repository) {
		super(Component.translatable("bedrock_compat.screen.title"));
		this.parent = parent;
		this.repository = repository;
		selectedIds.addAll(BedrockOptionsState.selectedIds());
	}

	@Override
	protected void init() {
		int contentHeight = Math.max(40, height - HEADER_HEIGHT - FOOTER_HEIGHT);
		availableList = addRenderableWidget(new AddonList(minecraft, this, false,
			Component.translatable("pack.available.title")));
		selectedList = addRenderableWidget(new AddonList(minecraft, this, true,
			Component.translatable("pack.selected.title")));
		availableList.updateSizeAndPosition(LIST_WIDTH, contentHeight, width / 2 - 15 - LIST_WIDTH, HEADER_HEIGHT);
		selectedList.updateSizeAndPosition(LIST_WIDTH, contentHeight, width / 2 + 15, HEADER_HEIGHT);

		search = addRenderableWidget(new EditBox(font, width / 2 - 100, 43, 200, 15, Component.empty()));
		search.setHint(Component.translatable("gui.packSelection.search").withStyle(EditBox.SEARCH_HINT_STYLE));
		search.setResponder(ignored -> rebuildLists());

		errorsButton = addRenderableWidget(Button.builder(Component.empty(), button ->
			minecraft.setScreenAndShow(new AddonErrorsScreen(this, diagnostics)))
			.bounds(width - 106, 6, 100, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("pack.openFolder"), button -> openFolder())
			.tooltip(Tooltip.create(Component.translatable("pack.folderInfo")))
			.bounds(width / 2 - 154, height - 28, 150, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
			.bounds(width / 2 + 4, height - 28, 150, 20).build());

		openWatcher();
		refresh();
	}

	@Override
	public void tick() {
		if (watcher != null) {
			try {
				if (watcher.pollForChanges()) reloadCountdown = RELOAD_DELAY_TICKS;
			} catch (IOException | RuntimeException exception) {
				BedrockCompat.LOGGER.warn("Stopped watching the add-ons directory", exception);
				transientDiagnostics.add(new AddonDiagnostic("Add-ons folder", message(exception), repository.addonsDirectory().toString()));
				closeWatcher();
			}
		}
		if (reloadCountdown > 0 && --reloadCountdown == 0) refresh();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
		graphics.centeredText(font, Component.translatable("pack.dropInfo").withStyle(ChatFormatting.GRAY),
			width / 2, 24, 0xFFFFFFFF);
	}

	@Override
	public void onFilesDrop(List<Path> paths) {
		minecraft.setScreenAndShow(new ConfirmScreen(confirmed -> {
			if (confirmed) copyDroppedPacks(paths);
			minecraft.setScreenAndShow(this);
		}, Component.translatable("bedrock_compat.drop.title"),
			Component.translatable("bedrock_compat.drop.message", paths.size())));
	}

	@Override
	public void onClose() {
		BedrockOptionsState.setSelectedIds(selectedIds);
		VirtualCompatibilityPackRuntime.applyPendingChanges();
		minecraft.setScreenAndShow(parent);
	}

	@Override
	public void removed() {
		closeWatcher();
		releaseTextures();
	}

	private void openWatcher() {
		if (watcher != null) return;
		try {
			watcher = new AddonDirectoryWatcher(repository.addonsDirectory());
		} catch (IOException | RuntimeException exception) {
			BedrockCompat.LOGGER.warn("Could not watch the add-ons directory", exception);
			transientDiagnostics.add(new AddonDiagnostic("Add-ons folder", message(exception), repository.addonsDirectory().toString()));
		}
	}

	private void closeWatcher() {
		if (watcher == null) return;
		try {
			watcher.close();
		} catch (IOException exception) {
			BedrockCompat.LOGGER.warn("Could not close the add-ons directory watcher", exception);
		} finally {
			watcher = null;
		}
	}

	private void refresh() {
		releaseTextures();
		results = repository.scan();
		Set<String> discoveredIds = new LinkedHashSet<>();
		for (PackLoadResult result : results) {
			if (result.pack().isPresent()) discoveredIds.add(selectionId(result));
		}
		if (selectedIds.removeIf(id -> !discoveredIds.contains(id))) BedrockOptionsState.setSelectedIds(selectedIds);
		List<AddonDiagnostic> foundDiagnostics = new ArrayList<>(transientDiagnostics);
		int textureIndex = 0;
		for (PackLoadResult result : results) {
			if (result.pack().isEmpty()) {
				foundDiagnostics.add(new AddonDiagnostic(result.displayName(), result.error().orElse("Unknown parsing error"), source(result)));
				continue;
			}
			BedrockPack pack = result.pack().get();
			for (String warning : pack.warnings()) foundDiagnostics.add(new AddonDiagnostic(pack.displayName(), warning, source(result)));
			loadTexture(result, textureIndex++, foundDiagnostics).ifPresent(texture -> texturesByPack.put(selectionId(result), texture));
		}
		VirtualCompatibilityPackRuntime.sourceFilesChanged();
		for (String error : VirtualCompatibilityPackRuntime.generationErrors()) {
			foundDiagnostics.add(new AddonDiagnostic("Resource translation", error, repository.addonsDirectory().toString()));
		}
		diagnostics = List.copyOf(foundDiagnostics);
		errorsButton.visible = !diagnostics.isEmpty();
		errorsButton.active = !diagnostics.isEmpty();
		errorsButton.setMessage(Component.translatable("bedrock_compat.errors", diagnostics.size()));
		rebuildLists();
	}

	private void rebuildLists() {
		if (availableList == null || selectedList == null) return;
		String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT);
		Map<String, PackLoadResult> loadedById = new HashMap<>();
		for (PackLoadResult result : results) {
			if (result.pack().isPresent()) loadedById.putIfAbsent(selectionId(result), result);
		}

		List<PackLoadResult> selected = new ArrayList<>();
		for (String id : selectedIds) {
			PackLoadResult result = loadedById.get(id);
			if (result != null && matches(result, query)) selected.add(result);
		}
		Set<String> selectedSet = new LinkedHashSet<>(selectedIds);
		List<PackLoadResult> available = results.stream()
			.filter(result -> result.pack().isPresent())
			.filter(result -> !selectedSet.contains(selectionId(result)))
			.filter(result -> matches(result, query))
			.toList();
		availableList.setPacks(available);
		selectedList.setPacks(selected);
	}

	private boolean matches(PackLoadResult result, String query) {
		if (query.isBlank()) return true;
		BedrockPack pack = result.pack().orElseThrow();
		return pack.displayName().toLowerCase(Locale.ROOT).contains(query)
			|| pack.description().toLowerCase(Locale.ROOT).contains(query)
			|| pack.type().displayName().toLowerCase(Locale.ROOT).contains(query)
			|| result.source().toString().toLowerCase(Locale.ROOT).contains(query);
	}

	private void select(PackLoadResult result) {
		String id = selectionId(result);
		selectedIds.remove(id);
		selectedIds.addFirst(id);
		selectionChanged();
	}

	private void unselect(PackLoadResult result) {
		selectedIds.remove(selectionId(result));
		selectionChanged();
	}

	private void openConfiguration(PackLoadResult result) {
		minecraft.setScreenAndShow(new BedrockPackConfigScreen(this, selectionId(result), result.pack().orElseThrow()));
	}

	private void move(PackLoadResult result, int offset) {
		String id = selectionId(result);
		int from = selectedIds.indexOf(id);
		int to = from + offset;
		if (from < 0 || to < 0 || to >= selectedIds.size()) return;
		selectedIds.remove(from);
		selectedIds.add(to, id);
		selectionChanged();
	}

	private void selectionChanged() {
		BedrockOptionsState.setSelectedIds(selectedIds);
		rebuildLists();
	}

	private boolean canMove(PackLoadResult result, int offset) {
		int index = selectedIds.indexOf(selectionId(result));
		return index >= 0 && index + offset >= 0 && index + offset < selectedIds.size();
	}

	private Optional<Identifier> loadTexture(PackLoadResult result, int index, List<AddonDiagnostic> foundDiagnostics) {
		Optional<byte[]> icon = result.pack().flatMap(BedrockPack::icon);
		if (icon.isEmpty()) return Optional.empty();
		Identifier id = Identifier.fromNamespaceAndPath(BedrockCompat.MOD_ID, "pack_icon/" + index);
		NativeImage image = null;
		DynamicTexture texture = null;
		try {
			image = NativeImage.read(icon.get());
			texture = new DynamicTexture(() -> result.displayName(), image);
			image = null;
			minecraft.getTextureManager().register(id, texture);
			textureIds.add(id);
			return Optional.of(id);
		} catch (IOException | RuntimeException exception) {
			if (texture != null) texture.close();
			else if (image != null) image.close();
			BedrockCompat.LOGGER.warn("Could not decode pack icon for {}", result.source(), exception);
			foundDiagnostics.add(new AddonDiagnostic(result.displayName(), "Could not decode pack icon: " + message(exception), source(result)));
			return Optional.empty();
		}
	}

	private void releaseTextures() {
		for (Identifier id : textureIds) minecraft.getTextureManager().release(id);
		textureIds.clear();
		texturesByPack.clear();
	}

	private void openFolder() {
		try {
			Files.createDirectories(repository.addonsDirectory());
			Util.getPlatform().openPath(repository.addonsDirectory());
		} catch (IOException | SecurityException exception) {
			BedrockCompat.LOGGER.error("Could not open add-ons folder {}", repository.addonsDirectory(), exception);
			transientDiagnostics.add(new AddonDiagnostic("Add-ons folder", message(exception), repository.addonsDirectory().toString()));
			refresh();
		}
	}

	private void copyDroppedPacks(List<Path> sources) {
		transientDiagnostics.clear();
		for (Path source : sources) {
			try {
				Path target = repository.addonsDirectory().resolve(source.getFileName().toString());
				if (Files.exists(target)) throw new IOException("A file or folder with this name already exists");
				if (Files.isDirectory(source)) copyDirectory(source, target);
				else Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
			} catch (IOException | RuntimeException exception) {
				BedrockCompat.LOGGER.warn("Could not copy dropped add-on {}", source, exception);
				transientDiagnostics.add(new AddonDiagnostic(source.getFileName().toString(), message(exception), source.toString()));
			}
		}
		refresh();
	}

	private static void copyDirectory(Path source, Path target) throws IOException {
		try (Stream<Path> paths = Files.walk(source)) {
			for (Path path : paths.toList()) {
				Path destination = target.resolve(source.relativize(path).toString());
				if (Files.isDirectory(path)) Files.createDirectories(destination);
				else if (Files.isRegularFile(path)) Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES);
			}
		}
	}

	private String selectionId(PackLoadResult result) {
		BedrockPack pack = result.pack().orElseThrow();
		return BedrockPackIdentity.selectionId(pack);
	}

	private static String source(PackLoadResult result) {
		return result.source() + (result.locationInSource().isBlank() ? "" : "!/" + result.locationInSource());
	}

	private static String message(Exception exception) {
		return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
	}

	private static final class AddonList extends ObjectSelectionList<AddonListEntry> {
		private final AddonsScreen screen;
		private final boolean selected;
		private final Component title;

		private AddonList(Minecraft minecraft, AddonsScreen screen, boolean selected, Component title) {
			super(minecraft, LIST_WIDTH, 100, HEADER_HEIGHT, ENTRY_HEIGHT);
			this.screen = screen;
			this.selected = selected;
			this.title = title;
			centerListVertically = false;
		}

		private void setPacks(List<PackLoadResult> packs) {
			double scroll = scrollAmount();
			clearEntries();
			addEntry(new HeaderEntry(screen.font, title.copy().withStyle(ChatFormatting.UNDERLINE, ChatFormatting.BOLD)), 13);
			for (PackLoadResult pack : packs) addEntry(new PackEntry(screen, pack, selected));
			setScrollAmount(scroll);
		}

		@Override
		public int getRowWidth() {
			return getWidth() - 4;
		}

		@Override
		protected int scrollBarX() {
			return getRight() - scrollbarWidth();
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (getSelected() != null && getSelected().keyPressed(event)) return true;
			return super.keyPressed(event);
		}
	}

	private abstract static class AddonListEntry extends ObjectSelectionList.Entry<AddonListEntry> {
	}

	private static final class HeaderEntry extends AddonListEntry {
		private final Font font;
		private final Component text;

		private HeaderEntry(Font font, Component text) {
			this.font = font;
			this.text = text;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			graphics.centeredText(font, text, getX() + getWidth() / 2, getContentYMiddle() - font.lineHeight / 2, 0xFFFFFFFF);
		}

		@Override
		public Component getNarration() {
			return text;
		}
	}

	private static final class PackEntry extends AddonListEntry {
		private static final int ICON_SIZE = 32;
		private static final Identifier SELECT = Identifier.withDefaultNamespace("transferable_list/select");
		private static final Identifier SELECT_HIGHLIGHTED = Identifier.withDefaultNamespace("transferable_list/select_highlighted");
		private static final Identifier UNSELECT = Identifier.withDefaultNamespace("transferable_list/unselect");
		private static final Identifier UNSELECT_HIGHLIGHTED = Identifier.withDefaultNamespace("transferable_list/unselect_highlighted");
		private static final Identifier MOVE_UP = Identifier.withDefaultNamespace("transferable_list/move_up");
		private static final Identifier MOVE_UP_HIGHLIGHTED = Identifier.withDefaultNamespace("transferable_list/move_up_highlighted");
		private static final Identifier MOVE_DOWN = Identifier.withDefaultNamespace("transferable_list/move_down");
		private static final Identifier MOVE_DOWN_HIGHLIGHTED = Identifier.withDefaultNamespace("transferable_list/move_down_highlighted");
		private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
		private static final Identifier BUTTON_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/button_highlighted");
		private static final int CONFIG_WIDTH = 42;

		private final AddonsScreen screen;
		private final PackLoadResult result;
		private final boolean selected;

		private PackEntry(AddonsScreen screen, PackLoadResult result, boolean selected) {
			this.screen = screen;
			this.result = result;
			this.selected = selected;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			int x = getContentX();
			int y = getContentY();
			Identifier icon = screen.texturesByPack.getOrDefault(screen.selectionId(result), DEFAULT_ICON);
			graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

			int relativeX = mouseX - x;
			int relativeY = mouseY - y;
			if (hovered) {
				graphics.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, 0xA0000000);
				extractActions(graphics, x, y, relativeX, relativeY);
			}

			BedrockPack pack = result.pack().orElseThrow();
			int textX = x + ICON_SIZE + 2;
			boolean configurable = selected && (!pack.subpacks().isEmpty() || !pack.settings().isEmpty());
			int textWidth = Math.max(20, getContentWidth() - ICON_SIZE - 4 - (configurable ? CONFIG_WIDTH + 2 : 0));
			graphics.text(screen.font, fit(pack.displayName(), textWidth), textX, y + 1, 0xFFFFFFFF);
			String description = pack.description().isBlank() ? "No description" : pack.description();
			graphics.text(screen.font, fit(description, textWidth), textX, y + 12, 0xFF808080);
			graphics.text(screen.font, fit(pack.type().displayName() + " • v" + pack.version(), textWidth), textX, y + 22, 0xFF808080);
			if (configurable) {
				int buttonX = x + getContentWidth() - CONFIG_WIDTH;
				boolean overButton = mouseX >= buttonX && mouseX < buttonX + CONFIG_WIDTH && mouseY >= y + 6 && mouseY < y + 26;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, overButton ? BUTTON_HIGHLIGHTED : BUTTON,
					buttonX, y + 6, CONFIG_WIDTH, 20);
				graphics.centeredText(screen.font, Component.translatable("bedrock_compat.config.button"),
					buttonX + CONFIG_WIDTH / 2, y + 12, 0xFFFFFFFF);
				if (overButton) graphics.requestCursor(CursorTypes.POINTING_HAND);
			}
		}

		private void extractActions(GuiGraphicsExtractor graphics, int x, int y, int relativeX, int relativeY) {
			if (!selected) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, insideIcon(relativeX, relativeY) ? SELECT_HIGHLIGHTED : SELECT,
					x, y, ICON_SIZE, ICON_SIZE);
				if (insideIcon(relativeX, relativeY)) graphics.requestCursor(CursorTypes.POINTING_HAND);
				return;
			}
			boolean overUnselect = relativeX >= 0 && relativeX < 16 && relativeY >= 0 && relativeY < ICON_SIZE;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, overUnselect ? UNSELECT_HIGHLIGHTED : UNSELECT,
				x, y, ICON_SIZE, ICON_SIZE);
			if (screen.canMove(result, -1)) {
				boolean overUp = relativeX >= 16 && relativeX < 32 && relativeY >= 0 && relativeY < 16;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, overUp ? MOVE_UP_HIGHLIGHTED : MOVE_UP,
					x, y, ICON_SIZE, ICON_SIZE);
				if (overUp) graphics.requestCursor(CursorTypes.POINTING_HAND);
			}
			if (screen.canMove(result, 1)) {
				boolean overDown = relativeX >= 16 && relativeX < 32 && relativeY >= 16 && relativeY < 32;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, overDown ? MOVE_DOWN_HIGHLIGHTED : MOVE_DOWN,
					x, y, ICON_SIZE, ICON_SIZE);
				if (overDown) graphics.requestCursor(CursorTypes.POINTING_HAND);
			}
			if (overUnselect) graphics.requestCursor(CursorTypes.POINTING_HAND);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (event.button() != 0) return super.mouseClicked(event, doubleClick);
			int relativeX = (int) event.x() - getContentX();
			int relativeY = (int) event.y() - getContentY();
			BedrockPack pack = result.pack().orElseThrow();
			boolean configurable = selected && (!pack.subpacks().isEmpty() || !pack.settings().isEmpty());
			if (configurable && relativeX >= getContentWidth() - CONFIG_WIDTH && relativeY >= 6 && relativeY < 26) {
				screen.openConfiguration(result);
				return true;
			}
			if (!insideIcon(relativeX, relativeY)) return super.mouseClicked(event, doubleClick);
			if (!selected) screen.select(result);
			else if (relativeX < 16) screen.unselect(result);
			else if (relativeY < 16) screen.move(result, -1);
			else screen.move(result, 1);
			return true;
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (event.isConfirmation()) {
				if (selected) screen.unselect(result);
				else screen.select(result);
				return true;
			}
			if (selected && event.hasShiftDown() && event.isUp()) {
				screen.move(result, -1);
				return true;
			}
			if (selected && event.hasShiftDown() && event.isDown()) {
				screen.move(result, 1);
				return true;
			}
			return super.keyPressed(event);
		}

		private static boolean insideIcon(int x, int y) {
			return x >= 0 && x < ICON_SIZE && y >= 0 && y < ICON_SIZE;
		}

		private String fit(String value, int width) {
			if (screen.font.width(value) <= width) return value;
			return screen.font.plainSubstrByWidth(value, Math.max(0, width - screen.font.width("…"))) + "…";
		}

		@Override
		public Component getNarration() {
			BedrockPack pack = result.pack().orElseThrow();
			return Component.literal(pack.displayName() + ", " + pack.description() + ", "
				+ pack.type().displayName() + ", version " + pack.version());
		}
	}
}
