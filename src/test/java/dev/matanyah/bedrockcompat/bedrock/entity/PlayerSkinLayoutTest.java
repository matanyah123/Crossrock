package dev.matanyah.bedrockcompat.bedrock.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerSkinLayoutTest {
    @Test void facialOverlaySamplingDoesNotTurnPaddingIntoBlackSkin() {
        for(int[] xy:new int[][]{{42,16},{43,19},{2,18},{18,32},{56,0},{0,0},{16,48},{32,48}})
            assertFalse(PlayerSkinLayout.baseTexel(xy[0],xy[1]),"Padding at "+xy[0]+","+xy[1]);
    }
    @Test void allSixBaseCubeNetsRemainOpaqueAndOverlaysRemainIndependent() {
        for(int[] xy:new int[][]{{8,8},{8,0},{4,16},{0,20},{20,16},{16,20},
                {44,16},{40,20},{20,48},{16,52},{36,48},{32,52}}) {
            assertTrue(PlayerSkinLayout.baseTexel(xy[0],xy[1]));
        }
        for(int y=0;y<64;y++)for(int x=0;x<64;x++) {
            boolean javaOpaqueRegion=x<32&&y<16||y>=16&&y<32||x>=16&&x<48&&y>=48;
            if(PlayerSkinLayout.baseTexel(x,y))assertTrue(javaOpaqueRegion);
        }
        assertFalse(PlayerSkinLayout.baseTexel(40,8)); // hat
        assertFalse(PlayerSkinLayout.baseTexel(20,36)); // jacket
        assertFalse(PlayerSkinLayout.baseTexel(44,36)); // sleeve
    }
}
