package ua.ghibli.tv.launcher;

import org.junit.Assert;
import org.junit.Test;
import ua.ghibli.tv.launcher.game.RunnerPhysics;

public class RunnerPhysicsTest {

    @Test
    public void testHeroWaitsForStart() {
        for (String hero : new String[]{"soot", "kodama", "totoro"}) {
            RunnerPhysics r = new RunnerPhysics(hero);
            r.step(1.0);
            Assert.assertEquals(0.0, r.distance, 0.0001);
            Assert.assertFalse(r.jump());
        }
    }

    @Test
    public void testHeroJumpPeaksAndLandsOnce() {
        for (String hero : new String[]{"soot", "kodama", "totoro"}) {
            RunnerPhysics r = new RunnerPhysics(hero);
            r.start();
            Assert.assertTrue(r.jump());
            r.step(0.2);
            Assert.assertTrue(r.y > 100.0);
            Assert.assertFalse(r.jump());
            for (int i = 0; i < 4; i++) r.step(0.2);
            Assert.assertEquals(0.0, r.y, 0.0001);
            Assert.assertEquals(0.0, r.vy, 0.0001);
        }
    }

    @Test
    public void testHeroDuckExpires() {
        for (String hero : new String[]{"soot", "kodama", "totoro"}) {
            RunnerPhysics r = new RunnerPhysics(hero);
            r.start();
            double standing = r.box().h;
            r.crouch();
            Assert.assertTrue(r.box().h < standing);
            for (int i = 0; i < 5; i++) r.step(0.2);
            Assert.assertEquals(0.0, r.duck, 0.0001);
            Assert.assertEquals(standing, r.box().h, 0.0001);
        }
    }

    @Test
    public void testHeroPausesAllPhysics() {
        for (String hero : new String[]{"soot", "kodama", "totoro"}) {
            RunnerPhysics r = new RunnerPhysics(hero);
            r.start();
            r.jump();
            r.step(0.1);
            r.status = "paused";
            double distBefore = r.distance;
            double yBefore = r.y;
            r.step(0.2);
            Assert.assertEquals(distBefore, r.distance, 0.0001);
            Assert.assertEquals(yBefore, r.y, 0.0001);
        }
    }

    @Test
    public void testObstacleCollisionsAndAvoidance() {
        for (String hero : new String[]{"soot", "kodama", "totoro"}) {
            for (double speed : new double[]{360.0, 500.0, 760.0}) {
                for (String kind : new String[]{"stump", "stone", "paper_bird"}) {
                    double distance = (speed - 360.0) / 0.012;
                    double w = "stump".equals(kind) ? 64.0 : "stone".equals(kind) ? 40.0 : 42.0;
                    double h = "stump".equals(kind) ? 48.0 : "stone".equals(kind) ? 74.0 : 34.0;
                    double y = "paper_bird".equals(kind) ? 344.0 : 440.0 - h;

                    RunnerPhysics rCol = new RunnerPhysics(hero);
                    rCol.start();
                    rCol.distance = distance;
                    rCol.next = 100.0;
                    rCol.speed = speed;
                    rCol.obstacles.add(new RunnerPhysics.Obstacle(1, kind, rCol.box().x + rCol.box().w + speed * 0.27, y, w, h));
                    for (int i = 0; i < 120 && "running".equals(rCol.status); i++) {
                        rCol.step(1.0 / 120.0);
                    }
                    Assert.assertEquals("over", rCol.status);

                    RunnerPhysics rAvoid = new RunnerPhysics(hero);
                    rAvoid.start();
                    rAvoid.distance = distance;
                    rAvoid.next = 100.0;
                    rAvoid.speed = speed;
                    rAvoid.obstacles.add(new RunnerPhysics.Obstacle(1, kind, rAvoid.box().x + rAvoid.box().w + speed * 0.27, y, w, h));
                    if ("paper_bird".equals(kind)) {
                        rAvoid.crouch();
                    } else {
                        rAvoid.jump();
                    }
                    for (int i = 0; i < 120 && "running".equals(rAvoid.status); i++) {
                        rAvoid.step(1.0 / 120.0);
                    }
                    Assert.assertEquals("running", rAvoid.status);
                    Assert.assertEquals(1, rAvoid.cleared);
                }
            }
        }
    }

    @Test
    public void testRestartClearsAllState() {
        for (String hero : new String[]{"soot", "kodama", "totoro"}) {
            RunnerPhysics r = new RunnerPhysics(hero);
            r.start();
            r.step(0.2);
            r.status = "over";
            r.start();
            Assert.assertEquals(0.0, r.distance, 0.0001);
            Assert.assertEquals(0, r.obstacles.size());
            Assert.assertEquals(0.0, r.y, 0.0001);
            Assert.assertEquals("running", r.status);
        }
    }

    @Test
    public void testDeterministicAtDifferentHz() {
        for (String hero : new String[]{"soot", "kodama", "totoro"}) {
            RunnerPhysics r30 = simulateHz(hero, 30);
            RunnerPhysics r60 = simulateHz(hero, 60);
            RunnerPhysics r120 = simulateHz(hero, 120);
            Assert.assertTrue(Math.abs(r30.distance - r60.distance) < 0.001);
            Assert.assertTrue(Math.abs(r120.distance - r60.distance) < 0.001);
        }
    }

    private RunnerPhysics simulateHz(String hero, int hz) {
        RunnerPhysics r = new RunnerPhysics(hero, () -> 0.5);
        r.start();
        r.next = 100.0;
        for (int i = 0; i < hz * 2; i++) {
            r.step(1.0 / hz);
        }
        return r;
    }

    @Test
    public void testSpeedProgressionCapped() {
        RunnerPhysics r = new RunnerPhysics();
        r.start();
        r.distance = 1e7;
        r.next = 100.0;
        r.step(0.1);
        Assert.assertEquals(760.0, r.speed, 0.0001);
    }

    @Test
    public void testInvalidHeroRecoversToSoot() {
        Assert.assertEquals("soot", new RunnerPhysics("bad").hero);
    }

    @Test
    public void testSlidePhasesAndPause() {
        RunnerPhysics r = new RunnerPhysics("soot");
        r.start();
        r.crouch();
        Assert.assertEquals(0.72, r.duck, 0.0001);
        r.step(0.25);
        r.status = "paused";
        RunnerPhysics.Pose before = RunnerPhysics.poseFor(r);
        r.step(0.2);
        RunnerPhysics.Pose after = RunnerPhysics.poseFor(r);
        Assert.assertEquals("slide", before.mode);
        Assert.assertEquals(before.frame, after.frame);
    }
}
