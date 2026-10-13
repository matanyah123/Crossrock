package dev.matanyah.bedrockcompat.bedrock.entity;

import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import java.util.*;

/** Immutable per-frame mesh in Bedrock model units. No retained mutable entity or world state. */
public final class BedrockGeometryMesh {
    public record Vertex(float x, float y, float z, float u, float v, BedrockGeometry.Vec normal) {
        public Vertex(float x, float y, float z, float u, float v) { this(x,y,z,u,v,null); }
    }
    public record Quad(String bone, String material, List<Vertex> vertices, float nx, float ny, float nz,int cubeIndex,String face) {
        public Quad(String bone,String material,List<Vertex> vertices,float nx,float ny,float nz){this(bone,material,vertices,nx,ny,nz,-1,"");}
    }
    private BedrockGeometryMesh() {}

    /** A camera pass lacks the living renderer's 180-degree facing transform. */
    public static List<Quad> cameraFacing(List<Quad> mesh) {
        return mesh.stream().map(q->new Quad(q.bone,q.material,q.vertices.stream().map(v->{
            var n=v.normal;
            return new Vertex(-v.x,v.y,-v.z,v.u,v.v,n==null?null:new BedrockGeometry.Vec(-n.x(),n.y(),-n.z()));
        }).toList(),-q.nx,q.ny,-q.nz,q.cubeIndex,q.face)).toList();
    }

    /** Bedrock camera-space depth faces +Z; Java's hand projection looks along -Z.
     * A reflection also reverses winding, otherwise culled materials disappear. */
    public static List<Quad> reflectDepth(List<Quad> mesh) {
        List<Quad> result=new ArrayList<>(mesh.size());
        for(var quad:mesh) {
            List<Vertex> vertices=new ArrayList<>(quad.vertices.size());
            for(int i=quad.vertices.size()-1;i>=0;i--) {
                var v=quad.vertices.get(i);
                var n=v.normal;
                vertices.add(new Vertex(v.x,v.y,-v.z,v.u,v.v,
                    n==null?null:new BedrockGeometry.Vec(n.x(),n.y(),-n.z())));
            }
            result.add(new Quad(quad.bone,quad.material,List.copyOf(vertices),quad.nx,quad.ny,-quad.nz,quad.cubeIndex,quad.face));
        }
        return List.copyOf(result);
    }

    public static List<Quad> bake(BedrockGeometry geometry, Map<String,BedrockAnimation.Pose> poses,
                                  List<BedrockRenderController.Visibility> visibility) {
        return bake(geometry,poses,visibility,Map.of());
    }
    /** Resolved root bindings are holder anchors in Bedrock model space, never Java matrices. */
    public static List<Quad> bake(BedrockGeometry geometry, Map<String,BedrockAnimation.Pose> poses,
                                  List<BedrockRenderController.Visibility> visibility,Map<String,Matrix4f> bindings) {
        Map<String,Matrix4f> transforms=transforms(geometry,poses,bindings);
        List<Quad> out = new ArrayList<>();
        for (var bone : geometry.bones()) {
            // Hidden bindings/zero animation scales collapse the complete descendant branch.
            // Avoid inverse normal matrices and NaNs for that intentionally invisible geometry.
            if(!bindings.isEmpty()&&Math.abs(transforms.get(bone.name()).determinant3x3())<1e-12f)continue;
            boolean visible = true;
            for (var rule : visibility) if (matches(rule.bonePattern(),bone.name())) visible = rule.visible();
            if (!visible || bone.neverRender()) continue;
            for (int cubeIndex=0;cubeIndex<bone.cubes().size();cubeIndex++) {
                var cube=bone.cubes().get(cubeIndex);
                Matrix4f transform = new Matrix4f(transforms.get(bone.name()));
                var pivot = cube.pivot();
                transform.translate((float)pivot.x(),(float)pivot.y(),(float)pivot.z());
                rotate(transform,cube.rotation());
                transform.translate((float)-pivot.x(),(float)-pivot.y(),(float)-pivot.z());
                bakeCube(geometry,bone,cube,transform,out,cubeIndex);
            }
            bakePolygons(bone,transforms.get(bone.name()),out);
        }
        return List.copyOf(out);
    }

    /** Bone pivots and named locators share exactly the model's animated hierarchy. */
    public static Map<String,Matrix4f> anchors(BedrockGeometry geometry,Map<String,BedrockAnimation.Pose> poses) {
        return anchors(geometry,poses,Map.of());
    }
    public static Map<String,Matrix4f> anchors(BedrockGeometry geometry,Map<String,BedrockAnimation.Pose> poses,Map<String,Matrix4f> bindings) {
        var transforms=transforms(geometry,poses,bindings);Map<String,Matrix4f> result=new LinkedHashMap<>();
        for(var bone:geometry.bones()) {
            var matrix=transforms.get(bone.name());var pivot=bone.pivot();
            result.put(bone.name(),new Matrix4f(matrix).translate((float)pivot.x(),(float)pivot.y(),(float)pivot.z()));
            bone.locators().forEach((name,locator)-> {
                Matrix4f anchor=new Matrix4f(matrix);
                if(locator.ignoreInheritedScale())anchor.normalize3x3();
                var point=locator.offset();anchor.translate((float)point.x(),(float)point.y(),(float)point.z());
                rotate(anchor,locator.rotation());result.put(name,anchor);
            });
        }
        return Map.copyOf(result);
    }

