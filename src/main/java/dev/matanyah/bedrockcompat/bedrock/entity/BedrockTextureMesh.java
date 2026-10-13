package dev.matanyah.bedrockcompat.bedrock.entity;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;
import java.util.function.IntBinaryOperator;

/** Value-only texture voxelizer. Image acquisition and caching belong to the resource consumer. */
public final class BedrockTextureMesh {
    private BedrockTextureMesh() {}
    public static List<BedrockGeometry.Polygon> polygons(BedrockGeometry.TextureMesh mesh,int width,int height,
            double logicalWidth,double logicalHeight,IntBinaryOperator argb) {
        return polygons(mesh,width,height,logicalWidth,logicalHeight,0,argb);
    }
    public static List<BedrockGeometry.Polygon> polygons(BedrockGeometry.TextureMesh mesh,int width,int height,
            double logicalWidth,double logicalHeight,double bonePivotY,IntBinaryOperator argb) {
        if(width<=0||height<=0||(long)width*height>262144)throw new IllegalArgumentException("Texture mesh exceeds pixel budget");
        double sx=logicalWidth/width,sz=logicalHeight/height,depth=mesh.pixelDepth()?sx:1;
        boolean[] opaque=new boolean[width*height];int pixels=0;
        for(int z=0;z<height;z++)for(int x=0;x<width;x++)if((argb.applyAsInt(x,z)>>>24)!=0){opaque[z*width+x]=true;pixels++;}
        if(pixels>65536)throw new IllegalArgumentException("Texture mesh exceeds voxel budget");
        var p=mesh.position();var r=mesh.rotation();var scale=mesh.scale();var pivot=mesh.localPivot();
        // Texture geometry uses downward Y offsets from the bone pivot, unlike
        // cube origins. Texels extrude below the XZ plane; local_pivot precedes rotation.
        var transform=new Matrix4f().translate((float)p.x(),(float)(bonePivotY-p.y()),(float)p.z())
            .rotateZYX((float)Math.toRadians(-r.z()),(float)Math.toRadians(r.y()),(float)Math.toRadians(-r.x()))
            .scale((float)scale.x(),(float)scale.y(),(float)scale.z())
            .translate((float)-pivot.x(),(float)pivot.y(),(float)-pivot.z())
            .scale(1,-1,1);
        List<BedrockGeometry.Polygon> result=new ArrayList<>();
        for(int z=0;z<height;z++)for(int x=0;x<width;x++)if(opaque[z*width+x]) {
            double x0=x*sx,x1=(x+1)*sx,z0=z*sz,z1=(z+1)*sz;
            double u=(x+.5)/width,v=(z+.5)/height;
            // All six voxel faces sample this texel's centre; adjacent solid pixels
            // share no internal faces. Alpha holes retain their side walls.
            face(result,transform,u,v,0,1,0,new double[][]{{x0,depth,z1},{x1,depth,z1},{x1,depth,z0},{x0,depth,z0}});
            face(result,transform,u,v,0,-1,0,new double[][]{{x0,0,z0},{x1,0,z0},{x1,0,z1},{x0,0,z1}});
            if(x==0||!opaque[z*width+x-1])face(result,transform,u,v,-1,0,0,new double[][]{{x0,0,z1},{x0,depth,z1},{x0,depth,z0},{x0,0,z0}});
            if(x==width-1||!opaque[z*width+x+1])face(result,transform,u,v,1,0,0,new double[][]{{x1,0,z0},{x1,depth,z0},{x1,depth,z1},{x1,0,z1}});
            if(z==0||!opaque[(z-1)*width+x])face(result,transform,u,v,0,0,-1,new double[][]{{x0,0,z0},{x0,depth,z0},{x1,depth,z0},{x1,0,z0}});
            if(z==height-1||!opaque[(z+1)*width+x])face(result,transform,u,v,0,0,1,new double[][]{{x1,0,z1},{x1,depth,z1},{x0,depth,z1},{x0,0,z1}});
        }
        return List.copyOf(result);
    }
    private static void face(List<BedrockGeometry.Polygon> output,Matrix4f transform,double u,double v,
            float nx,float ny,float nz,double[][] points) {
        var normal=transform.transformDirection(new Vector3f(nx,ny,nz));
        if(normal.lengthSquared()!=0)normal.normalize();
        List<BedrockGeometry.MeshVertex> vertices=new ArrayList<>();
        for(var point:points) {
            var position=transform.transformPosition(new Vector3f((float)point[0],(float)point[1],(float)point[2]));
            vertices.add(new BedrockGeometry.MeshVertex(new BedrockGeometry.Vec(position.x,position.y,position.z),
                new BedrockGeometry.Vec(normal.x,normal.y,normal.z),u,v));
        }
        if(transform.determinant3x3()<0)Collections.reverse(vertices);
        output.add(new BedrockGeometry.Polygon(List.copyOf(vertices)));
    }
    public static BedrockGeometry layer(BedrockGeometry geometry,String bone,List<BedrockGeometry.Polygon> polygons) {
        return new BedrockGeometry(geometry.textureWidth(),geometry.textureHeight(),geometry.bones().stream().map(b->
            new BedrockGeometry.Bone(b.name(),b.parent(),b.pivot(),b.rotation(),List.of(),b.locators(),
                b.name().equals(bone)?polygons:List.of(),b.neverRender(),b.binding())).toList());
    }
}
