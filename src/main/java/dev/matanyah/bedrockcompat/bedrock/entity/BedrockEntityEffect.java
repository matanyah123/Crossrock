package dev.matanyah.bedrockcompat.bedrock.entity;

/** Immutable resource-driven action, dispatched outside geometry generation. */
public record BedrockEntityEffect(Kind kind,String effect,String locator) {
    public enum Kind { SOUND, PARTICLE }
}
