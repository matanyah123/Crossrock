package dev.matanyah.bedrockcompat.bedrock.entity;

/** Pixel operations use straight ARGB, not alpha-over layering. */
public final class BedrockTextureComposition {
    private BedrockTextureComposition() {}

    public static int masked(int base,int mask) {
        // A nonblack RGB value with any transparency is a preserve-base marker.
        // Transparent black is a cutout, not a request to reveal the base layer.
        return (mask&0xffffff)!=0&&(mask>>>24)!=255?base:mask;
    }

    /** Common point-sampling grid; unlike max(), this preserves non-divisible sheets. */
    public static int commonSize(int first,int second) {
        if(first<=0||second<=0)throw new IllegalArgumentException("Invalid masked texture dimensions");
        int a=first,b=second;
        while(b!=0){int remainder=a%b;a=b;b=remainder;}
        long size=(long)(first/a)*second;
        if(size>4096)throw new IllegalArgumentException("Masked texture sampling grid exceeds 4096");
        return (int)size;
    }
}
