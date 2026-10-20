package com.julz.rendererbenchmark.core;

import java.util.List;

/**
 * The camera path, as keyframes relative to the world spawn. Positions between keyframes follow
 * a Catmull-Rom spline so the flight is smooth; the route is a closed loop, so the warm-up can fly
 * the same path the timed run will.
 */
public final class Route {
    /** One point on the path. Yaw and pitch are in degrees, as Minecraft uses them. */
    public record Keyframe(double seconds, double x, double y, double z, float yaw, float pitch) {
    }

    public record Pose(double x, double y, double z, float yaw, float pitch) {
    }

    /**
     * About 75 seconds over and around spawn: a high circle that shows the far terrain at full
     * render distance, a dive down to treetop height, and a low pass back to the start.
     */
    public static final Route DEFAULT = new Route(List.of(
            new Keyframe(0, 0, 40, -96, 0, 15),
            new Keyframe(10, 96, 50, -48, 45, 20),
            new Keyframe(20, 96, 60, 48, 135, 25),
            new Keyframe(30, 0, 60, 112, 180, 20),
            new Keyframe(40, -96, 45, 48, 225, 10),
            new Keyframe(50, -64, 15, -32, 300, 0),
            new Keyframe(60, 16, 10, -48, 360, 5),
            new Keyframe(75, 0, 40, -96, 360, 15)));

    private final List<Keyframe> keyframes;

    public Route(List<Keyframe> keyframes) {
        if (keyframes.size() < 2) {
            throw new IllegalArgumentException("A route needs at least two keyframes");
        }
        this.keyframes = List.copyOf(keyframes);
    }

    public double durationSeconds() {
        return keyframes.getLast().seconds();
    }

    /** Where the camera is after the given time, wrapping around at the end of the loop. */
    public Pose sample(double seconds) {
        double duration = durationSeconds();
        double t = ((seconds % duration) + duration) % duration;
        int i = 0;
        while (i < keyframes.size() - 2 && keyframes.get(i + 1).seconds() <= t) {
            i++;
        }
        Keyframe k1 = keyframes.get(i);
        Keyframe k2 = keyframes.get(i + 1);
        Keyframe k0 = neighbor(i - 1);
        Keyframe k3 = neighbor(i + 2);
        double span = k2.seconds() - k1.seconds();
        double u = span > 0 ? (t - k1.seconds()) / span : 0;
        return new Pose(
                catmullRom(k0.x(), k1.x(), k2.x(), k3.x(), u),
                catmullRom(k0.y(), k1.y(), k2.y(), k3.y(), u),
                catmullRom(k0.z(), k1.z(), k2.z(), k3.z(), u),
                (float) lerp(k1.yaw(), k2.yaw(), smooth(u)),
                (float) lerp(k1.pitch(), k2.pitch(), smooth(u)));
    }

    /**
     * The keyframe at an index outside the list: on a closed loop (last keyframe back at the
     * first one's position) the path continues around, otherwise the end keyframe repeats.
     */
    private Keyframe neighbor(int index) {
        int last = keyframes.size() - 1;
        if (index >= 0 && index <= last) {
            return keyframes.get(index);
        }
        if (isClosed() && last >= 2) {
            return index < 0 ? keyframes.get(last - 1) : keyframes.get(1);
        }
        return keyframes.get(Math.clamp(index, 0, last));
    }

    private boolean isClosed() {
        Keyframe first = keyframes.getFirst();
        Keyframe last = keyframes.getLast();
        return first.x() == last.x() && first.y() == last.y() && first.z() == last.z();
    }

    private static double catmullRom(double p0, double p1, double p2, double p3, double u) {
        double u2 = u * u;
        double u3 = u2 * u;
        return 0.5 * (2 * p1 + (p2 - p0) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (3 * p1 - p0 - 3 * p2 + p3) * u3);
    }

    private static double lerp(double a, double b, double u) {
        return a + (b - a) * u;
    }

    private static double smooth(double u) {
        return u * u * (3 - 2 * u);
    }
}
