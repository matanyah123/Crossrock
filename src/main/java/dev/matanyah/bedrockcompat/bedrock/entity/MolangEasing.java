package dev.matanyah.bedrockcompat.bedrock.entity;

/** Bedrock's three-argument easing functions: start, end, progress. */
final class MolangEasing {
    static double sample(String name,double progress) {
        double t=Math.clamp(progress,0,1);
        if(t==0||t==1)return t;
        if(name.startsWith("in_out_")) {
            String curve=name.substring(7);
            // Back and elastic use their standard in/out overshoot periods.
            if(curve.equals("back")) {
                double k=1.70158*1.525,u=2*t;
                return t<.5?u*u*((k+1)*u-k)/2:((u-2)*(u-2)*((k+1)*(u-2)+k)+2)/2;
            }
            if(curve.equals("elastic")) {
                double s=Math.sin((20*t-11.125)*2*Math.PI/4.5);
                return t<.5?-Math.pow(2,20*t-10)*s/2:Math.pow(2,-20*t+10)*s/2+1;
            }
            return t<.5?in(curve,2*t)/2:1-in(curve,2-2*t)/2;
        }
        if(name.startsWith("out_"))return 1-in(name.substring(4),1-t);
        if(name.startsWith("in_"))return in(name.substring(3),t);
        throw new IllegalArgumentException("Unsupported Molang easing: "+name);
    }
    private static double in(String curve,double t) {
        return switch(curve) {
            case "quad"->t*t;
            case "cubic"->t*t*t;
            case "quart"->Math.pow(t,4);
            case "quint"->Math.pow(t,5);
            case "sine"->1-Math.cos(t*Math.PI/2);
            case "expo"->t==0?0:Math.pow(2,10*t-10);
            case "circ"->1-Math.sqrt(1-t*t);
            case "back"->2.70158*t*t*t-1.70158*t*t;
            case "elastic"->t==0||t==1?t:-Math.pow(2,10*t-10)*Math.sin((10*t-10.75)*2*Math.PI/3);
            case "bounce"->1-bounce(1-t);
            default->throw new IllegalArgumentException("Unsupported Molang easing curve: "+curve);
        };
    }
    private static double bounce(double t) {
        if(t<1/2.75)return 7.5625*t*t;
        if(t<2/2.75){t-=1.5/2.75;return 7.5625*t*t+.75;}
        if(t<2.5/2.75){t-=2.25/2.75;return 7.5625*t*t+.9375;}
        t-=2.625/2.75;return 7.5625*t*t+.984375;
    }
}
