package ua.ghibli.tv.launcher.game;

public class RunnerHudLayout {
    public static class Bounds {
        public float left;
        public float top;
        public float right;
        public float bottom;

        public Bounds() {}

        public Bounds(float left, float top, float right, float bottom) {
            set(left, top, right, bottom);
        }

        public void set(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public float width() { return right - left; }
        public float height() { return bottom - top; }
        public float centerX() { return (left + right) / 2f; }
        public float centerY() { return (top + bottom) / 2f; }

        public boolean contains(float x, float y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
    }

    public enum Action {
        NONE, EXIT, PAUSE_TOGGLE, HERO_TOGGLE, JUMP, CROUCH
    }

    public final Bounds exitBounds = new Bounds();
    public final Bounds pauseBounds = new Bounds();
    public final Bounds heroBounds = new Bounds();
    public final Bounds scoreBounds = new Bounds();
    public boolean isCompact = false;

    public int currentWidth = 0;
    public int currentHeight = 0;

    public void update(int width, int height) {
        currentWidth = width;
        currentHeight = height;
        float w = (float) width;
        isCompact = width < 720;

        if (isCompact) {
            exitBounds.set(w - 95f, 15f, w - 15f, 65f);
            pauseBounds.set(exitBounds.left - 10f - 80f, 15f, exitBounds.left - 10f, 65f);
            float heroRight = pauseBounds.left - 10f;
            float heroLeft = Math.max(15f, heroRight - 150f);
            heroBounds.set(heroLeft, 15f, heroRight, 65f);
            scoreBounds.set(15f, 75f, w - 15f, 115f);
        } else {
            exitBounds.set(w - 100f, 15f, w - 20f, 65f);
            pauseBounds.set(w - 190f, 15f, w - 110f, 65f);
            float heroRight = pauseBounds.left - 15f;
            float heroLeft = Math.max(340f, heroRight - 220f);
            heroBounds.set(heroLeft, 15f, heroRight, 65f);
            scoreBounds.set(40f, 25f, 320f, 95f);
        }
    }

    public Action resolveAction(float x, float y, int height) {
        return resolveAction(x, y, currentWidth > 0 ? currentWidth : 1280, height);
    }

    public Action resolveAction(float x, float y, int width, int height) {
        if (width <= 0 || height <= 0 || x < 0 || x > width || y < 0 || y > height) {
            return Action.NONE;
        }

        if (exitBounds.contains(x, y)) return Action.EXIT;
        if (pauseBounds.contains(x, y)) return Action.PAUSE_TOGGLE;
        if (heroBounds.contains(x, y)) return Action.HERO_TOGGLE;

        float hudBottom = isCompact ? 120f : 70f;
        if (y <= hudBottom) {
            return Action.NONE;
        }

        if (y > height * 0.65f) return Action.CROUCH;
        return Action.JUMP;
    }
}
