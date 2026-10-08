package dev.matanyah.bedrockcompat.bedrock.discovery;

import com.google.gson.JsonParseException;
import dev.matanyah.bedrockcompat.BedrockCompat;
import dev.matanyah.bedrockcompat.bedrock.format.BedrockManifestParser;
import dev.matanyah.bedrockcompat.bedrock.model.BedrockPack;
import dev.matanyah.bedrockcompat.bedrock.model.PackLoadResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public final class BedrockPackRepository {
	private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
	private static final int MAX_ICON_BYTES = 8 * 1024 * 1024;
	private static final int MAX_LANGUAGE_BYTES = 1024 * 1024;
	private static final int MAX_NESTED_ARCHIVE_BYTES = 64 * 1024 * 1024;
	private static final int MAX_ARCHIVE_ENTRIES = 100_000;
	private static final int MAX_PACKS_PER_ARCHIVE = 256;
	private static final int DIRECTORY_SCAN_DEPTH = 5;

	private final Path addonsDirectory;
	private final BedrockManifestParser parser;

	public BedrockPackRepository(Path gameDirectory) {
		this(gameDirectory.resolve("addons"), new BedrockManifestParser());
	}

	BedrockPackRepository(Path addonsDirectory, BedrockManifestParser parser) {
		this.addonsDirectory = addonsDirectory;
		this.parser = parser;
	}

	public Path addonsDirectory() {
		return addonsDirectory;
	}

	public List<PackLoadResult> scan() {
		List<PackLoadResult> results = new ArrayList<>();
		try {
			Files.createDirectories(addonsDirectory);
			Set<Path> manifests = new HashSet<>();
			try (Stream<Path> paths = Files.walk(addonsDirectory, DIRECTORY_SCAN_DEPTH)) {
				paths.filter(Files::isRegularFile).forEach(path -> {
					String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
					if (name.equals("manifest.json")) manifests.add(path);
					else if (name.endsWith(".mcpack") || name.endsWith(".mcaddon")) results.addAll(scanArchive(path));
				});
			}
			manifests.stream().sorted().map(this::scanManifestFile).forEach(results::add);
		} catch (IOException | UncheckedIOException | SecurityException exception) {
			BedrockCompat.LOGGER.error("Could not scan add-ons directory {}", addonsDirectory, exception);
			results.add(PackLoadResult.failed(addonsDirectory, "", concise(exception)));
		}
		results.sort(Comparator.comparing(PackLoadResult::displayName, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(result -> result.source().toString())
			.thenComparing(PackLoadResult::locationInSource));
		return List.copyOf(results);
	}

	private PackLoadResult scanManifestFile(Path manifest) {
		Path packDirectory = manifest.getParent();
		Path icon = packDirectory.resolve("pack_icon.png");
		try {
			byte[] iconBytes = Files.isRegularFile(icon) ? readLimited(() -> Files.newInputStream(icon), MAX_ICON_BYTES) : null;
			Path language = packDirectory.resolve("texts/en_US.lang");
			Map<String, String> translations = Files.isRegularFile(language)
				? parseLanguage(readLimited(() -> Files.newInputStream(language), MAX_LANGUAGE_BYTES)) : Map.of();
			byte[] manifestBytes = readLimited(() -> Files.newInputStream(manifest), MAX_MANIFEST_BYTES);
			return parse(manifestBytes, packDirectory, "manifest.json", iconBytes, translations);
		} catch (Exception exception) {
			return failure(packDirectory, "manifest.json", exception);
		}
	}

	private List<PackLoadResult> scanArchive(Path archive) {
		List<PackLoadResult> results = new ArrayList<>();
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			Map<String, ZipEntry> entries = safeEntries(zip);
			List<String> manifests = entries.keySet().stream().filter(BedrockPackRepository::isManifest).sorted().toList();
			if (manifests.size() > MAX_PACKS_PER_ARCHIVE) throw new IOException("Archive contains too many manifests");
			for (String manifestName : manifests) {
				String prefix = parentPrefix(manifestName);
				ZipEntry icon = findCaseInsensitive(entries, prefix + "pack_icon.png");
				ZipEntry language = findCaseInsensitive(entries, prefix + "texts/en_US.lang");
				byte[] iconBytes = icon == null ? null : readLimited(() -> zip.getInputStream(icon), MAX_ICON_BYTES);
				Map<String, String> translations = language == null ? Map.of()
					: parseLanguage(readLimited(() -> zip.getInputStream(language), MAX_LANGUAGE_BYTES));
				byte[] manifestBytes = readLimited(() -> zip.getInputStream(entries.get(manifestName)), MAX_MANIFEST_BYTES);
				results.add(parse(manifestBytes, archive, manifestName, iconBytes, translations));
			}

			if (archive.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mcaddon")) {
				for (Map.Entry<String, ZipEntry> entry : entries.entrySet()) {
					if (entry.getKey().toLowerCase(Locale.ROOT).endsWith(".mcpack")) {
						byte[] nested = readLimited(() -> zip.getInputStream(entry.getValue()), MAX_NESTED_ARCHIVE_BYTES);
						results.addAll(scanNestedArchive(archive, entry.getKey(), nested));
					}
				}
			}
			if (results.isEmpty()) results.add(PackLoadResult.failed(archive, "", "Archive contains no manifest.json"));
		} catch (Exception exception) {
			results.clear();
			results.add(failure(archive, "", exception));
		}
		return results;
	}

	private List<PackLoadResult> scanNestedArchive(Path outerArchive, String nestedName, byte[] bytes) throws IOException {
		Map<String, byte[]> selected = new HashMap<>();
		int count = 0;
		try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
			ZipEntry entry;
			while ((entry = input.getNextEntry()) != null) {
				if (++count > MAX_ARCHIVE_ENTRIES) throw new IOException("Nested archive contains too many entries");
				String name = safeEntryName(entry.getName());
				String lowerName = name.toLowerCase(Locale.ROOT);
				if (!entry.isDirectory() && (isManifest(name) || lowerName.endsWith("pack_icon.png")
					|| lowerName.endsWith("texts/en_us.lang"))) {
					int limit = isManifest(name) ? MAX_MANIFEST_BYTES
						: lowerName.endsWith(".lang") ? MAX_LANGUAGE_BYTES : MAX_ICON_BYTES;
					selected.put(name, readLimited(input, limit));
				}
			}
		}
		List<String> manifests = selected.keySet().stream().filter(BedrockPackRepository::isManifest).sorted().toList();
		if (manifests.size() > MAX_PACKS_PER_ARCHIVE) throw new IOException("Nested archive contains too many manifests");
		List<PackLoadResult> results = new ArrayList<>();
		for (String manifest : manifests) {
			String location = nestedName + "!/" + manifest;
			byte[] icon = findCaseInsensitiveBytes(selected, parentPrefix(manifest) + "pack_icon.png");
			byte[] language = findCaseInsensitiveBytes(selected, parentPrefix(manifest) + "texts/en_US.lang");
			Map<String, String> translations = language == null ? Map.of() : parseLanguage(language);
			results.add(parse(selected.get(manifest), outerArchive, location, icon, translations));
		}
		if (results.isEmpty()) results.add(PackLoadResult.failed(outerArchive, nestedName, "Nested .mcpack contains no manifest.json"));
		return results;
	}

	private Map<String, ZipEntry> safeEntries(ZipFile zip) throws IOException {
		Map<String, ZipEntry> entries = new HashMap<>();
		Enumeration<? extends ZipEntry> enumeration = zip.entries();
		int count = 0;
		while (enumeration.hasMoreElements()) {
			if (++count > MAX_ARCHIVE_ENTRIES) throw new IOException("Archive contains too many entries");
			ZipEntry entry = enumeration.nextElement();
			if (!entry.isDirectory()) {
				String name = safeEntryName(entry.getName());
				String lowerName = name.toLowerCase(Locale.ROOT);
				if (isManifest(name) || lowerName.endsWith("pack_icon.png") || lowerName.endsWith("texts/en_us.lang")
					|| lowerName.endsWith(".mcpack")) {
					entries.put(name, entry);
				}
			}
		}
		return entries;
	}

	private PackLoadResult parse(byte[] manifest, Path source, String location, byte[] icon,
		Map<String, String> translations) {
		try {
			BedrockPack pack = parser.parse(manifest, source, location, icon, translations);
			if (!pack.warnings().isEmpty()) BedrockCompat.LOGGER.warn("Loaded {} with warnings: {}", source, pack.warnings());
			return PackLoadResult.loaded(pack);
		} catch (JsonParseException | IllegalArgumentException exception) {
			return failure(source, location, exception);
		}
	}

	private PackLoadResult failure(Path source, String location, Exception exception) {
		BedrockCompat.LOGGER.warn("Could not load Bedrock pack {}{}", source,
			location.isBlank() ? "" : "!/" + location, exception);
		return PackLoadResult.failed(source, location, concise(exception));
	}

	private static byte[] readLimited(IOSupplier<InputStream> supplier, int limit) throws IOException {
		try (InputStream input = supplier.get()) {
			return readLimited(input, limit);
		}
	}

	private static byte[] readLimited(InputStream input, int limit) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
		byte[] buffer = new byte[8192];
		int total = 0;
		int read;
		while ((read = input.read(buffer)) != -1) {
			total += read;
			if (total > limit) throw new IOException("Entry exceeds the " + limit + " byte safety limit");
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}

	private static String safeEntryName(String rawName) throws IOException {
		String name = rawName.replace('\\', '/');
		if (name.startsWith("/") || name.matches("^[A-Za-z]:.*")) throw new IOException("Archive contains an absolute path");
		for (String part : name.split("/")) {
			if (part.equals("..")) throw new IOException("Archive contains a parent path segment");
		}
		return name;
	}

	private static boolean isManifest(String name) {
		return name.equalsIgnoreCase("manifest.json") || name.toLowerCase(Locale.ROOT).endsWith("/manifest.json");
	}

	private static String parentPrefix(String name) {
		int slash = name.lastIndexOf('/');
		return slash < 0 ? "" : name.substring(0, slash + 1);
	}

	private static ZipEntry findCaseInsensitive(Map<String, ZipEntry> entries, String name) {
		return entries.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
			.map(Map.Entry::getValue).findFirst().orElse(null);
	}

	private static byte[] findCaseInsensitiveBytes(Map<String, byte[]> entries, String name) {
		return entries.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
			.map(Map.Entry::getValue).findFirst().orElse(null);
	}

	private static String concise(Exception exception) {
		String message = exception.getMessage();
		return exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
	}

	private static Map<String, String> parseLanguage(byte[] bytes) {
		Map<String, String> translations = new HashMap<>();
		String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
		for (String line : content.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
			int separator = line.indexOf('=');
			if (separator <= 0) continue;
			String key = line.substring(0, separator).trim();
			String value = line.substring(separator + 1);
			int comment = value.indexOf("\t#");
			if (comment >= 0) value = value.substring(0, comment);
			translations.putIfAbsent(key, value.trim());
		}
		return Map.copyOf(translations);
	}

	@FunctionalInterface
	private interface IOSupplier<T> {
		T get() throws IOException;
	}
}
