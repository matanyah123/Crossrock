package dev.matanyah.bedrockcompat.bedrock.ui;

import com.google.gson.*;
import java.util.*;
import java.util.regex.Pattern;

/** Pure GUI-unit layout and animation sampling; no file IO or game-engine dependencies. */
public final class BedrockUiLayout {
    public record Rect(double x,double y,double width,double height) {
        public Rect intersect(Rect r){double left=Math.max(x,r.x),top=Math.max(y,r.y);return new Rect(left,top,Math.max(0,Math.min(x+width,r.x+r.width)-left),Math.max(0,Math.min(y+height,r.y+r.height)-top));}
    }
    public record Draw(String name,String type,Rect bounds,Rect clip,double alpha,Map<String,JsonElement> properties) {}
    private static final Pattern DIMENSION=Pattern.compile("([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+))(%|px)?(?:\\s*([+-])\\s*([0-9]+(?:\\.[0-9]*)?)px)?");
    public static List<Draw> layout(BedrockUi.Program program,String root,double width,double height,Map<String,JsonElement> data,double seconds) {
        return layout(program,root,width,height,data,Map.of(),seconds);
    }
    public static List<Draw> layout(BedrockUi.Program program,String root,double width,double height,Map<String,JsonElement> data,Map<String,List<Map<String,JsonElement>>> collections,double seconds) {
        var node=program.roots().get(root);if(node==null)return List.of();
        if(!Double.isFinite(width)||!Double.isFinite(height)||width<0||height<0||!Double.isFinite(seconds))throw new IllegalArgumentException("Invalid UI viewport/clock");
        var out=new ArrayList<Draw>();var viewport=new Rect(0,0,width,height);
        visit(program,node,viewport,viewport,1,data,collections,Math.max(0,seconds),out,0,0,false,root,true,new int[]{0});
        return List.copyOf(out);
    }
    private static void visit(BedrockUi.Program program,BedrockUi.Node node,Rect parent,Rect clip,double inheritedAlpha,Map<String,JsonElement> data,Map<String,List<Map<String,JsonElement>>> collections,double time,List<Draw> out,double stackX,double stackY,boolean stacked,String path,boolean parentEnabled,int[] budget) {
        if(++budget[0]>16384)throw new IllegalArgumentException("Runtime UI expansion limit exceeded");
        var p=new LinkedHashMap<>(node.properties());
        p.put("__path",new JsonPrimitive(path));
        if(data.containsKey("__slot"))p.put("__slot",data.get("__slot"));
        if(p.containsKey("bindings"))for(var item:p.get("bindings").getAsJsonArray()) {
            var binding=item.getAsJsonObject();
            for(String key:binding.keySet())if(!Set.of("binding_name","binding_name_override","binding_type","binding_condition","binding_collection_name","source_property_name","target_property_name").contains(key))throw new IllegalArgumentException(node.name()+": Unsupported binding field "+key);
            String type=string(binding.asMap(),"binding_type","global"),condition=string(binding.asMap(),"binding_condition","always");
            if(type.equals("view")){
                if(!condition.equals("always"))throw new IllegalArgumentException("Unsupported view binding condition");
                var context=new HashMap<>(data);context.putAll(p);
                String source=binding.get("source_property_name").getAsString(),target=binding.get("target_property_name").getAsString();
                JsonElement result=BedrockUiExpression.isExpression(source)?BedrockUiExpression.evaluate(source,context::get):context.get(source);
                if(result==null)throw new IllegalArgumentException("Unavailable view binding: "+source);
                if(!Set.of("#visible","#text","#alpha","#enabled").contains(target))throw new IllegalArgumentException("Unsupported view binding target: "+target);
                p.put(target.substring(1),result);continue;
            }
            if(!(type.equals("global")||type.equals("collection")&&data.containsKey("__collection")&&data.get("__collection").getAsString().equals(string(binding.asMap(),"binding_collection_name","")))||!condition.equals("always"))throw new IllegalArgumentException("Unsupported binding type/condition: "+type+"/"+condition);
            String name=binding.get("binding_name").getAsString();
            if(!data.containsKey(name))throw new IllegalArgumentException("Unavailable UI data: "+name);
            String target=string(binding.asMap(),"binding_name_override",name);
            if(target.equals(name))p.put(name,data.get(name));
            else if(Set.of("#visible","#text","#alpha","#enabled").contains(target))p.put(target.substring(1),data.get(name));
            else throw new IllegalArgumentException("Unsupported binding target: "+target);
        }
        var expressionContext=new HashMap<>(data);expressionContext.putAll(p);
        for(String key:List.of("visible","ignored","enabled","alpha","layer","text","size","offset","color","min_size","max_size"))if(p.containsKey(key))p.put(key,evaluate(p.get(key),expressionContext));
        if(p.containsKey("anims"))for(var ref:p.get("anims").getAsJsonArray())sample(program,BedrockUi.qualify(node.namespace(),ref.getAsString().replaceFirst("^@","")),time,p);
        if(bool(p,"ignored",false)||!bool(p,"visible",true))return;
        p.put("enabled",new JsonPrimitive(parentEnabled&&bool(p,"enabled",true)));
        double w=axis(p.get("size"),0,parent.width,parent.width),h=axis(p.get("size"),1,parent.height,parent.height);
        w=Math.max(axis(p.get("min_size"),0,parent.width,0),Math.min(axis(p.get("max_size"),0,parent.width,1e6),w));
        h=Math.max(axis(p.get("min_size"),1,parent.height,0),Math.min(axis(p.get("max_size"),1,parent.height,1e6),h));
        if(w<0||h<0||w>1e6||h>1e6)throw new IllegalArgumentException("UI size out of range");
        double[] from=anchor(string(p,"anchor_from","center")),to=anchor(string(p,"anchor_to","center"));
        double x=parent.x+(stacked?stackX:parent.width*from[0]-w*to[0])+axis(p.get("offset"),0,parent.width,0);
        double y=parent.y+(stacked?stackY:parent.height*from[1]-h*to[1])+axis(p.get("offset"),1,parent.height,0);
        var rect=new Rect(x,y,w,h);
        double alpha=Math.clamp(number(p,"alpha",1)*inheritedAlpha,0,1);
        String type=string(p,"type","panel");
        if(type.equals("label")&&p.containsKey("text")) {
            String text=p.get("text").getAsString();
            if(text.startsWith("#")){var value=p.containsKey(text)?p.get(text):data.get(text);if(value==null)throw new IllegalArgumentException("Unavailable UI text: "+text);p.put("text",new JsonPrimitive(value.getAsString()));}
        }
        out.add(new Draw(node.name(),type,rect,clip,alpha,Map.copyOf(p)));
        var childClip=bool(p,"clips_children",false)?clip.intersect(rect):clip;
        var children=new ArrayList<>(node.children());
        if(type.equals("grid")) {
            String collection=string(p,"collection_name","");var items=collections.get(collection);
            if(items==null)throw new IllegalArgumentException("Unavailable UI collection: "+collection);
            var dimensions=p.get("grid_dimensions").getAsJsonArray();
            int columns=dimensions.get(0).getAsInt(),rows=dimensions.get(1).getAsInt();
            if(dimensions.size()!=2||dimensions.get(0).getAsDouble()!=columns||dimensions.get(1).getAsDouble()!=rows||columns<=0||rows<=0||(long)columns*rows>512||columns*rows!=items.size()||children.size()!=1)throw new IllegalArgumentException("Grid dimensions do not match authoritative collection");
            double cw=w/columns,ch=h/rows;
            for(int i=0;i<items.size();i++) {
                var scoped=new HashMap<>(data);scoped.putAll(items.get(i));scoped.put("__collection",new JsonPrimitive(collection));scoped.put("#collection_index",new JsonPrimitive(i));
                visit(program,children.getFirst(),new Rect(x+(i%columns)*cw,y+(i/columns)*ch,cw,ch),childClip,bool(p,"propagate_alpha",false)?alpha:1,scoped,collections,time,out,0,0,false,path+"/"+i,bool(p,"enabled",true),budget);
            }
            return;
        }
        boolean stack=type.equals("stack_panel"),horizontal=string(p,"orientation","vertical").equals("horizontal");
        if(!Set.of("horizontal","vertical").contains(string(p,"orientation","vertical")))throw new IllegalArgumentException("Unsupported stack orientation");
        // Layout flow follows source order, independent of render layer.
        var groups=new ArrayList<List<Draw>>();double cursor=0;
        String state=string(data,"__state:"+path,"default"),selected=string(p,state+"_control",state);
        for(var child:children){
            if(type.equals("button")&&Set.of(string(p,"default_control","default"),string(p,"hover_control","hover"),string(p,"pressed_control","pressed")).contains(child.name())&&!child.name().equals(selected))continue;
            var group=new ArrayList<Draw>();visit(program,child,rect,childClip,bool(p,"propagate_alpha",false)?alpha:1,data,collections,time,group,horizontal?cursor:0,horizontal?0:cursor,stack,path+"/"+child.name(),bool(p,"enabled",true),budget);
            if(stack&&!group.isEmpty())cursor+=horizontal?group.getFirst().bounds.width:group.getFirst().bounds.height;
            groups.add(group);}
        groups.sort(Comparator.comparingDouble(g->g.isEmpty()?0:number(g.getFirst().properties,"layer",0)));
        groups.forEach(out::addAll);
    }
    private static JsonElement evaluate(JsonElement value,Map<String,JsonElement> context){
        if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()&&BedrockUiExpression.isExpression(value.getAsString()))return BedrockUiExpression.evaluate(value.getAsString(),context::get);
        if(value.isJsonArray()){var array=new JsonArray();for(var item:value.getAsJsonArray())array.add(evaluate(item,context));return array;}return value;
    }
    public static double dimension(JsonElement value,double parent) {
        if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isNumber())return finite(value.getAsDouble());
        String s=value.getAsString();if(s.equals("fill"))return parent;
        var m=DIMENSION.matcher(s);if(!m.matches())throw new IllegalArgumentException("Unsupported UI dimension: "+s);
        double n=Double.parseDouble(m.group(1));if("%".equals(m.group(2)))n=n*parent/100;
        if(m.group(3)!=null)n+=(m.group(3).equals("-")?-1:1)*Double.parseDouble(m.group(4));return finite(n);
    }
    private static double axis(JsonElement value,int axis,double parent,double fallback){return value==null?fallback:dimension(value.getAsJsonArray().get(axis),parent);}
    private static double finite(double n){if(!Double.isFinite(n))throw new IllegalArgumentException("Nonfinite UI number");return n;}
    public static double[] anchor(String value){return switch(value){
        case "top_left"->new double[]{0,0};case "top_middle"->new double[]{.5,0};case "top_right"->new double[]{1,0};
        case "left_middle"->new double[]{0,.5};case "center"->new double[]{.5,.5};case "right_middle"->new double[]{1,.5};
        case "bottom_left"->new double[]{0,1};case "bottom_middle"->new double[]{.5,1};case "bottom_right"->new double[]{1,1};default->throw new IllegalArgumentException("Unsupported anchor: "+value);};}
    private static void sample(BedrockUi.Program program,String id,double seconds,Map<String,JsonElement> props) {
        var chain=new ArrayList<BedrockUi.Animation>();var seen=new LinkedHashMap<String,Integer>();int cycle=-1;
        while(!id.isEmpty()) {
            if(seen.containsKey(id)){cycle=seen.get(id);break;}
            if(chain.size()>=256)throw new IllegalArgumentException("UI animation chain limit");
            seen.put(id,chain.size());var a=program.animations().get(id);if(a==null)throw new IllegalArgumentException("Missing UI animation: "+id);chain.add(a);id=a.next();
        }
        if(cycle>=0){double prefix=0,period=0;for(int i=0;i<chain.size();i++)if(i<cycle)prefix+=chain.get(i).duration();else period+=chain.get(i).duration();if(seconds>=prefix)seconds=prefix+(seconds-prefix)%period;}
        for(var a:chain){double t=Math.clamp(seconds/a.duration(),0,1);t=switch(a.easing()){case "in_quad"->t*t;case "out_quad"->1-(1-t)*(1-t);case "in_out_quad"->t<.5?2*t*t:1-Math.pow(-2*t+2,2)/2;default->t;};props.put(a.property(),interpolate(a.from(),a.to(),t));if(seconds<a.duration())break;seconds-=a.duration();}
    }
    private static JsonElement interpolate(JsonElement a,JsonElement b,double t) {
        if(a==null||b==null)throw new IllegalArgumentException("Missing animation endpoint");
        if(a.isJsonArray()&&b.isJsonArray()) {if(a.getAsJsonArray().size()!=b.getAsJsonArray().size())throw new IllegalArgumentException("Mismatched animation vector");var out=new JsonArray();for(int i=0;i<a.getAsJsonArray().size();i++)out.add(interpolate(a.getAsJsonArray().get(i),b.getAsJsonArray().get(i),t));return out;}
        if(!a.isJsonPrimitive()||!b.isJsonPrimitive()||!a.getAsJsonPrimitive().isNumber()||!b.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Only numeric UI animation endpoints supported");
        return new JsonPrimitive(finite(a.getAsDouble()+(b.getAsDouble()-a.getAsDouble())*t));
    }
    public static String string(Map<String,JsonElement> p,String key,String fallback){return p.containsKey(key)?p.get(key).getAsString():fallback;}
    public static boolean bool(Map<String,JsonElement> p,String key,boolean fallback){if(!p.containsKey(key))return fallback;var v=p.get(key);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Unsupported boolean expression: "+key);return v.getAsBoolean();}
    public static double number(Map<String,JsonElement> p,String key,double fallback){return p.containsKey(key)?finite(p.get(key).getAsDouble()):fallback;}
}
