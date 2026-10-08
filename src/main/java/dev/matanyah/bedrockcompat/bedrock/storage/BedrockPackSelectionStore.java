package dev.matanyah.bedrockcompat.bedrock.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class BedrockPackSelectionStore {
	public static final String OPTION_KEY = "bedrockCompatPacks";

	private final Path optionsFile;

	public BedrockPackSelectionStore(Path optionsFile) {
		this.optionsFile = optionsFile;
	}

	public synchronized List<String> load() throws IOException {
		if (!Files.isRegularFile(optionsFile)) return List.of();
		List<String> selected = new ArrayList<>();
		for (String line : Files.readAllLines(optionsFile, StandardCharsets.UTF_8)) {
			if (!line.startsWith(OPTION_KEY + ":")) continue;
			String value = line.substring(OPTION_KEY.length() + 1);
			JsonElement parsed = JsonParser.parseString(value);
			if (!parsed.isJsonArray()) continue;
			for (JsonElement item : parsed.getAsJsonArray()) {
				if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) selected.add(item.getAsString());
			}
		}
		return List.copyOf(new LinkedHashSet<>(selected));
	}

	public synchronized void save(List<String> selectedIds) throws IOException {
		Path parent = optionsFile.getParent();
		if (parent != null) Files.createDirectories(parent);
		List<String> lines = Files.isRegularFile(optionsFile)
			? new ArrayList<>(Files.readAllLines(optionsFile, StandardCharsets.UTF_8)) : new ArrayList<>();
		lines.removeIf(line -> line.startsWith(OPTION_KEY + ":"));

		JsonArray array = new JsonArray();
		new LinkedHashSet<>(selectedIds).forEach(array::add);
		lines.add(OPTION_KEY + ":" + array);

		Path temporary = optionsFile.resolveSibling(optionsFile.getFileName() + ".bedrock_compat.tmp");
		Files.write(temporary, lines, StandardCharsets.UTF_8);
		try {
			Files.move(temporary, optionsFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(temporary, optionsFile, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
