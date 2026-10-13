package dev.matanyah.bedrockcompat.bedrock.resource;

import com.google.gson.*;
import dev.matanyah.bedrockcompat.bedrock.model.*;
import dev.matanyah.bedrockcompat.resource.GeneratedResourceSink;
import net.minecraft.resources.Identifier;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bedrock presentation assets become Java resources, never executable Bedrock UI documents. */
public final class BedrockPresentationTranslator implements BedrockResourceTranslator {
    private final BedrockPackContentReader reader;
    public BedrockPresentationTranslator(BedrockPackContentReader reader) { this.reader = reader; }

    @Override public void translate(BedrockPack pack, BedrockPackConfiguration configuration,
                                    GeneratedResourceSink sink) throws IOException {
        Map<String, byte[]> content = new LinkedHashMap<>();
        reader.visitPresentation(pack, configuration.selectedSubpack(), (path, data) ->
            content.put(path.replace('\\', '/').toLowerCase(Locale.ROOT), data));
        translate(content, sink);
    }

    static void translate(Map<String, byte[]> content, GeneratedResourceSink sink) throws IOException {
        List<String> errors = new ArrayList<>();
        BedrockWidgetTranslator.translate(content, sink, errors);
        panoramas(content, sink, errors);
        image(content, sink, "panorama_overlay", "textures/gui/title/background/panorama_overlay.png", 0, 0, errors);
        String[][] sprites = {
            {"heart","heart/full"},{"heart_half","heart/half"},{"heart_background","heart/container"},
            {"heart_flash","heart/full_blinking"},{"heart_flash_half","heart/half_blinking"},
            {"heart_blink","heart/container_blinking"},
            {"absorption_heart","heart/absorbing_full"},{"absorption_heart_half","heart/absorbing_half"},
            {"freeze_heart","heart/frozen_full"},{"freeze_heart_half","heart/frozen_half"},
            {"freeze_heart_flash","heart/frozen_full_blinking"},{"freeze_heart_flash_half","heart/frozen_half_blinking"},
            {"poison_heart","heart/poisoned_full"},{"poison_heart_half","heart/poisoned_half"},
            {"poison_heart_flash","heart/poisoned_full_blinking"},{"poison_heart_flash_half","heart/poisoned_half_blinking"},
            {"wither_heart","heart/withered_full"},{"wither_heart_half","heart/withered_half"},
            {"wither_heart_flash","heart/withered_full_blinking"},{"wither_heart_flash_half","heart/withered_half_blinking"},
            {"horse_heart","heart/vehicle_full"},{"horse_heart_half","heart/vehicle_half"},
            {"heart_background","heart/vehicle_container"},
            {"armor_empty","armor_empty"},{"armor_full","armor_full"},{"armor_half","armor_half"},
            {"bubble","air"},{"bubble_empty","air_empty"},{"bubble_pop","air_bursting"},
            {"hunger_background","food_empty"},{"hunger_full","food_full"},{"hunger_half","food_half"},
            {"hunger_effect_background","food_empty_hunger"},{"hunger_effect_full","food_full_hunger"},
            {"hunger_effect_half","food_half_hunger"},{"crosshair","crosshair"},
            {"selected_hotbar_slot","hotbar_selection"},
            {"experiencebarempty","experience_bar_background"},{"experiencebarfull","experience_bar_progress"},
            {"horse_jump_empty","jump_bar_background"},{"horse_jump_full","jump_bar_progress"}
        };
        for (String[] mapping : sprites) {
            String target = mapping[1];
            int w = target.contains("_bar_") ? 182 : target.equals("hotbar_selection") ? 24 : target.equals("crosshair") ? 15 : 9;
            int h = target.contains("_bar_") ? 5 : w;
            image(content, sink, mapping[0], "textures/gui/sprites/hud/" + target + ".png", w, h, errors);
        }
        try { hotbar(content, sink); } catch (IOException | RuntimeException ex) { errors.add("HUD hotbar: " + ex.getMessage()); }
        byte[] splash = content.getOrDefault("splashes.json", content.get("texts/splashes.json"));
        if (splash != null) try {
            JsonObject root = JsonParser.parseString(new String(splash, StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> lines = new ArrayList<>();
            for (JsonElement entry : root.getAsJsonArray("splashes")) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                    errors.add("Conditional splash entry is not supported"); continue;
                }
                String line = entry.getAsString().replace('\r', ' ').replace('\n', ' ');
                if (!line.isBlank()) lines.add(line);
            }
            boolean merge = root.has("canMerge") && root.get("canMerge").getAsBoolean();
            byte[] base = null;
            if (merge) try (InputStream stream = BedrockPresentationTranslator.class.getResourceAsStream("/assets/minecraft/texts/splashes.txt")) {
                if (stream != null) base = stream.readAllBytes();
            }
            if (!lines.isEmpty() || !merge) sink.putText(Identifier.withDefaultNamespace("texts/splashes.txt"),
                String.join("\n", lines).getBytes(StandardCharsets.UTF_8),
                merge, base);
        } catch (RuntimeException ex) { errors.add("Invalid Bedrock splashes.json: " + ex.getMessage()); }
        if (!errors.isEmpty()) throw new IOException(String.join("; ", errors));
    }

