package dev.matanyah.bedrockcompat.bedrock.entity;

import com.google.gson.*;
import java.util.*;

/** Resource-owned particle component data. Expressions are compiled once, not per spawn/frame. */
public final class BedrockParticleEffect {
    /** Align local X to motion while keeping the ribbon's normal towards the viewer. */
    public static org.joml.Quaternionf trailRotation(org.joml.Vector3f direction,org.joml.Vector3f view) {
        var axis=new org.joml.Vector3f(direction);
        if(axis.lengthSquared()==0)axis.set(0,1,0);
        axis.normalize();var up=new org.joml.Vector3f(view).cross(axis);
        if(up.lengthSquared()<1e-9f)up.set(Math.abs(axis.y)<.9f?new org.joml.Vector3f(0,1,0):new org.joml.Vector3f(1,0,0)).cross(axis);
        up.normalize();var normal=new org.joml.Vector3f(axis).cross(up).normalize();
        return new org.joml.Quaternionf().setFromNormalized(new org.joml.Matrix3f(axis,up,normal));
    }
    public final String id,texture,material;
    private final JsonObject components,curves,events;
    private final Map<String,Molang.Expression> expressions=new HashMap<>();
    public BedrockParticleEffect(JsonObject source) {
        var description=source.getAsJsonObject("description");
        id=description.get("identifier").getAsString();
        var render=description.getAsJsonObject("basic_render_parameters");
        texture=render.get("texture").getAsString().replaceFirst("\\.(png|tga)$","");
        material=render.get("material").getAsString();
        components=source.has("components")?source.getAsJsonObject("components").deepCopy():new JsonObject();
        curves=source.has("curves")?source.getAsJsonObject("curves").deepCopy():new JsonObject();
        events=source.has("events")?source.getAsJsonObject("events").deepCopy():new JsonObject();
        compile(components);compile(curves);compile(events);
    }
    private void compile(JsonElement element) {
        if(element.isJsonObject())element.getAsJsonObject().entrySet().forEach(e->compile(e.getValue()));
        else if(element.isJsonArray())element.getAsJsonArray().forEach(this::compile);
        else if(element.isJsonPrimitive()&&element.getAsJsonPrimitive().isString()) {
            String value=element.getAsString();
            try {expressions.put(value,Molang.compile(value));}catch(IllegalArgumentException ignored) { /* Enum, event, block or color literal. */ }
        }
    }
    public boolean has(String name){return components.has("minecraft:"+name);}
    public JsonObject component(String name){var v=components.get("minecraft:"+name);return v!=null&&v.isJsonObject()?v.getAsJsonObject():new JsonObject();}
    public JsonElement raw(String name){return components.get("minecraft:"+name);}
    public Set<String> components(){return components.keySet();}
    public JsonObject events(){return events;}
    public double number(JsonElement value,Molang.Context context,double fallback) {
        if(value==null||value.isJsonNull())return fallback;
        if(value.isJsonPrimitive()) {
            var primitive=value.getAsJsonPrimitive();
            if(primitive.isNumber())return primitive.getAsDouble();
            if(primitive.isBoolean())return primitive.getAsBoolean()?1:0;
            var expression=expressions.get(primitive.getAsString());
            if(expression==null)throw new IllegalArgumentException("Invalid particle expression: "+value);
            double result=Molang.number(expression.evaluate(context));
            return Double.isFinite(result)?result:fallback;
        }
        throw new IllegalArgumentException("Expected particle expression: "+value);
    }
    public double value(JsonObject object,String name,Molang.Context context,double fallback){return number(object.get(name),context,fallback);}
    public void script(JsonObject object,String name,Molang.Context context) {
        var value=object.get(name);if(value==null)return;
        if(value.isJsonArray())value.getAsJsonArray().forEach(v->number(v,context,0));else number(value,context,0);
    }
    public double[] vector(JsonElement value,Molang.Context context,double... fallback) {
        if(value==null)return fallback.clone();
        if(!value.isJsonArray()){double n=number(value,context,0);double[] v=new double[fallback.length];Arrays.fill(v,n);return v;}
        var array=value.getAsJsonArray();double[] out=fallback.clone();
        for(int i=0;i<Math.min(out.length,array.size());i++)out[i]=number(array.get(i),context,out[i]);
        return out;
    }
    public void curves(Molang.Context context) {
        for(var entry:curves.entrySet()) {
            var curve=entry.getValue().getAsJsonObject();
            double t=value(curve,"input",context,0)/Math.max(1e-9,value(curve,"horizontal_range",context,1));
            var raw=curve.get("nodes");if(raw==null)continue;
            String type=curve.has("type")?curve.get("type").getAsString():"linear";
            String name=entry.getKey().replaceFirst("^(variable|v)\\.","");
            if(raw.isJsonObject()) {
                var nodes=new TreeMap<Double,JsonObject>();raw.getAsJsonObject().entrySet().forEach(e->nodes.put(Double.parseDouble(e.getKey()),e.getValue().getAsJsonObject()));
                var lo=nodes.floorEntry(t);var hi=nodes.ceilingEntry(t);if(lo==null)lo=nodes.firstEntry();if(hi==null)hi=nodes.lastEntry();
                double span=hi.getKey()-lo.getKey(),f=span==0?0:(t-lo.getKey())/span;
                double a=value(lo.getValue(),"value",context,0),b=value(hi.getValue(),"value",context,0);
                double m=value(lo.getValue(),"slope",context,0)*span,n=value(hi.getValue(),"slope",context,0)*span;
                context.setVariable(name,(2*f*f*f-3*f*f+1)*a+(f*f*f-2*f*f+f)*m+(-2*f*f*f+3*f*f)*b+(f*f*f-f*f)*n);continue;
            }
            var nodes=raw.getAsJsonArray();if(nodes.isEmpty())continue;
            if(type.equals("bezier")&&nodes.size()==4) {
                double f=Math.clamp(t,0,1),g=1-f;
                context.setVariable(name,g*g*g*number(nodes.get(0),context,0)+3*g*g*f*number(nodes.get(1),context,0)+3*g*f*f*number(nodes.get(2),context,0)+f*f*f*number(nodes.get(3),context,0));continue;
            }
            if(type.equals("catmull_rom")&&nodes.size()>=4) {
                double position=Math.clamp(t,0,1)*(nodes.size()-3);int i=Math.min(nodes.size()-4,(int)position);double f=position-i;
                double p0=number(nodes.get(i),context,0),p1=number(nodes.get(i+1),context,0),p2=number(nodes.get(i+2),context,0),p3=number(nodes.get(i+3),context,0);
                context.setVariable(name,.5*((2*p1)+(-p0+p2)*f+(2*p0-5*p1+4*p2-p3)*f*f+(-p0+3*p1-3*p2+p3)*f*f*f));continue;
            }
            double position=Math.clamp(t,0,1)*(nodes.size()-1);int i=(int)position;
            double a=number(nodes.get(i),context,0),b=number(nodes.get(Math.min(i+1,nodes.size()-1)),context,0);
            context.setVariable(name,a+(b-a)*(position-i));
        }
    }
    public double[] color(JsonElement color,Molang.Context context) {
        if(color==null)return new double[]{1,1,1,1};
        if(color.isJsonPrimitive()&&color.getAsString().startsWith("#")) {
            String hex=color.getAsString().substring(1);long rgba=Long.parseLong(hex,16);
            return new double[]{((rgba>>16)&255)/255d,((rgba>>8)&255)/255d,(rgba&255)/255d,hex.length()==8?((rgba>>24)&255)/255d:1};
        }
        if(color.isJsonArray())return vector(color,context,1,1,1,1);
        var object=color.getAsJsonObject();var gradient=object.get("gradient");
        double t=value(object,"interpolant",context,0);var points=new TreeMap<Double,JsonElement>();
        if(gradient.isJsonArray()) {var array=gradient.getAsJsonArray();for(int i=0;i<array.size();i++)points.put(i/(double)Math.max(1,array.size()-1),array.get(i));}
        else gradient.getAsJsonObject().entrySet().forEach(e->points.put(Double.parseDouble(e.getKey()),e.getValue()));
        var lo=points.floorEntry(t);var hi=points.ceilingEntry(t);if(lo==null)lo=points.firstEntry();if(hi==null)hi=points.lastEntry();
        double[] a=color(lo.getValue(),context),b=color(hi.getValue(),context);double blend=lo.getKey().equals(hi.getKey())?0:(t-lo.getKey())/(hi.getKey()-lo.getKey());
        for(int i=0;i<4;i++)a[i]+=(b[i]-a[i])*blend;return a;
    }
    /** Normalized UV rectangle, including atlas flipbooks. */
    public double[] uv(Molang.Context context,double age,double lifetime) {
        var uv=component("particle_appearance_billboard").getAsJsonObject("uv");
        if(uv==null)return new double[]{0,0,1,1};
        double width=value(uv,"texture_width",context,1),height=value(uv,"texture_height",context,1);
        double[] origin=vector(uv.get("uv"),context,0,0),size=vector(uv.get("uv_size"),context,width,height);
        if(uv.has("flipbook")) {
            var flip=uv.getAsJsonObject("flipbook");origin=vector(flip.get("base_UV"),context,0,0);size=vector(flip.get("size_UV"),context,1,1);
            var step=vector(flip.get("step_UV"),context,0,0);int count=Math.max(1,(int)value(flip,"max_frame",context,1));
            double phase=value(flip,"stretch_to_lifetime",context,0)!=0?age/Math.max(1e-9,lifetime)*count:age*value(flip,"frames_per_second",context,1);
            int frame=(int)Math.max(0,phase);frame=value(flip,"loop",context,0)!=0?frame%count:Math.min(count-1,frame);
            origin[0]+=step[0]*frame;origin[1]+=step[1]*frame;
        }
        return new double[]{origin[0]/width,origin[1]/height,(origin[0]+size[0])/width,(origin[1]+size[1])/height};
    }
}
