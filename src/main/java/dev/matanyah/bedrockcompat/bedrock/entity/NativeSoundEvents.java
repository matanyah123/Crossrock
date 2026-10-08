package dev.matanyah.bedrockcompat.bedrock.entity;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import net.minecraft.resources.Identifier;

/** Collision-free IDs shared by sound generation and native client-entity actions. */
public final class NativeSoundEvents {
    private NativeSoundEvents() {}
    public static Identifier id(String bedrockEvent) {
        String name=bedrockEvent.toLowerCase(Locale.ROOT);
        if(name.startsWith("minecraft:"))name=name.substring(10);
        return Identifier.fromNamespaceAndPath("bedrock_native","event/"+HexFormat.of().formatHex(name.getBytes(StandardCharsets.UTF_8)));
    }
}
