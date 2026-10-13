package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.*;

/** Sulfur's opaque negative-size inner masks must face out, not hide the core's near side.
 * Keep translucent inside/outside shell pairs and ordinary geometry untouched. */
public final class SulfurCubeGeometry {
    private SulfurCubeGeometry() {}
    /** Magma supplies its inside/outside textures on a coincident, oppositely-sized
     * shell pair. Correct that pair only; its face planes and lava lumps are separate. */
    public static List<BedrockGeometryMesh.Quad> magmaShell(BedrockGeometry geometry,
            List<BedrockGeometryMesh.Quad> quads,BedrockMaterials.Material material) {
        if(!material.blend()||!material.cull())return List.copyOf(quads);
        Set<String> shells=new HashSet<>();
        for(var bone:geometry.bones())if(bone.cubes().size()==2) {
            var a=bone.cubes().get(0);var b=bone.cubes().get(1);
            if(a.size().x()<0&&a.size().y()<0&&a.size().z()<0
                    &&a.size().x()==-b.size().x()&&a.size().y()==-b.size().y()&&a.size().z()==-b.size().z()
                    &&a.origin().x()+a.size().x()==b.origin().x()
                    &&a.origin().y()+a.size().y()==b.origin().y()
                    &&a.origin().z()+a.size().z()==b.origin().z())shells.add(bone.name());
        }
        return reverse(quads,shells);
    }
    public static List<BedrockGeometryMesh.Quad> opaqueCore(BedrockGeometry geometry,
            List<BedrockGeometryMesh.Quad> quads,BedrockMaterials.Material material) {
        if(material.blend()||!material.cull())return List.copyOf(quads);
        Set<String> inward=new HashSet<>();
        for(var bone:geometry.bones())if(!bone.cubes().isEmpty()&&bone.cubes().stream().allMatch(c->
                c.size().x()<0&&c.size().y()<0&&c.size().z()<0))inward.add(bone.name());
        return reverse(quads,inward);
    }
    private static List<BedrockGeometryMesh.Quad> reverse(List<BedrockGeometryMesh.Quad> quads,Set<String> inward) {
        return quads.stream().map(q->{
            if(!inward.contains(q.bone()))return q;
            var vertices=new ArrayList<BedrockGeometryMesh.Vertex>();
            for(int i=q.vertices().size()-1;i>=0;i--) {
                var v=q.vertices().get(i);var n=v.normal();
                vertices.add(new BedrockGeometryMesh.Vertex(v.x(),v.y(),v.z(),v.u(),v.v(),
                    n==null?null:new BedrockGeometry.Vec(-n.x(),-n.y(),-n.z())));
            }
            return new BedrockGeometryMesh.Quad(q.bone(),q.material(),List.copyOf(vertices),-q.nx(),-q.ny(),-q.nz());
        }).toList();
    }
}
