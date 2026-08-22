package org.xiaojian999.superpowers.math;

/**
 * Easing curves for Glungus animations: HUD transitions, tornado growth,
 * ring spin-up, earthquake shake falloff. All take {@code t in [0,1]}.
 */
public final class Easing {
    private Easing() {}

    // ---- Linear
    public static double linear(double t) { return t; }

    // ---- Quad
    public static double inQuad(double t) { return t * t; }
    public static double outQuad(double t) { double u = 1 - t; return 1 - u * u; }
    public static double inOutQuad(double t) {
        if (t < 0.5) return 2 * t * t;
        double u = 1 - t;
        return 1 - 2 * u * u;
    }

    // ---- Cubic
    public static double inCubic(double t) { return t * t * t; }
    public static double outCubic(double t) { double u = 1 - t; return 1 - u * u * u; }
    public static double inOutCubic(double t) {
        if (t < 0.5) return 4 * t * t * t;
        double u = 1 - t;
        return 1 - 4 * u * u * u;
    }

    // ---- Quart / Quint / Expo / Circ
    public static double inQuart(double t) { double t2 = t * t; return t2 * t2; }
    public static double outQuart(double t) { double u = 1 - t; double u2 = u * u; return 1 - u2 * u2; }
    public static double inOutQuart(double t) {
        if (t < 0.5) return 8 * t * t * t * t;
        double u = 1 - t;
        double u2 = u * u;
        return 1 - 8 * u2 * u2;
    }
    public static double inQuint(double t) { double t2 = t * t; return t2 * t2 * t; }
    public static double outQuint(double t) { double u = 1 - t; double u2 = u * u; return 1 - u2 * u2 * u; }
    public static double inExpo(double t) { return t == 0 ? 0 : Math.pow(2, 10 * (t - 1)); }
    public static double outExpo(double t) { return t == 1 ? 1 : 1 - Math.pow(2, -10 * t); }
    public static double inOutExpo(double t) {
        if (t == 0) return 0; if (t == 1) return 1;
        return t < 0.5 ? Math.pow(2, 20 * t - 10) / 2 : (2 - Math.pow(2, -20 * t + 10)) / 2;
    }
    public static double inCirc(double t) { return 1 - Math.sqrt(1 - t * t); }
    public static double outCirc(double t) { double u = t - 1; return Math.sqrt(1 - u * u); }
    public static double inOutCirc(double t) {
        if (t < 0.5) {
            double u = 2 * t;
            return (1 - Math.sqrt(1 - u * u)) * 0.5;
        } else {
            double u = -2 * t + 2;
            return (Math.sqrt(1 - u * u) + 1) * 0.5;
        }
    }

    // ---- Back (overshoot)
    public static double inBack(double t) {
        double c1 = 1.70158, c3 = c1 + 1;
        return c3 * t * t * t - c1 * t * t;
    }
    public static double outBack(double t) {
        double c1 = 1.70158, c3 = c1 + 1;
        double u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }
    public static double inOutBack(double t) {
        double c1 = 1.70158, c2 = c1 * 1.525;
        if (t < 0.5) {
            double u = 2 * t;
            return (u * u * ((c2 + 1) * u - c2)) * 0.5;
        } else {
            double u = 2 * t - 2;
            return (u * u * ((c2 + 1) * u + c2) + 2) * 0.5;
        }
    }

    // ---- Elastic
    public static double inElastic(double t) {
        if (t == 0) return 0; if (t == 1) return 1;
        return -Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * (GlungFastMath.TAU) / 3);
    }
    public static double outElastic(double t) {
        if (t == 0) return 0; if (t == 1) return 1;
        return Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * GlungFastMath.TAU / 3) + 1;
    }
    public static double inOutElastic(double t) {
        if (t == 0) return 0; if (t == 1) return 1;
        double s = GlungFastMath.TAU / 4.5;
        return t < 0.5 ? -(Math.pow(2, 20 * t - 10) * Math.sin((20 * t - 11.125) * s)) / 2
                : (Math.pow(2, -20 * t + 10) * Math.sin((20 * t - 11.125) * s)) / 2 + 1;
    }

    // ---- Bounce
    public static double outBounce(double t) {
        double n1 = 7.5625, d1 = 2.75;
        if (t < 1 / d1) return n1 * t * t;
        else if (t < 2 / d1) { t -= 1.5 / d1; return n1 * t * t + 0.75; }
        else if (t < 2.5 / d1) { t -= 2.25 / d1; return n1 * t * t + 0.9375; }
        else { t -= 2.625 / d1; return n1 * t * t + 0.984375; }
    }
    public static double inBounce(double t) { return 1 - outBounce(1 - t); }
    public static double inOutBounce(double t) { return t < 0.5 ? (1 - outBounce(1 - 2 * t)) / 2 : (1 + outBounce(2 * t - 1)) / 2; }

    // ---- Helpers
    public static double apply(String name, double t) {
        return switch (name) {
            case "linear" -> linear(t);
            case "inQuad" -> inQuad(t);
            case "outQuad" -> outQuad(t);
            case "inOutQuad" -> inOutQuad(t);
            case "inCubic" -> inCubic(t);
            case "outCubic" -> outCubic(t);
            case "inOutCubic" -> inOutCubic(t);
            case "outBounce" -> outBounce(t);
            case "inElastic" -> inElastic(t);
            case "outElastic" -> outElastic(t);
            default -> t;
        };
    }
}
