package dev.matanyah.bedrockcompat.bedrock.entity;

/** Bedrock model units to the Java living-renderer frame (before its yaw/Y flip). */
public final class BedrockModelSpace {
    private BedrockModelSpace() {}
    public static double headYaw(float bodyYaw,float relativeHeadYaw) {
        return (double)bodyYaw+relativeHeadYaw;
    }
    /** A sleeping clip already lies along the bed axis; it does not need the standing facing turn. */
    public static float livingYaw(float yaw,Float bedYaw) {
        return bedYaw==null?180-yaw:-bedYaw;
    }
    public static org.joml.Vector3f camera(double dx,double dy,double dz,double yaw) {
        double angle=Math.toRadians(yaw),c=Math.cos(angle),s=Math.sin(angle);
        return new org.joml.Vector3f((float)((c*dx+s*dz)*16),(float)(dy*16),(float)((s*dx-c*dz)*16));
    }
    public static org.joml.Matrix4f rendererTransform() {
        return new org.joml.Matrix4f().translate(0,1.5f,0).scale(1/16f,-1/16f,1/16f);
    }
    /** The renderer's Y reflection reverses handedness, including face winding. */
    public static int rendererVertexIndex(int index,int vertexCount) {
        return vertexCount-1-index;
    }
    /** Java block submissions cannot reverse their vertices like our native meshes.
     * Compensate the Y reflection on X, as for Java held-item attachments. */
    public static org.joml.Matrix4f containedBlockTransform(org.joml.Matrix4fc anchor,double scale) {
        return rendererTransform().mul(anchor).scale(-16,16,16)
            .scale((float)scale).translate(-.5f,0,-.5f);
    }
}