    private static void image(Map<String, byte[]> content, GeneratedResourceSink sink, String source,
                              String target, int w, int h, List<String> errors) {
        byte[] data = content.get("textures/ui/" + source + ".png");
        if (data == null) return;
        try {
            BufferedImage image = decode(data);
            if (source.matches("panorama_[0-5]") && image.getWidth() != image.getHeight())
                throw new IOException("Panorama face must be square");
            BufferedImage converted = w == 0 ? image : resize(image, w, h);
            byte[] metadata = content.get("textures/ui/" + source + ".json");
            if (w == 182 && (metadata != null || source.startsWith("experiencebar"))) {
                JsonObject descriptor = metadata == null ? JsonParser.parseString(source.equals("experiencebarempty")
                    ? "{\"nineslice_size\":[6,1,6,1],\"base_size\":[13,5]}"
                    : "{\"nineslice_size\":[1,0,1,0],\"base_size\":[13,5]}").getAsJsonObject()
                    : JsonParser.parseString(new String(metadata, StandardCharsets.UTF_8)).getAsJsonObject();
                if (descriptor.has("nineslice_size")) converted = nineSlice(image, descriptor, w, h);
            }
            sink.put(Identifier.withDefaultNamespace(target), w == 0 ? data : encode(converted));
        } catch (IOException | RuntimeException ex) { errors.add(source + ": " + ex.getMessage()); }
    }

    private static void panoramas(Map<String, byte[]> content, GeneratedResourceSink sink, List<String> errors) {
        Map<Integer,BufferedImage> faces=new LinkedHashMap<>();
        for(int i=0;i<6;i++) {
            byte[] data=content.get("textures/ui/panorama_"+i+".png");
            if(data==null)continue;
            try {
                BufferedImage image=decode(data);
                if(image.getWidth()!=image.getHeight())throw new IOException("Panorama face must be square");
                faces.put(i,image);
            }catch(IOException|RuntimeException ex){errors.add("panorama_"+i+": "+ex.getMessage());}
        }
        // Java loads these as one cubemap: even a valid square crashes if its size differs
        // from another face. Partial Bedrock overrides must match 26.2's 1024px vanilla faces.
        int size=1024; // One size across all selected Bedrock packs, including partial higher-priority overlays.
        for(var entry:faces.entrySet())try {
            BufferedImage source=entry.getValue(),out=source;
            if(source.getWidth()!=size) {
                out=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
                for(int y=0;y<size;y++)for(int x=0;x<size;x++)out.setRGB(x,y,source.getRGB(x*source.getWidth()/size,y*source.getHeight()/size));
            }
            sink.put(Identifier.withDefaultNamespace("textures/gui/title/background/panorama_"+entry.getKey()+".png"),encode(out));
        }catch(IOException|RuntimeException ex){errors.add("panorama_"+entry.getKey()+": "+ex.getMessage());}
    }

