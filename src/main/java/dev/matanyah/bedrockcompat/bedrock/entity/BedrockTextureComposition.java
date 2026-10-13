package dev.matanyah.bedrockcompat.bedrock.entity;

/** Pixel operations use straight ARGB, not alpha-over layering. */
public final class BedrockTextureComposition {
    private BedrockTextureComposition() {}

    /** USE_COLOR_MASK stores dye coverage in alpha; it is not surface opacity. */
    public static int colorMask(int argb,int dye,boolean alphaTest) {
        int alpha=argb>>>24;
        if(alphaTest&&alpha==0)return argb&0xffffff;
        int result=0xff000000;
        for(int shift:new int[]{16,8,0}) {
            int channel=argb>>>shift&255,tint=dye>>>shift&255;
            result|=(int)Math.round(channel*((255-alpha)+alpha*tint/255.0)/255.0)<<shift;
        }
        return result;
    }

    /** USE_EMISSIVE consumes alpha as a light mask, not Java blend opacity.
     * ALPHA_TEST still cuts out exactly-zero pixels, including transparent black.
     * A value of 1 is a visible, almost fully emissive pixel, not 1/255 opacity. */
    public static int emissiveOpacity(int argb,boolean alphaTest) {
        return alphaTest&&(argb>>>24)==0?argb&0xffffff:argb|0xff000000;
    }

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