    private static Map<String,Matrix4f> transforms(BedrockGeometry geometry,Map<String,BedrockAnimation.Pose> poses) {
        return transforms(geometry,poses,Map.of());
    }
    private static Map<String,Matrix4f> transforms(BedrockGeometry geometry,Map<String,BedrockAnimation.Pose> poses,
                                                   Map<String,Matrix4f> bindings) {
        Map<String,BedrockGeometry.Bone> bones = new LinkedHashMap<>();
        geometry.bones().forEach(b -> bones.put(b.name(),b));
        Map<String,Matrix4f> transforms = new HashMap<>();
        // Iterative traversal: arbitrary ordering and long parent chains cannot overflow the stack.
        for (var bone : geometry.bones()) {
            Deque<BedrockGeometry.Bone> chain = new ArrayDeque<>();
            var current = bone;
            while (current != null && !transforms.containsKey(current.name())) {
                chain.push(current);
                current = current.parent() == null ? null : bones.get(current.parent());
            }
            while (!chain.isEmpty()) {
                var next = chain.pop();
                Matrix4f matrix = next.parent() == null ? new Matrix4f() : new Matrix4f(transforms.get(next.parent()));
                var pivot = next.pivot();
                if(bindings.containsKey(next.name()))matrix=new Matrix4f(bindings.get(next.name()))
                    .translate((float)-pivot.x(),(float)-pivot.y(),(float)-pivot.z());
                var pose = poses.get(next.name());
                if (pose != null) matrix.translate((float)pose.position().x(),(float)pose.position().y(),(float)pose.position().z());
                matrix.translate((float)pivot.x(),(float)pivot.y(),(float)pivot.z());
                if(pose!=null&&pose.entityRelativeRotation()) {
                    // Keep the inherited pivot position and scale, but not the parent's orientation.
                    var scale=matrix.getScale(new Vector3f());
                    matrix.m00(scale.x).m01(0).m02(0)
                        .m10(0).m11(scale.y).m12(0).m20(0).m21(0).m22(scale.z);
                }
                var rest=next.rotation();
                // Bedrock sums Euler rotation channels before building the bone matrix.
                // Multiplying separate rest/animation matrices changes multi-axis rotations.
                rotate(matrix,pose==null?rest:new BedrockGeometry.Vec(rest.x()+pose.rotation().x(),
                    rest.y()+pose.rotation().y(),rest.z()+pose.rotation().z()));
                if (pose != null) {
                    matrix.scale((float)pose.scale().x(),(float)pose.scale().y(),(float)pose.scale().z());
                }
                matrix.translate((float)-pivot.x(),(float)-pivot.y(),(float)-pivot.z());
                transforms.put(next.name(),matrix);
            }
        }
        return transforms;
    }
    private static void bakePolygons(BedrockGeometry.Bone bone, Matrix4f transform, List<Quad> out) {
        if (bone.polygons().isEmpty()) return;
        Matrix3f normals = transform.normal(new Matrix3f());
        boolean reflected = transform.determinant3x3() < 0;
        for (var polygon : bone.polygons()) {
            List<Vector3f> points = new ArrayList<>(4);
            for (var corner : polygon.vertices()) {
                var p = corner.position();
                Vector3f point = transform.transformPosition(new Vector3f((float)p.x(),(float)p.y(),(float)p.z()));
                if (!point.isFinite()) throw new IllegalArgumentException("Non-finite bone transform: " + bone.name());
                points.add(point);
            }
            Vector3f flat = new Vector3f(points.get(1)).sub(points.get(0))
                .cross(new Vector3f(points.get(2)).sub(points.get(0)));
            if (flat.lengthSquared() < 1e-12f && points.size() == 4)
                flat.set(points.get(2)).sub(points.get(0)).cross(new Vector3f(points.get(3)).sub(points.get(0)));
            if (flat.lengthSquared() < 1e-12f) continue;
            flat.normalize();
            if (reflected) flat.negate();
            List<Vertex> vertices = new ArrayList<>(4);
            for (int i=0;i<points.size();i++) {
                var corner=polygon.vertices().get(i);var p=points.get(i);var n=corner.normal();
                Vector3f normal=normals.transform(new Vector3f((float)n.x(),(float)n.y(),(float)n.z()));
                // Zero scale can make the inverse transpose singular; never submit NaN lighting.
                if (!normal.isFinite() || normal.lengthSquared()<1e-12f) normal.set(flat);
                else normal.normalize();
                vertices.add(new Vertex(p.x,p.y,p.z,(float)corner.u(),(float)corner.v(),
                    new BedrockGeometry.Vec(normal.x,normal.y,normal.z)));
            }
            if (reflected) Collections.reverse(vertices);
            // Minecraft submits QUADS: duplicate the last corner to encode one triangle.
            if (vertices.size()==3) vertices.add(vertices.getLast());
            out.add(new Quad(bone.name(),"*",List.copyOf(vertices),flat.x,flat.y,flat.z));
        }
    }
    private static void bakeCube(BedrockGeometry geometry,BedrockGeometry.Bone bone,BedrockGeometry.Cube cube,
                                 Matrix4f transform,List<Quad> out,int cubeIndex) {
                double x0 = cube.origin().x()-cube.inflate(), y0 = cube.origin().y()-cube.inflate(), z0 = cube.origin().z()-cube.inflate();
                double x1 = cube.origin().x()+cube.size().x()+cube.inflate();
                double y1 = cube.origin().y()+cube.size().y()+cube.inflate();
                double z1 = cube.origin().z()+cube.size().z()+cube.inflate();
                for (String face : List.of("north","south","east","west","up","down")) {
                    String uvFace = cube.mirror() ? switch(face){case "east"->"west";case "west"->"east";default->face;} : face;
                    var uv = cube.faces().get(uvFace);
                    if (uv == null) continue;
                    // UV corners; reverse the final vertex order below for outward winding.
                    double[][] points = switch (face) {
                        case "north" -> new double[][]{{x0,y1,z0},{x0,y0,z0},{x1,y0,z0},{x1,y1,z0}};
                        case "south" -> new double[][]{{x1,y1,z1},{x1,y0,z1},{x0,y0,z1},{x0,y1,z1}};
                        case "east" -> new double[][]{{x1,y1,z0},{x1,y0,z0},{x1,y0,z1},{x1,y1,z1}};
                        case "west" -> new double[][]{{x0,y1,z1},{x0,y0,z1},{x0,y0,z0},{x0,y1,z0}};
                        case "up" -> new double[][]{{x0,y1,z1},{x0,y1,z0},{x1,y1,z0},{x1,y1,z1}};
                        default -> new double[][]{{x0,y0,z0},{x0,y0,z1},{x1,y0,z1},{x1,y0,z0}};
                    };
                    List<Vertex> vertices = new ArrayList<>(4);
                    List<Vector3f> transformed = new ArrayList<>(4);
                    for (int i=0;i<4;i++) {
                        var p = points[i];
                        Vector3f v = transform.transformPosition(new Vector3f((float)p[0],(float)p[1],(float)p[2]));
                        if (!v.isFinite()) throw new IllegalArgumentException("Non-finite bone transform: " + bone.name());
                        double s = i >= 2 ? 1 : 0, t = i == 1 || i == 2 ? 1 : 0;
                        double[] mapped = uv.at(cube.mirror() ? 1-s : s,t);
                        vertices.add(new Vertex(v.x,v.y,v.z,(float)(mapped[0]/geometry.textureWidth()),(float)(mapped[1]/geometry.textureHeight())));
                        transformed.add(v);
                    }
                    Vector3f normal = new Vector3f(transformed.get(1)).sub(transformed.get(0))
                        .cross(new Vector3f(transformed.get(2)).sub(transformed.get(0)));
                    if (normal.lengthSquared() < 1e-12f) continue;
                    // Negative bone scale reflects winding, but must not turn a shell inside out.
                    boolean reflected=transform.determinant3x3()<0;
                    normal.normalize().mul(reflected?1:-1);
                    if(!reflected)Collections.reverse(vertices);
                    out.add(new Quad(bone.name(),uv.material(),List.copyOf(vertices),normal.x,normal.y,normal.z,cubeIndex,face));
                }
    }

    private static void rotate(Matrix4f matrix, BedrockGeometry.Vec rotation) {
        // Bedrock's Euler angles are not right-handed JOML angles. Geometry stays in
        // original Bedrock coordinates here: X and Z rotate in the opposite direction.
        matrix.rotateZYX((float)Math.toRadians(-rotation.z()),(float)Math.toRadians(rotation.y()),(float)Math.toRadians(-rotation.x()));
    }
    /** Wildcards without compiling untrusted patterns as regular expressions. */
    public static boolean matches(String pattern, String value) {
        int p=0,v=0,star=-1,mark=0;
        while(v<value.length()) {
            if(p<pattern.length() && pattern.charAt(p)==value.charAt(v)){p++;v++;}
            else if(p<pattern.length() && pattern.charAt(p)=='*'){star=p++;mark=v;}
            else if(star>=0){p=star+1;v=++mark;}
            else return false;
        }
        while(p<pattern.length() && pattern.charAt(p)=='*')p++;
        return p==pattern.length();
    }
}
