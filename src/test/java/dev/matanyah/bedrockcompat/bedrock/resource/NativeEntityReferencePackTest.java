package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.JsonParser;
import dev.matanyah.bedrockcompat.bedrock.entity.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/** Optional read-only audit; copyrighted reference packs are never checked into the project. */
class NativeEntityReferencePackTest {
    @Test void auditReferenceDefinitions() throws Exception {
        String path = System.getenv("BEDROCK_REFERENCE_PACK");
        assumeTrue(path != null && !path.isBlank(), "Set BEDROCK_REFERENCE_PACK to audit a local pack");
        Map<String,Integer> accepted = new TreeMap<>(), failures = new TreeMap<>();
        try (ZipFile zip = new ZipFile(path)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("__brarchive/") || !name.endsWith(".brarchive")) continue;
                if (!(name.contains("entity") || name.contains("render_controllers") || name.contains("animation"))) continue;
                try (var stream = zip.getInputStream(entry)) {
                    BrArchiveReader.visit(stream.readAllBytes(), (source,bytes) -> {
                        var document = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                        NativeEntityResourceTranslator.definitions(document).forEach((id,definition) -> {
                            String type = id.startsWith("geometry.") ? "geometry" : id.startsWith("controller.render.") ? "render" :
                                id.startsWith("controller.animation.") ? "animation_controller" : id.startsWith("animation.") ? "animation" : id.contains(":") ? "entity" : "other";
                            try {
                                switch (type) {
                                    case "geometry" -> BedrockGeometry.parse(definition);
                                    case "render" -> new BedrockRenderController(definition);
                                    case "entity" -> BedrockClientEntity.parse(definition);
                                    case "animation" -> new BedrockAnimation(definition);
                                    case "animation_controller" -> new BedrockAnimationController(definition);
                                }
                                accepted.merge(type, 1, Integer::sum);
                            } catch (RuntimeException exception) {
                                failures.merge(type + ": " + exception.getMessage(), 1, Integer::sum);
                                System.out.println("Unsupported native definition " + id + ": " + exception);
                            }
                        });
                    });
                }
            }
        }
        System.out.println("Native definition audit accepted=" + accepted + " unsupported=" + failures);
        assertTrue(accepted.getOrDefault("geometry", 0) > 0, "Reference audit did not parse any geometry");
        assertTrue(accepted.getOrDefault("entity", 0) > 0, "Reference audit did not parse any client entities");
        // This is coverage evidence, not an assertion that every feature renders correctly.
    }
}
