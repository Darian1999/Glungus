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
        for (int row=0; row<4; row++) {
            for (int col=0; col<4; col++) {
                double sum = 0;
                for (int k=0;k<4;k++) sum += a[row*4+k]*b[k*4+col];
                out[row*4+col]=sum;
            }
        }
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
    public static double[] transpose4(double[] m) {
        double[] t = new double[16];
        for (int r=0;r<4;r++) for (int c=0;c<4;c++) t[c*4+r]=m[r*4+c];
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
    public static double[] multiply3(double[] a, double[] b){
        double[] r=new double[9];
        for(int row=0;row<3;row++) for(int col=0;col<3;col++){
            double s=0; for(int k=0;k<3;k++) s+=a[row*3+k]*b[k*3+col];
            r[row*3+col]=s;
        }
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
