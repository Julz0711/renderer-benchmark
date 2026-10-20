package com.julz.rendererbenchmark.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteTest {
    @Test
    void passesThroughKeyframes() {
        Route.Pose pose = Route.DEFAULT.sample(10);
        assertEquals(96, pose.x(), 1e-9);
        assertEquals(50, pose.y(), 1e-9);
        assertEquals(-48, pose.z(), 1e-9);
        assertEquals(45, pose.yaw(), 1e-6);
    }

    @Test
    void loopsBackToTheStart() {
        Route.Pose start = Route.DEFAULT.sample(0);
        Route.Pose wrapped = Route.DEFAULT.sample(Route.DEFAULT.durationSeconds());
        assertEquals(start.x(), wrapped.x(), 1e-9);
        assertEquals(start.z(), wrapped.z(), 1e-9);
    }

    @Test
    void isSmoothBetweenKeyframes() {
        Route route = new Route(List.of(
                new Route.Keyframe(0, 0, 0, 0, 0, 0),
                new Route.Keyframe(10, 10, 0, 0, 90, 0)));
        Route.Pose middle = route.sample(5);
        assertTrue(middle.x() > 0 && middle.x() < 10);
        assertEquals(45, middle.yaw(), 1e-6);
    }

    @Test
    void wrapsSmoothlyAtTheEndOfTheLoop() {
        // Moving at a similar speed just before and just after the loop point, with no jump
        Route.Pose before = Route.DEFAULT.sample(Route.DEFAULT.durationSeconds() - 0.05);
        Route.Pose start = Route.DEFAULT.sample(0);
        Route.Pose after = Route.DEFAULT.sample(0.05);
        double speedIn = Math.hypot(start.x() - before.x(), start.z() - before.z());
        double speedOut = Math.hypot(after.x() - start.x(), after.z() - start.z());
        assertEquals(speedIn, speedOut, speedOut * 0.5);
    }

    @Test
    void sameTimeGivesSamePose() {
        assertEquals(Route.DEFAULT.sample(33.3), Route.DEFAULT.sample(33.3));
    }
}
