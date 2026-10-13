package dev.matanyah.bedrockcompat.bedrock.resource;

import dev.matanyah.bedrockcompat.bedrock.entity.BedrockGeometryMesh;
import org.joml.*;
import java.util.*;
import java.lang.Math;

/** Keeps axis-aligned face UV orientation fixed under block quarter-turn transformations. */
final class BedrockUvLock {
    static List<BedrockGeometryMesh.Vertex> lock(BedrockGeometryMesh.Quad original,List<BedrockGeometryMesh.Vertex> transformed,Matrix4f matrix) {
        if(Math.abs(matrix.determinant3x3())<1e-12)return transformed;
        Vector3f n=new Vector3f(original.nx(),original.ny(),original.nz());
        Vector3f rotated=matrix.normal(new Matrix3f()).transform(new Vector3f(n)).normalize();
        int oldFace=face(n),newFace=face(rotated);
        Vector3f ru=matrix.transformDirection(u(oldFace)).normalize(),rv=matrix.transformDirection(v(oldFace)).normalize();
        Vector3f targetU=u(newFace),targetV=v(newFace);
        float a=targetU.dot(ru),b=targetU.dot(rv),c=targetV.dot(ru),d=targetV.dot(rv);
        float minU=Float.POSITIVE_INFINITY,maxU=Float.NEGATIVE_INFINITY,minV=minU,maxV=maxU;
        for(var vertex:transformed){minU=Math.min(minU,vertex.u());maxU=Math.max(maxU,vertex.u());minV=Math.min(minV,vertex.v());maxV=Math.max(maxV,vertex.v());}
        float width=maxU-minU,height=maxV-minV;if(width==0||height==0)return transformed;
        List<BedrockGeometryMesh.Vertex> result=new ArrayList<>();
        for(var vertex:transformed) {
            float x=(vertex.u()-minU)/width-.5f,y=(vertex.v()-minV)/height-.5f;
            result.add(new BedrockGeometryMesh.Vertex(vertex.x(),vertex.y(),vertex.z(),minU+(a*x+b*y+.5f)*width,minV+(c*x+d*y+.5f)*height));
        }
        return result;
    }
    private static int face(Vector3f n) {
        if(Math.abs(n.x)>.9999)return n.x>0?0:1;if(Math.abs(n.y)>.9999)return n.y>0?2:3;
        if(Math.abs(n.z)>.9999)return n.z>0?4:5;
        throw new IllegalArgumentException("UV locking oblique source faces is not defined by this adapter");
    }
    private static Vector3f u(int face){return switch(face){case 0->new Vector3f(0,0,1);case 1->new Vector3f(0,0,-1);case 4->new Vector3f(-1,0,0);default->new Vector3f(1,0,0);};}
    private static Vector3f v(int face){return switch(face){case 2->new Vector3f(0,0,-1);case 3->new Vector3f(0,0,1);default->new Vector3f(0,-1,0);};}
}
