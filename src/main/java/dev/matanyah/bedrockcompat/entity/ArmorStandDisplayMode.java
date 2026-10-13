package dev.matanyah.bedrockcompat.entity;

/** Control-name display modes, applied only when a Bedrock pack owns the stand renderer. */
public enum ArmorStandDisplayMode {
    NORMAL(0), PROP(0), ITEM(0);
    private final double originY;
    ArmorStandDisplayMode(double originY){this.originY=originY;}
    public double originY(){return originY;}
    public boolean hidesName(){return this!=NORMAL;}
    public static ArmorStandDisplayMode fromName(String name) {
        return switch(name){case "§§"->PROP;case "§§§§"->ITEM;default->NORMAL;};
    }
}
