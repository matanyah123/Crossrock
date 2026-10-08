package dev.matanyah.bedrockcompat.client.entity;

public interface NativeRenderState {
    NativeEntityRenderer.Frame bedrockCompatFrame();
    void bedrockCompatFrame(NativeEntityRenderer.Frame frame);
}
