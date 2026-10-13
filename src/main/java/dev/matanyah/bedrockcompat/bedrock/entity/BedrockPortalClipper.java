package dev.matanyah.bedrockcompat.bedrock.entity;

import org.joml.Vector3f;
import java.util.*;

/** Geometry-side aperture masks. Keeps portal geometry out of the global framebuffer stencil. */
public final class BedrockPortalClipper {
    public record Surface(BedrockMaterials.Material material,List<BedrockGeometryMesh.Quad> mesh) {}
    private record Fragment(BedrockGeometryMesh.Quad source,List<BedrockGeometryMesh.Vertex> vertices) {}
    private record Plane(Vector3f normal,float offset) {
        float distance(BedrockGeometryMesh.Vertex v){return normal.x*v.x()+normal.y*v.y()+normal.z*v.z()+offset;}
    }
    private BedrockPortalClipper() {}
    public static List<List<BedrockGeometryMesh.Quad>> clip(List<Surface> surfaces,Vector3f camera) {
        var result=new ArrayList<List<BedrockGeometryMesh.Quad>>();
        for(var surface:surfaces) {
            var material=surface.material();var read=material.stencil();
            if(!material.colorWrite()){result.add(List.of());continue;}
            if(read!=null&&read.function().equals("NotEqual")) {
                var polygons=new ArrayList<Fragment>();
                for(var q:surface.mesh())polygons.add(new Fragment(q,q.vertices()));
                // Outline/glow shells exclude the projected silhouette stamped by the
                // preceding body pass, regardless of the shell's own surface depth.
                for(var mask:surfaces) {
                    if(mask==surface)break;
                    var write=mask.material().stencil();
                    if(write==null||!write.write()||!write.replace()||!write.function().equals("Always")
                            ||(read.reference()&read.readMask())!=(write.reference()&read.readMask()))continue;
                    for(var aperture:mask.mesh()) {
                        var cone=planes(aperture,camera);
                        if(cone.size()<4)continue;
                        var remaining=new ArrayList<Fragment>();
                        for(var quad:polygons) {
                            var inside=quad.vertices();
                            for(var plane:cone.subList(1,cone.size())) {
                                var outside=halfSpace(inside,plane,false);
                                if(nonDegenerate(outside))remaining.add(new Fragment(quad.source(),outside));
                                inside=halfSpace(inside,plane,true);
                                if(inside.isEmpty())break;
                            }
                        }
                        polygons=remaining;
                        if(polygons.isEmpty())break;
                    }
                }
                var triangles=new ArrayList<BedrockGeometryMesh.Quad>();
                for(var polygon:polygons)triangulate(triangles,polygon.source(),polygon.vertices());
                result.add(List.copyOf(triangles));continue;
            }
            if(read==null||!read.function().equals("Equal")){result.add(surface.mesh());continue;}
            var apertures=new ArrayList<BedrockGeometryMesh.Quad>();
            for(var mask:surfaces) {
                var write=mask.material().stencil();
                if(write==null||!write.write()||!write.replace()||!write.function().equals("Always")||mask.material().colorWrite())continue;
                boolean matches=(read.reference()&read.readMask())==(write.reference()&read.readMask());
                // Some masks first stamp a region, then promote its low bits in a
                // second equality-tested write pass. Match the declared read mask.
                for(var bridge:surfaces) {
                    var pass=bridge.material().stencil();
                    if(pass!=null&&pass.write()&&pass.replace()&&pass.function().equals("Equal")
                            &&pass.reference()==read.reference()
                            &&(write.reference()&pass.readMask())==(pass.reference()&pass.readMask()))matches=true;
                }
                if(matches)apertures.addAll(mask.mesh());
            }
            if(apertures.isEmpty()){result.add(List.of());continue;} // Unwritten stencil is zero, never the portal reference.
            var aperture=apertures.stream().max(Comparator.comparingDouble(BedrockPortalClipper::area)).orElseThrow();
            var planes=planes(aperture,camera);
            if(planes.isEmpty()){result.add(List.of());continue;}
            var clipped=new ArrayList<BedrockGeometryMesh.Quad>();
            for(var quad:surface.mesh()) {
                List<BedrockGeometryMesh.Vertex> vertices=quad.vertices();
                for(var plane:planes) {
                    if(vertices.isEmpty())break;
                    var next=new ArrayList<BedrockGeometryMesh.Vertex>();
                    var previous=vertices.getLast();float before=plane.distance(previous);
                    for(var current:vertices) {
                        float after=plane.distance(current);
                        if((before>=0)!=(after>=0))next.add(lerp(previous,current,before/(before-after)));
                        if(after>=0)next.add(current);
                        previous=current;before=after;
                    }
                    vertices=next;
                }
                for(int i=1;i+1<vertices.size();i++)clipped.add(new BedrockGeometryMesh.Quad(quad.bone(),quad.material(),
                    List.of(vertices.getFirst(),vertices.get(i),vertices.get(i+1),vertices.get(i+1)),quad.nx(),quad.ny(),quad.nz()));
            }
            result.add(List.copyOf(clipped));
        }
        return List.copyOf(result);
    }
    private static boolean nonDegenerate(List<BedrockGeometryMesh.Vertex> vertices) {
        if(vertices.size()<3)return false;
        for(int i=1;i+1<vertices.size();i++) {
            var a=point(vertices.getFirst());
            if(point(vertices.get(i)).sub(a).cross(point(vertices.get(i+1)).sub(a)).lengthSquared()>1e-10f)return true;
        }
        return false;
    }
    private static List<BedrockGeometryMesh.Vertex> halfSpace(List<BedrockGeometryMesh.Vertex> vertices,Plane plane,boolean positive) {
        if(vertices.isEmpty())return List.of();
        var next=new ArrayList<BedrockGeometryMesh.Vertex>();
        var previous=vertices.getLast();float before=plane.distance(previous);
        for(var current:vertices) {
            float after=plane.distance(current);
            boolean wasInside=positive?before>=0:before<0, isInside=positive?after>=0:after<0;
            if(wasInside!=isInside)next.add(lerp(previous,current,before/(before-after)));
            if(isInside)next.add(current);
            previous=current;before=after;
        }
        return next;
    }
    private static void triangulate(List<BedrockGeometryMesh.Quad> output,BedrockGeometryMesh.Quad source,List<BedrockGeometryMesh.Vertex> vertices) {
        for(int i=1;i+1<vertices.size();i++) {
            var a=point(vertices.getFirst());var b=point(vertices.get(i));var c=point(vertices.get(i+1));
            if(b.sub(a).cross(c.sub(a)).lengthSquared()<1e-10f)continue;
            output.add(new BedrockGeometryMesh.Quad(source.bone(),source.material(),
                List.of(vertices.getFirst(),vertices.get(i),vertices.get(i+1),vertices.get(i+1)),source.nx(),source.ny(),source.nz()));
        }
    }
    private static Vector3f point(BedrockGeometryMesh.Vertex v){return new Vector3f(v.x(),v.y(),v.z());}
    private static double area(BedrockGeometryMesh.Quad q) {
        return point(q.vertices().get(1)).sub(point(q.vertices().getFirst()))
            .cross(point(q.vertices().get(2)).sub(point(q.vertices().getFirst()))).lengthSquared();
    }
    private static List<Plane> planes(BedrockGeometryMesh.Quad aperture,Vector3f camera) {
        var vertices=aperture.vertices();var center=new Vector3f();vertices.forEach(v->center.add(point(v)));center.div(vertices.size());
        var normal=point(vertices.get(1)).sub(point(vertices.getFirst())).cross(point(vertices.get(2)).sub(point(vertices.getFirst())));
        if(normal.lengthSquared()<1e-10f)return List.of();normal.normalize();
        float side=new Vector3f(camera).sub(center).dot(normal);
        if(Math.abs(side)<1e-4f)return List.of();
        // Keep only geometry beyond the opening, not objects between the eye and mask.
        if(side>0)normal.negate();
        var planes=new ArrayList<Plane>();planes.add(new Plane(normal,-normal.dot(center)));
        for(int i=0;i<vertices.size();i++) {
            var a=point(vertices.get(i));var b=point(vertices.get((i+1)%vertices.size()));
            var edge=new Vector3f(a).sub(camera).cross(new Vector3f(b).sub(camera));
            if(edge.lengthSquared()<1e-10f)continue;edge.normalize();
            if(edge.dot(new Vector3f(center).sub(camera))<0)edge.negate();
            planes.add(new Plane(edge,-edge.dot(camera)));
        }
        return planes;
    }
    private static BedrockGeometryMesh.Vertex lerp(BedrockGeometryMesh.Vertex a,BedrockGeometryMesh.Vertex b,float t) {
        BedrockGeometry.Vec normal=null;
        if(a.normal()!=null&&b.normal()!=null)normal=new BedrockGeometry.Vec(
            a.normal().x()+(b.normal().x()-a.normal().x())*t,a.normal().y()+(b.normal().y()-a.normal().y())*t,
            a.normal().z()+(b.normal().z()-a.normal().z())*t);
        return new BedrockGeometryMesh.Vertex(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t,
            a.u()+(b.u()-a.u())*t,a.v()+(b.v()-a.v())*t,normal);
    }
}
