package org.xiaojian999.superpowers.math;

import net.minecraft.util.math.Vec3d;

/**
 * Quaternion utilities: axis-angle, euler, slerp, rotate.
 * Stored as double[4] = [x,y,z,w].
 */
public final class FastQuaternion {
    private FastQuaternion() {}

    public static double[] identity() { return new double[]{0,0,0,1}; }
    public static double[] fromAxisAngle(Vec3d axis, double rad) {
        Vec3d n = GlungFastMath.normalize(axis);
        double s = Math.sin(rad*0.5);
        return new double[]{n.x*s, n.y*s, n.z*s, Math.cos(rad*0.5)};
    }
    public static double[] fromAxisAngle(GlungVec3 axis, double rad) {
        GlungVec3 n = axis.normalize();
        double s = Math.sin(rad*0.5);
        return new double[]{n.x()*s, n.y()*s, n.z()*s, Math.cos(rad*0.5)};
    }
    public static double[] fromEuler(double pitch, double yaw, double roll) {
        double cy=Math.cos(yaw*0.5), sy=Math.sin(yaw*0.5);
        double cp=Math.cos(pitch*0.5), sp=Math.sin(pitch*0.5);
        double cr=Math.cos(roll*0.5), sr=Math.sin(roll*0.5);
        return new double[]{
                sr*cp*cy - cr*sp*sy,
                cr*sp*cy + sr*cp*sy,
                cr*cp*sy - sr*sp*cy,
                cr*cp*cy + sr*sp*sy
        };
    }
    public static double[] multiply(double[] a, double[] b) {
        return new double[]{
                a[3]*b[0]+a[0]*b[3]+a[1]*b[2]-a[2]*b[1],
                a[3]*b[1]-a[0]*b[2]+a[1]*b[3]+a[2]*b[0],
                a[3]*b[2]+a[0]*b[1]-a[1]*b[0]+a[2]*b[3],
                a[3]*b[3]-a[0]*b[0]-a[1]*b[1]-a[2]*b[2]
        };
    }
    public static double[] conjugate(double[] q){ return new double[]{-q[0],-q[1],-q[2],q[3]}; }
    public static double[] normalize(double[] q){
        double len=Math.sqrt(q[0]*q[0]+q[1]*q[1]+q[2]*q[2]+q[3]*q[3]);
        if(len< GlungFastMath.EPSILON) return identity();
        double inv=1/len;
        return new double[]{q[0]*inv,q[1]*inv,q[2]*inv,q[3]*inv};
    }
    public static double dot(double[] a, double[] b){ return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]+a[3]*b[3]; }
    public static double[] slerp(double[] a, double[] b, double t){
        double d=dot(a,b);
        double[] b2=b;
        if(d<0){ d=-d; b2=new double[]{-b[0],-b[1],-b[2],-b[3]}; }
        if(d>0.9995) return normalize(new double[]{GlungFastMath.lerp(a[0],b2[0],t),GlungFastMath.lerp(a[1],b2[1],t),GlungFastMath.lerp(a[2],b2[2],t),GlungFastMath.lerp(a[3],b2[3],t)});
        double th0=Math.acos(d);
        double th=th0*t;
        double st=Math.sin(th), st0=Math.sin(th0);
        double s0=Math.cos(th)-d*st/st0;
        double s1=st/st0;
        return new double[]{a[0]*s0+b2[0]*s1,a[1]*s0+b2[1]*s1,a[2]*s0+b2[2]*s1,a[3]*s0+b2[3]*s1};
    }
    public static double[] nlerp(double[] a, double[] b, double t){
        return normalize(new double[]{GlungFastMath.lerp(a[0],b[0],t),GlungFastMath.lerp(a[1],b[1],t),GlungFastMath.lerp(a[2],b[2],t),GlungFastMath.lerp(a[3],b[3],t)});
    }
    public static Vec3d rotate(double[] q, Vec3d v){
        double[] qv=new double[]{v.x,v.y,v.z,0};
        double[] qc=conjugate(q);
        double[] tmp=multiply(q,qv);
        double[] res=multiply(tmp,qc);
        return new Vec3d(res[0],res[1],res[2]);
    }
    public static GlungVec3 rotate(double[] q, GlungVec3 v){
        return GlungVec3.fromVec3d(rotate(q,v.toVec3d()));
    }
    public static double[] fromTwoVectors(Vec3d from, Vec3d to){
        Vec3d f=GlungFastMath.normalize(from);
        Vec3d t=GlungFastMath.normalize(to);
        double d= f.dotProduct(t);
        if(d<-0.9999){
            Vec3d axis = Math.abs(f.x)<0.9 ? new Vec3d(1,0,0).crossProduct(f) : new Vec3d(0,1,0).crossProduct(f);
            axis = GlungFastMath.normalize(axis);
            return fromAxisAngle(axis, Math.PI);
        }
        Vec3d c=f.crossProduct(t);
        double s=Math.sqrt((1+d)*2);
        double inv=1/s;
        return normalize(new double[]{c.x*inv,c.y*inv,c.z*inv,s*0.5});
    }
    public static double angle(double[] q){
        return 2*Math.acos(GlungFastMath.clamp(q[3],-1,1));
    }
    public static Vec3d axis(double[] q){
        double s=Math.sqrt(1-q[3]*q[3]);
        if(s< GlungFastMath.EPSILON) return new Vec3d(1,0,0);
        return new Vec3d(q[0]/s,q[1]/s,q[2]/s);
    }
}
