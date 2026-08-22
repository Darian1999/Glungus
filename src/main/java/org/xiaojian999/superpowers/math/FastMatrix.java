package org.xiaojian999.superpowers.math;

import net.minecraft.util.math.Vec3d;

/**
 * Fast 3x3 and 4x4 matrix ops. Row-major double arrays.
 * No allocation in multiply if you pass out arrays (overloads provided).
 */
public final class FastMatrix {
    private FastMatrix() {}

    // ------------------- 4x4 -------------------
    public static double[] identity4() {
        return new double[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
    }
    public static double[] translate4(double x, double y, double z) {
        double[] m = identity4(); m[3]=x; m[7]=y; m[11]=z; return m;
    }
    public static double[] scale4(double x, double y, double z) {
        double[] m = identity4(); m[0]=x; m[5]=y; m[10]=z; return m;
    }
    public static double[] rotateX4(double rad) {
        double c = GlungFastMath.fastCos(rad), s = GlungFastMath.fastSin(rad);
        return new double[]{1,0,0,0, 0,c,-s,0, 0,s,c,0, 0,0,0,1};
    }
    public static double[] rotateY4(double rad) {
        double c = GlungFastMath.fastCos(rad), s = GlungFastMath.fastSin(rad);
        return new double[]{c,0,s,0, 0,1,0,0, -s,0,c,0, 0,0,0,1};
    }
    public static double[] rotateZ4(double rad) {
        double c = GlungFastMath.fastCos(rad), s = GlungFastMath.fastSin(rad);
        return new double[]{c,-s,0,0, s,c,0,0, 0,0,1,0, 0,0,0,1};
    }
    public static double[] multiply4(double[] a, double[] b) {
        double[] r = new double[16];
        multiply4(a,b,r);
        return r;
    }
    public static void multiply4(double[] a, double[] b, double[] out) {
        // Unrolled 4x4 multiply - avoids triple nested loops and enables better pipelining
        double a00 = a[0], a01 = a[1], a02 = a[2], a03 = a[3];
        double a10 = a[4], a11 = a[5], a12 = a[6], a13 = a[7];
        double a20 = a[8], a21 = a[9], a22 = a[10], a23 = a[11];
        double a30 = a[12], a31 = a[13], a32 = a[14], a33 = a[15];
        double b00 = b[0], b01 = b[1], b02 = b[2], b03 = b[3];
        double b10 = b[4], b11 = b[5], b12 = b[6], b13 = b[7];
        double b20 = b[8], b21 = b[9], b22 = b[10], b23 = b[11];
        double b30 = b[12], b31 = b[13], b32 = b[14], b33 = b[15];
        out[0]  = a00 * b00 + a01 * b10 + a02 * b20 + a03 * b30;
        out[1]  = a00 * b01 + a01 * b11 + a02 * b21 + a03 * b31;
        out[2]  = a00 * b02 + a01 * b12 + a02 * b22 + a03 * b32;
        out[3]  = a00 * b03 + a01 * b13 + a02 * b23 + a03 * b33;
        out[4]  = a10 * b00 + a11 * b10 + a12 * b20 + a13 * b30;
        out[5]  = a10 * b01 + a11 * b11 + a12 * b21 + a13 * b31;
        out[6]  = a10 * b02 + a11 * b12 + a12 * b22 + a13 * b32;
        out[7]  = a10 * b03 + a11 * b13 + a12 * b23 + a13 * b33;
        out[8]  = a20 * b00 + a21 * b10 + a22 * b20 + a23 * b30;
        out[9]  = a20 * b01 + a21 * b11 + a22 * b21 + a23 * b31;
        out[10] = a20 * b02 + a21 * b12 + a22 * b22 + a23 * b32;
        out[11] = a20 * b03 + a21 * b13 + a22 * b23 + a23 * b33;
        out[12] = a30 * b00 + a31 * b10 + a32 * b20 + a33 * b30;
        out[13] = a30 * b01 + a31 * b11 + a32 * b21 + a33 * b31;
        out[14] = a30 * b02 + a31 * b12 + a32 * b22 + a33 * b32;
        out[15] = a30 * b03 + a31 * b13 + a32 * b23 + a33 * b33;
    }
    public static Vec3d transformPos4(double[] m, Vec3d v) {
        double x = m[0]*v.x + m[1]*v.y + m[2]*v.z + m[3];
        double y = m[4]*v.x + m[5]*v.y + m[6]*v.z + m[7];
        double z = m[8]*v.x + m[9]*v.y + m[10]*v.z + m[11];
        double w = m[12]*v.x + m[13]*v.y + m[14]*v.z + m[15];
        if (Math.abs(w) > 1e-9 && w != 1) { x/=w; y/=w; z/=w; }
        return new Vec3d(x,y,z);
    }
    public static Vec3d transformDir4(double[] m, Vec3d v) {
        return new Vec3d(m[0]*v.x + m[1]*v.y + m[2]*v.z,
                m[4]*v.x + m[5]*v.y + m[6]*v.z,
                m[8]*v.x + m[9]*v.y + m[10]*v.z);
    }
    public static void transpose4(double[] m, double[] out) {
        out[0] = m[0]; out[1] = m[4]; out[2] = m[8]; out[3] = m[12];
        out[4] = m[1]; out[5] = m[5]; out[6] = m[9]; out[7] = m[13];
        out[8] = m[2]; out[9] = m[6]; out[10] = m[10]; out[11] = m[14];
        out[12] = m[3]; out[13] = m[7]; out[14] = m[11]; out[15] = m[15];
    }
    public static double[] transpose4(double[] m) {
        double[] t = new double[16];
        transpose4(m, t);
        return t;
    }
    public static double determinant4(double[] m) {
        // Laplace expansion (can be optimized but rarely hot)
        return m[0]*det3(m[5],m[6],m[7], m[9],m[10],m[11], m[13],m[14],m[15])
                - m[1]*det3(m[4],m[6],m[7], m[8],m[10],m[11], m[12],m[14],m[15])
                + m[2]*det3(m[4],m[5],m[7], m[8],m[9],m[11],  m[12],m[13],m[15])
                - m[3]*det3(m[4],m[5],m[6], m[8],m[9],m[10], m[12],m[13],m[14]);
    }
    private static double det3(double a1,double a2,double a3,double b1,double b2,double b3,double c1,double c2,double c3){
        return a1*(b2*c3-b3*c2)-a2*(b1*c3-b3*c1)+a3*(b1*c2-b2*c1);
    }

    // ------------------- 3x3 -------------------
    public static double[] identity3() { return new double[]{1,0,0, 0,1,0, 0,0,1}; }
    public static double determinant3(double[] m){
        return m[0]*(m[4]*m[8]-m[5]*m[7]) - m[1]*(m[3]*m[8]-m[5]*m[6]) + m[2]*(m[3]*m[7]-m[4]*m[6]);
    }
    public static double[] inverse3(double[] m){
        double det = determinant3(m);
        if (Math.abs(det) < GlungFastMath.EPSILON) return null;
        double inv = 1.0/det;
        double[] r=new double[9];
        r[0]=(m[4]*m[8]-m[5]*m[7])*inv;
        r[1]=(m[2]*m[7]-m[1]*m[8])*inv;
        r[2]=(m[1]*m[5]-m[2]*m[4])*inv;
        r[3]=(m[5]*m[6]-m[3]*m[8])*inv;
        r[4]=(m[0]*m[8]-m[2]*m[6])*inv;
        r[5]=(m[2]*m[3]-m[0]*m[5])*inv;
        r[6]=(m[3]*m[7]-m[4]*m[6])*inv;
        r[7]=(m[1]*m[6]-m[0]*m[7])*inv;
        r[8]=(m[0]*m[4]-m[1]*m[3])*inv;
        return r;
    }
    public static void multiply3(double[] a, double[] b, double[] out){
        double a00 = a[0], a01 = a[1], a02 = a[2];
        double a10 = a[3], a11 = a[4], a12 = a[5];
        double a20 = a[6], a21 = a[7], a22 = a[8];
        double b00 = b[0], b01 = b[1], b02 = b[2];
        double b10 = b[3], b11 = b[4], b12 = b[5];
        double b20 = b[6], b21 = b[7], b22 = b[8];
        out[0] = a00 * b00 + a01 * b10 + a02 * b20;
        out[1] = a00 * b01 + a01 * b11 + a02 * b21;
        out[2] = a00 * b02 + a01 * b12 + a02 * b22;
        out[3] = a10 * b00 + a11 * b10 + a12 * b20;
        out[4] = a10 * b01 + a11 * b11 + a12 * b21;
        out[5] = a10 * b02 + a11 * b12 + a12 * b22;
        out[6] = a20 * b00 + a21 * b10 + a22 * b20;
        out[7] = a20 * b01 + a21 * b11 + a22 * b21;
        out[8] = a20 * b02 + a21 * b12 + a22 * b22;
    }
    public static double[] multiply3(double[] a, double[] b){
        double[] r=new double[9];
        multiply3(a, b, r);
        return r;
    }
    public static Vec3d transform3(double[] m, Vec3d v){
        return new Vec3d(m[0]*v.x+m[1]*v.y+m[2]*v.z, m[3]*v.x+m[4]*v.y+m[5]*v.z, m[6]*v.x+m[7]*v.y+m[8]*v.z);
    }
    public static double[] transpose3(double[] m){
        return new double[]{m[0],m[3],m[6], m[1],m[4],m[7], m[2],m[5],m[8]};
    }
    public static double[] fromQuaternion(double[] q){
        double x=q[0], y=q[1], z=q[2], w=q[3];
        double x2=x+x, y2=y+y, z2=z+z;
        double xx=x*x2, xy=x*y2, xz=x*z2;
        double yy=y*y2, yz=y*z2, zz=z*z2;
        double wx=w*x2, wy=w*y2, wz=w*z2;
        return new double[]{
                1-(yy+zz), xy-wz, xz+wy,
                xy+wz, 1-(xx+zz), yz-wx,
                xz-wy, yz+wx, 1-(xx+yy)
        };
    }
}
