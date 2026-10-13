package dev.matanyah.bedrockcompat.bedrock.entity;

/** Immutable resource-driven action, dispatched outside geometry generation. */
public record BedrockEntityEffect(Kind kind,String effect,String locator,String preEffectScript,boolean bindToActor,boolean managed) {
    public BedrockEntityEffect(Kind kind,String effect,String locator,String script){this(kind,effect,locator,script,true,false);}
    public BedrockEntityEffect(Kind kind,String effect,String locator){this(kind,effect,locator,"");}
    public enum Kind { SOUND, PARTICLE, PARTICLE_STOP }
}
