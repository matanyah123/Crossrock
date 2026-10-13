package dev.matanyah.bedrockcompat.bedrock.entity;

/** Base-layer texels in the standard 64x64 humanoid skin atlas (not its padding). */
public final class PlayerSkinLayout {
    private PlayerSkinLayout() {}

    public static boolean baseTexel(int x,int y) {
        return rect(x,y,8,0,24,8)||rect(x,y,0,8,32,16)
            ||rect(x,y,4,16,12,20)||rect(x,y,0,20,16,32)
            ||rect(x,y,20,16,36,20)||rect(x,y,16,20,40,32)
            ||rect(x,y,44,16,52,20)||rect(x,y,40,20,56,32)
            ||rect(x,y,20,48,28,52)||rect(x,y,16,52,32,64)
            ||rect(x,y,36,48,44,52)||rect(x,y,32,52,48,64);
    }

    private static boolean rect(int x,int y,int left,int top,int right,int bottom) {
        return x>=left&&x<right&&y>=top&&y<bottom;
    }
}
