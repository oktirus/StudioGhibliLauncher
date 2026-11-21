package ua.ghibli.tv.launcher.game;

import java.util.Locale;

public enum RunnerCue {
    START, JUMP, LAND, SLIDE, CLEAR, HIT;

    public final String assetName = name().toLowerCase(Locale.ROOT);
}
