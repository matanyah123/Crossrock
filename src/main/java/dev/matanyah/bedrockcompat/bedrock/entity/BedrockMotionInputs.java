package dev.matanyah.bedrockcompat.bedrock.entity;

/** Client-observed motion, independent of server-only movement accumulators. */
public final class BedrockMotionInputs {
    private double time=Double.NaN,x,y,z,distance,walkDistance;
    private Sample last=new Sample(0,0,0,0,0,0,0);
    public record Sample(double distance,double walkDistance,double groundSpeed,double verticalSpeed,double dx,double dy,double dz) {}
    public static double timeOfDay(long ticks) { return (Math.floorMod(ticks,24000)+6000)%24000/24000d; }
    public static double day(long ticks) { return (double)Math.floorDiv(ticks,24000)+1; }
    public static double moonPhase(long ticks) { return (double)Math.floorMod(Math.floorDiv(ticks,24000),8); }
    public static double moonBrightness(long ticks) { return Math.abs(moonPhase(ticks)-4)/4; }
    public Sample sample(double now,double px,double py,double pz,boolean walking) {
        if(!Double.isNaN(time)&&now<=time)return last;
        double dx=0,dy=0,dz=0,dt=0;
        if(!Double.isNaN(time)) {
            dt=now-time;dx=px-x;dy=py-y;dz=pz-z;
            // A teleport is not a walking stride.
            if(dx*dx+dy*dy+dz*dz>4096){dx=dy=dz=0;}
        }
        double horizontal=Math.hypot(dx,dz);
        distance+=horizontal;if(walking)walkDistance+=horizontal;
        time=now;x=px;y=py;z=pz;
        return last=new Sample(distance,walkDistance,dt>0?horizontal/dt:0,dt>0?dy/dt:0,dt>0?dx/dt/20:0,dt>0?dy/dt/20:0,dt>0?dz/dt/20:0);
    }
}
