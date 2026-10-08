package dev.matanyah.bedrockcompat.client.discovery;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class AddonDirectoryWatcher implements AutoCloseable {
	private static final int MAX_DEPTH = 5;

	private final Path root;
	private final WatchService service;
	private final Map<WatchKey, Path> directories = new HashMap<>();

	public AddonDirectoryWatcher(Path root) throws IOException {
		this.root = root;
		Files.createDirectories(root);
		service = FileSystems.getDefault().newWatchService();
		registerTree(root);
	}

	public boolean pollForChanges() throws IOException {
		boolean changed = false;
		WatchKey key;
		while ((key = service.poll()) != null) {
			Path directory = directories.get(key);
			for (WatchEvent<?> event : key.pollEvents()) {
				changed = true;
				if (event.kind() == StandardWatchEventKinds.OVERFLOW || directory == null) continue;
				Path affected = directory.resolve((Path) event.context());
				if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(affected)) registerTree(affected);
			}
			if (!key.reset()) directories.remove(key);
		}
		return changed;
	}

	private void registerTree(Path start) throws IOException {
		int depth = start.equals(root) ? 0 : root.relativize(start).getNameCount();
		if (depth > MAX_DEPTH) return;
		int remainingDepth = MAX_DEPTH - depth;
		try (Stream<Path> paths = Files.walk(start, remainingDepth)) {
			for (Path directory : paths.filter(Files::isDirectory).toList()) register(directory);
		}
	}

	private void register(Path directory) throws IOException {
		WatchKey key = directory.register(service, StandardWatchEventKinds.ENTRY_CREATE,
			StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
		directories.put(key, directory);
	}

	@Override
	public void close() throws IOException {
		directories.clear();
		service.close();
	}
}