    private static void hotbar(Map<String, byte[]> content, GeneratedResourceSink sink) throws IOException {
        // Bedrock's nine individually rendered 20px slots become one Java 182px strip.
        List<BufferedImage> slots = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            byte[] slot = content.get("textures/ui/hotbar_" + i + ".png");
            if (slot == null) return; // Do not replace the whole strip with incomplete artwork.
            slots.add(decode(slot));
        }
        int scale = Math.max(1, slots.getFirst().getHeight() / 22);
        BufferedImage out = new BufferedImage(182 * scale, 22 * scale, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 9; i++) {
            BufferedImage image = slots.get(i);
            for (int y=0;y<22*scale;y++) for(int x=0;x<20*scale;x++)
                out.setRGB(scale+i*20*scale+x,y,image.getRGB(x*image.getWidth()/(20*scale),y*image.getHeight()/(22*scale)));
        }
        for (int cap=0;cap<2;cap++) {
            byte[] png = content.get("textures/ui/hotbar_" + (cap==0?"start":"end") + "_cap.png");
            BufferedImage image = png==null?null:decode(png);
            for(int y=0;y<out.getHeight();y++) for(int x=0;x<scale;x++)
                out.setRGB(cap==0?x:181*scale+x,y,image==null?out.getRGB(cap==0?scale:181*scale-1,y)
                    :image.getRGB(x*image.getWidth()/scale,y*image.getHeight()/out.getHeight()));
        }
        sink.put(Identifier.withDefaultNamespace("textures/gui/sprites/hud/hotbar.png"), encode(out));
    }
    private static BufferedImage nineSlice(BufferedImage image, JsonObject descriptor, int width, int height) {
        JsonArray base=descriptor.getAsJsonArray("base_size");
        int bw=base.get(0).getAsInt(),bh=base.get(1).getAsInt();
        if(bw<=0||bh<=0)throw new IllegalArgumentException("Invalid nine-slice base size");
        int scale=Math.max(1,image.getHeight()/bh);
        JsonElement slicing=descriptor.get("nineslice_size");
        int[] edges=new int[4];
        for(int i=0;i<4;i++)edges[i]=slicing.isJsonArray()?slicing.getAsJsonArray().get(i).getAsInt():slicing.getAsInt();
        if(Arrays.stream(edges).anyMatch(v->v<0)||edges[0]+edges[2]>=bw||edges[1]+edges[3]>=bh)
            throw new IllegalArgumentException("Invalid nine-slice edges");
        BufferedImage out=new BufferedImage(width*scale,height*scale,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<out.getHeight();y++)for(int x=0;x<out.getWidth();x++) {
            int sx=sliceCoordinate(x,out.getWidth(),image.getWidth(),edges[0]*scale,edges[2]*scale);
            int sy=sliceCoordinate(y,out.getHeight(),image.getHeight(),edges[1]*scale,edges[3]*scale);
            out.setRGB(x,y,image.getRGB(sx,sy));
        }
        return out;
    }
    private static int sliceCoordinate(int value,int target,int source,int before,int after) {
        if(before+after>=source||before+after>=target)throw new IllegalArgumentException("Nine-slice cannot fit target");
        if(value<before)return value;
        if(value>=target-after)return source-(target-value);
        return before+(value-before)*(source-before-after)/(target-before-after);
    }
    private static BufferedImage decode(byte[] data) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) throw new IOException("Invalid PNG");
        return image;
    }
    private static BufferedImage resize(BufferedImage image, int w, int h) {
        // Preserve pack resolution and crisp pixels; sprite dimensions carry the same logical aspect.
        int scale = Math.max(1, Math.min(image.getWidth() / w, image.getHeight() / h));
        BufferedImage out = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++) for (int x = 0; x < out.getWidth(); x++)
            out.setRGB(x, y, image.getRGB(x * image.getWidth() / out.getWidth(), y * image.getHeight() / out.getHeight()));
        return out;
    }
    private static byte[] encode(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(image, "PNG", out); return out.toByteArray();
    }
}
