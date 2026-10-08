package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.Map;

/** Vanilla 26.2 non-living renderer textures. Edition IDs are not texture filenames. */
public final class EntityTextureNames {
    private EntityTextureNames() {}
    private static final Map<String,String> PATHS=Map.ofEntries(
        Map.entry("arrow","projectiles/arrow"),
        Map.entry("spectral_arrow","projectiles/arrow_spectral"),
        Map.entry("minecart","minecart/minecart"),
        Map.entry("chest_minecart","minecart/minecart"),
        Map.entry("command_block_minecart","minecart/minecart"),
        Map.entry("furnace_minecart","minecart/minecart"),
        Map.entry("hopper_minecart","minecart/minecart"),
        Map.entry("tnt_minecart","minecart/minecart"),
        Map.entry("spawner_minecart","minecart/minecart"),
        Map.entry("end_crystal","end_crystal/end_crystal"),
        Map.entry("trident","trident/trident"),
        Map.entry("fishing_bobber","fishing/fishing_hook"),
        Map.entry("leash_knot","lead_knot/lead_knot"),
        Map.entry("experience_orb","experience/experience_orb"),
        Map.entry("shulker_bullet","shulker/spark"),
        Map.entry("wind_charge","projectiles/wind_charge"),
        Map.entry("breeze_wind_charge","projectiles/wind_charge"));

    public static String nonLiving(String entity,boolean tippedArrow) {
        var boat=BedrockEntityIds.boat(entity);
        if(boat.isPresent())return boat.get().javaTexture();
        int colon=entity.indexOf(':');
        String namespace=colon<0?"minecraft":entity.substring(0,colon),name=entity.substring(colon+1);
        String path=name;
        if(namespace.equals("minecraft"))
            path=name.equals("arrow")&&tippedArrow?"projectiles/arrow_tipped":PATHS.getOrDefault(name,name);
        return namespace+":textures/entity/"+path+".png";
    }
}
