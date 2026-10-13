package dev.matanyah.bedrockcompat.client.ui.json;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.BedrockCompat;
import dev.matanyah.bedrockcompat.bedrock.ui.*;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.fabricmc.fabric.api.resource.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.*;
import java.util.*;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import dev.matanyah.bedrockcompat.client.mixin.NativeJsonUiContainerAccess;
import dev.matanyah.bedrockcompat.client.mixin.NativeJsonUiSlotAccess;
import net.minecraft.world.inventory.Slot;

/** Bounded presentation/interaction bridge. Java owns all inventory operations and networking. */
public final class NativeJsonUi implements SimpleSynchronousResourceReloadListener {
    private static BedrockUi.Program program=BedrockUi.Program.EMPTY;
    private static BedrockUiDependencies.Report dependencies=BedrockUiDependencies.inspect(List.of());
    private static Map<String,int[]> textures=Map.of();
    private static final Set<String> errors=new LinkedHashSet<>();
    private static final Map<Screen,Session> screens=new WeakHashMap<>();
    private static Session hud=new Session();private static Object hudLevel;
    private static int lastDrawCount;
    private static final Map<String,Long> renderCounts=new HashMap<>();
    private static final class Session {
        final long born=System.nanoTime();long stamp=Long.MIN_VALUE;int width,height,dataHash;List<BedrockUiLayout.Draw> draws=List.of();
        final Map<String,NativeJsonUiButton> buttons=new LinkedHashMap<>();
        final Map<Slot,BedrockUiSlots.Slot> originals=new IdentityHashMap<>();
        Screen owner;
    }
    public static BedrockUi.Program program(){return program;}
    public static BedrockUiDependencies.Report dependencies(){return dependencies;}
    public static List<String> errors(){return List.copyOf(errors);}
    public static int lastDrawCount(){return lastDrawCount;}
    public static long renderCount(String root){return renderCounts.getOrDefault(root,0L);}
    public static String adapter(Screen screen) {
        if(screen==null)return "hud.hud_screen";
        // Exact classes protect modded subclasses and Crossrock's configuration/recovery UI.
        if(screen.getClass()==TitleScreen.class)return "start.start_screen";
        if(screen.getClass()==PauseScreen.class)return "pause.pause_screen";
        if(screen.getClass()==InventoryScreen.class)return "inventory.inventory_screen";
        if(screen.getClass()==ContainerScreen.class)return "chest.chest_screen";
        if(screen.getClass()==CraftingScreen.class)return "crafting.crafting_screen";
        if(screen.getClass()==FurnaceScreen.class)return "furnace.furnace_screen";
        if(screen.getClass()==SmokerScreen.class)return "smoker.smoker_screen";
        if(screen.getClass()==BlastFurnaceScreen.class)return "blast_furnace.blast_furnace_screen";
        if(screen.getClass()==AnvilScreen.class)return "anvil.anvil_screen";
        if(screen.getClass()==EnchantmentScreen.class)return "enchanting.enchanting_screen";
        if(screen.getClass()==BrewingStandScreen.class)return "brewing_stand.brewing_stand_screen";
        if(screen.getClass()==SmithingScreen.class)return "smithing_table.smithing_table_screen";
        if(screen.getClass()==ShulkerBoxScreen.class)return "shulker_box.shulker_box_screen";
        return "";
    }
    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new NativeJsonUi());
        ScreenEvents.AFTER_INIT.register((mc,screen,w,h)-> {
            if(adapter(screen).isEmpty())return;
            var previous=screens.remove(screen);if(previous!=null)clear(previous);
            var session=new Session();session.owner=screen;screens.put(screen,session);
            if(screen.getClass()!=TitleScreen.class)ScreenEvents.afterBackground(screen).register((s,g,x,y,t)->render(s,g));
            ScreenEvents.remove(screen).register(s->{var old=screens.remove(s);if(old!=null)clear(old);});
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS,Identifier.fromNamespaceAndPath("bedrock_compat","json_ui"),(g,t)-> {
            var mc=Minecraft.getInstance();if(mc.player!=null&&!mc.gui.hud.isHidden())render(null,g);
        });
    }
    @Override public Identifier getFabricId(){return Identifier.fromNamespaceAndPath("bedrock_compat","json_ui");}
    @Override public void onResourceManagerReload(ResourceManager manager) {
        var sources=new ArrayList<BedrockUi.Source>();long[] total={0};errors.clear();screens.values().forEach(NativeJsonUi::clear);screens.clear();renderCounts.clear();hud=new Session();hudLevel=null;lastDrawCount=0;
        manager.listResources("json_ui",id->id.getNamespace().equals("bedrock_native")&&id.getPath().endsWith(".json"))
            .entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{
                try(var stream=e.getValue().open()) {
                    var bytes=stream.readNBytes(2*1024*1024+1);
                    if(bytes.length>2*1024*1024||(total[0]+=bytes.length)>16*1024*1024||sources.size()>=1024)throw new IOException("UI reload resource limits exceeded");
                    var envelope=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    sources.add(new BedrockUi.Source(envelope.get("source").getAsString(),envelope.getAsJsonObject("document")));
                }catch(Exception failure){diagnose(e.getKey()+": "+failure.getMessage());}
            });
        dependencies=BedrockUiDependencies.inspect(sources);
        program=BedrockUi.compile(sources);program.errors().forEach(NativeJsonUi::diagnose);
        var images=new HashMap<String,int[]>();
        for(var root:program.roots().values())collect(root,images,manager);
        textures=Map.copyOf(images);
    }
    private static void collect(BedrockUi.Node node,Map<String,int[]> images,ResourceManager manager) {
        var p=node.properties();
        if(p.containsKey("texture"))try {
            String path=texturePath(p.get("texture").getAsString());
            if(!images.containsKey(path))try(var in=new DataInputStream(manager.getResource(Identifier.fromNamespaceAndPath("bedrock_native",path)).orElseThrow().open())) {
                if(in.readLong()!=0x89504e470d0a1a0aL||in.readInt()!=13||in.readInt()!=0x49484452)throw new IOException("Invalid PNG header");
                int w=in.readInt(),h=in.readInt();if(w<=0||h<=0||w>16384||h>16384)throw new IOException("PNG dimensions out of range");images.put(path,new int[]{w,h});
            }
        }catch(Exception e){diagnose(node.name()+": "+e.getMessage());}
        node.children().forEach(n->collect(n,images,manager));
    }
    public static String texturePath(String path) {
        if(!path.startsWith("textures/")||path.contains("..")||path.contains(":")||!Identifier.isValidPath(path))throw new IllegalArgumentException("Unsafe UI texture "+path);
        return path.endsWith(".png")?path:path+".png";
    }
    public static void render(Screen screen,GuiGraphicsExtractor graphics) {
        String root=adapter(screen);lastDrawCount=0;
        if(root.isEmpty()||!program.roots().containsKey(root))return;
        var mc=Minecraft.getInstance();
        if(hudLevel!=mc.level){hudLevel=mc.level;hud=new Session();}
        var session=screen==null?hud:screens.computeIfAbsent(screen,s->{var next=new Session();next.owner=s;return next;});
        var data=new HashMap<String,JsonElement>();
        data.put("#player_name",new JsonPrimitive(mc.player==null?mc.getUser().getName():mc.player.getName().getString()));
        data.put("#hud_visible",new JsonPrimitive(!mc.gui.hud.isHidden()));
        if(mc.player!=null){data.put("#level_number",new JsonPrimitive(mc.player.experienceLevel));data.put("#level_number_visible",new JsonPrimitive(mc.player.experienceLevel>0));}
        session.buttons.forEach((path,button)->data.put("__state:"+path,new JsonPrimitive(button.state())));
        var collections=collections(screen);
        double seconds=(System.nanoTime()-session.born)/1e9;
        // Static trees cache until viewport/data changes. Animated trees sample at most once per millisecond.
        long stamp=program.animations().isEmpty()?0:(long)(seconds*1000);int hash=Objects.hash(data,collections);
        try {
            if(session.width!=graphics.guiWidth()||session.height!=graphics.guiHeight()||session.dataHash!=hash||session.stamp!=stamp) {
                session.draws=BedrockUiLayout.layout(program,root,graphics.guiWidth(),graphics.guiHeight(),data,collections,seconds);
                session.width=graphics.guiWidth();session.height=graphics.guiHeight();session.dataHash=hash;session.stamp=stamp;
            }
            // Validate the entire draw list before rendering, so one unavailable image cannot half-replace a tree.
            for(var draw:session.draws)validateDraw(draw);
            sync(session,root);
            for(var draw:session.draws)draw(graphics,draw);
            lastDrawCount=session.draws.size();
            if(lastDrawCount>0)renderCounts.merge(root,1L,Long::sum);
        }catch(RuntimeException failure){clear(session);session.draws=List.of();session.width=graphics.guiWidth();session.height=graphics.guiHeight();session.dataHash=hash;session.stamp=stamp;diagnose(root+": "+failure.getMessage());}
    }
    private static Map<String,List<Map<String,JsonElement>>> collections(Screen screen) {
        if(!(screen instanceof AbstractContainerScreen<?> container))return Map.of();
        var mc=Minecraft.getInstance();var out=new HashMap<String,List<Map<String,JsonElement>>>();
        for(var slot:container.getMenu().slots) {
            if(!slot.isActive()||slot.isFake())continue;
            String name=slot.container==mc.player.getInventory()?(slot.getContainerSlot()<9?"hotbar_items":slot.getContainerSlot()<36?"inventory_items":"equipment_items"):"container_items";
            var data=new HashMap<String,JsonElement>();data.put("__slot",new JsonPrimitive(slot.index));data.put("#inventory_stack_count",new JsonPrimitive(slot.getItem().getCount()));
            out.computeIfAbsent(name,k->new ArrayList<>()).add(Map.copyOf(data));
        }
        return out;
    }
    private static void restore(Session session){session.originals.forEach((slot,p)->{((NativeJsonUiSlotAccess)slot).bedrockUiX(p.x());((NativeJsonUiSlotAccess)slot).bedrockUiY(p.y());});}
    private static void detach(Session session,NativeJsonUiButton button){button.active=false;button.visible=false;button.setFocused(false);if(session.owner.getFocused()==button)session.owner.setFocused(null);Screens.getWidgets(session.owner).remove(button);}
    private static void clear(Session session){restore(session);session.originals.clear();if(session.owner!=null)session.buttons.values().forEach(button->detach(session,button));session.buttons.clear();}
    private static void sync(Session session,String root) {
        Screen screen=session.owner;
        var plan=Map.<Integer,BedrockUiSlots.Slot>of();
        BedrockUiLayout.Rect panel=null;
        if(screen instanceof AbstractContainerScreen<?> container) {
            var access=(NativeJsonUiContainerAccess)container;panel=new BedrockUiLayout.Rect(access.bedrockUiLeft(),access.bedrockUiTop(),access.bedrockUiWidth(),access.bedrockUiHeight());
            for(var slot:container.getMenu().slots)session.originals.putIfAbsent(slot,new BedrockUiSlots.Slot(slot.index,slot.x,slot.y));
            plan=BedrockUiSlots.plan(session.draws,List.copyOf(session.originals.values()),panel);
        }else if(session.draws.stream().anyMatch(d->d.type().equals("custom")))throw new IllegalArgumentException("Inventory renderer unavailable on this screen");
        var next=new LinkedHashMap<String,BedrockUiLayout.Draw>();
        for(var draw:session.draws)if(draw.type().equals("button")) {
            if(screen==null)throw new IllegalArgumentException("HUD input unsupported");
            for(var mapping:BedrockUiInteraction.mappings(draw.properties()))if(!BedrockUiInteraction.supportedAction(mapping.action(),root))throw new IllegalArgumentException("Unsupported action: "+mapping.action());
            for(var widget:Screens.getWidgets(screen))if(!(widget instanceof NativeJsonUiButton)&&widget.visible&&BedrockUiInteraction.overlaps(draw.bounds(),new BedrockUiLayout.Rect(widget.getX(),widget.getY(),widget.getWidth(),widget.getHeight())))throw new IllegalArgumentException("Imported button overlaps native widget");
            if(panel!=null&&screen instanceof AbstractContainerScreen<?> container)for(var slot:container.getMenu().slots) {
                var p=plan.getOrDefault(slot.index,session.originals.get(slot));
                if(BedrockUiInteraction.overlaps(draw.bounds(),new BedrockUiLayout.Rect(panel.x()+p.x(),panel.y()+p.y(),16,16)))throw new IllegalArgumentException("Imported button overlaps native slot");
            }
            if(next.values().stream().anyMatch(d->BedrockUiInteraction.overlaps(d.bounds(),draw.bounds())))throw new IllegalArgumentException("Overlapping imported buttons unsupported");
            next.put(draw.properties().get("__path").getAsString(),draw);
        }
        // Commit only after the complete plan is validated. Native foreground draws and clicks read these same fields.
        restore(session);
        if(screen instanceof AbstractContainerScreen<?> container)for(var slot:container.getMenu().slots)if(plan.containsKey(slot.index)){var p=plan.get(slot.index);((NativeJsonUiSlotAccess)slot).bedrockUiX(p.x());((NativeJsonUiSlotAccess)slot).bedrockUiY(p.y());}
        if(screen==null)return;
        var widgets=Screens.getWidgets(screen);
        session.buttons.entrySet().removeIf(e->{if(next.containsKey(e.getKey()))return false;detach(session,e.getValue());return true;});
        next.forEach((path,draw)->{var button=session.buttons.get(path);if(button==null){button=new NativeJsonUiButton(draw,action->execute(screen,action));session.buttons.put(path,button);widgets.add(button);}else button.update(draw);});
    }
    private static void execute(Screen screen,String action) {
        var mc=Minecraft.getInstance();if(mc.gui.screen()!=screen||!BedrockUiInteraction.supportedAction(action,adapter(screen)))return;
        if(action.equals("button.menu_settings"))mc.gui.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(screen,mc.options,false));
        else screen.onClose();
    }
    private static void validateDraw(BedrockUiLayout.Draw d) {
        var p=d.properties();color(p.get("color"),d.alpha());
        if(d.type().equals("image")) {
            String path=texturePath(p.get("texture").getAsString());var size=textures.get(path);
            if(size==null)throw new IllegalArgumentException("Unavailable UI image: "+d.name());
            int u=axis(p.get("uv"),0,0),v=axis(p.get("uv"),1,0),w=axis(p.get("uv_size"),0,size[0]),h=axis(p.get("uv_size"),1,size[1]);
            if(u<0||v<0||w<=0||h<=0||(long)u+w>size[0]||(long)v+h>size[1])throw new IllegalArgumentException("UI UV region outside texture");
            dev.matanyah.bedrockcompat.bedrock.ui.BedrockUiNineSlice.regions(p,0,0,(int)Math.round(d.bounds().width()),(int)Math.round(d.bounds().height()),u,v,w,h);
        }
        if(d.type().equals("label")){if(!Set.of("left","center","right").contains(BedrockUiLayout.string(p,"text_alignment","center")))throw new IllegalArgumentException("Unsupported text alignment");BedrockUiLayout.bool(p,"shadow",false);}
    }
    private static void draw(GuiGraphicsExtractor g,BedrockUiLayout.Draw d) {
        var b=d.bounds();var c=d.clip();var p=d.properties();
        if(c.width()<=0||c.height()<=0||d.alpha()<=0)return;
        g.enableScissor((int)Math.floor(c.x()),(int)Math.floor(c.y()),(int)Math.ceil(c.x()+c.width()),(int)Math.ceil(c.y()+c.height()));
        try {
            int color=color(p.get("color"),d.alpha()),x=(int)Math.round(b.x()),y=(int)Math.round(b.y()),w=(int)Math.round(b.width()),h=(int)Math.round(b.height());
            if(d.type().equals("label")) {
                var mc=Minecraft.getInstance();var text=Component.translatable(BedrockUiLayout.string(p,"text",""));
                String alignment=BedrockUiLayout.string(p,"text_alignment","center");int tw=mc.font.width(text);
                if(alignment.equals("center"))x+=(w-tw)/2;else if(alignment.equals("right"))x+=w-tw;else if(!alignment.equals("left"))throw new IllegalArgumentException("Unsupported text alignment");
                g.text(mc.font,text,x,y+(h-mc.font.lineHeight)/2,color,BedrockUiLayout.bool(p,"shadow",false));
            }else if(d.type().equals("image")) {
                String path=texturePath(p.get("texture").getAsString());int[] size=textures.get(path);
                int u=axis(p.get("uv"),0,0),v=axis(p.get("uv"),1,0),uw=axis(p.get("uv_size"),0,size[0]),vh=axis(p.get("uv_size"),1,size[1]);
                for(var slice:dev.matanyah.bedrockcompat.bedrock.ui.BedrockUiNineSlice.regions(p,x,y,w,h,u,v,uw,vh))
                    g.blit(RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("bedrock_native",path),slice.x(),slice.y(),slice.u(),slice.v(),slice.width(),slice.height(),slice.sourceWidth(),slice.sourceHeight(),size[0],size[1],color);
            }
        }finally{g.disableScissor();}
    }
    private static int axis(JsonElement a,int i,int fallback){return a==null?fallback:a.getAsJsonArray().get(i).getAsInt();}
    public static int color(JsonElement value,double alpha) {
        double[] rgba={1,1,1,1};if(value!=null){var a=value.getAsJsonArray();if(a.size()<3||a.size()>4)throw new IllegalArgumentException("Invalid color vector");for(int i=0;i<a.size();i++){rgba[i]=a.get(i).getAsDouble();if(!Double.isFinite(rgba[i]))throw new IllegalArgumentException("Nonfinite UI color");}}
        return ((int)Math.round(Math.clamp(alpha*rgba[3],0,1)*255)<<24)|((int)Math.round(Math.clamp(rgba[0],0,1)*255)<<16)|((int)Math.round(Math.clamp(rgba[1],0,1)*255)<<8)|(int)Math.round(Math.clamp(rgba[2],0,1)*255);
    }
    private static void diagnose(String error){if(errors.size()<256&&errors.add(error))BedrockCompat.LOGGER.warn("Bedrock JSON UI: {}",error);}
}
