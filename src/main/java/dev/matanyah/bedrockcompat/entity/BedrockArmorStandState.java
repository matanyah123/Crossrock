package dev.matanyah.bedrockcompat.entity;

/** Independent Bedrock state. Java limb rotations are deliberately not stored or changed here. */
public final class BedrockArmorStandState {
    public static final int POSE_COUNT = 13;
    public static final String POSE_KEY = "bedrock_compat:pose_index";
    public static final String ACTIVE_KEY = "bedrock_compat:armor_stand";
    public static final String ORIGINAL_ARMS_KEY = "bedrock_compat:original_show_arms";
    private int poseIndex;
    private boolean active;
    private boolean originalArms;

    public int poseIndex() { return poseIndex; }
    public boolean active() { return active; }
    public boolean originalArms() { return originalArms; }
    public void poseIndex(int index) { poseIndex = Math.floorMod(index, POSE_COUNT); }
    public int cycle() { poseIndex((poseIndex + 1)); return poseIndex; }
    public void activate(boolean existingArms) {
        if (!active) originalArms = existingArms;
        active = true;
    }
    public void deactivate() { active = false; }
    public void restore(boolean enabled, int index, boolean previousArms) {
        active = enabled; poseIndex(index); originalArms = previousArms;
    }
}
