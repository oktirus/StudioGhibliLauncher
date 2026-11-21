package ua.ghibli.tv.launcher.model;

public class Scene {
    private final String id;
    private final String name;
    private final String caption;
    private final String mood;
    private final String color;
    private final boolean hero;
    private final String title;
    private final String description;
    private final String imageAsset;
    private final String thumbAsset;
    private final String particles;
    private final int[] palette;

    public Scene(String id, String name, String caption, String mood, String color,
                 boolean hero, String title, String description,
                 String imageAsset, String thumbAsset, String particles, int[] palette) {
        this.id = id;
        this.name = name;
        this.caption = caption;
        this.mood = mood;
        this.color = color;
        this.hero = hero;
        this.title = title;
        this.description = description;
        this.imageAsset = imageAsset;
        this.thumbAsset = thumbAsset;
        this.particles = particles;
        this.palette = palette;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCaption() { return caption; }
    public String getMood() { return mood; }
    public String getColor() { return color; }
    public boolean isHero() { return hero; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getImageAsset() { return imageAsset; }
    public String getThumbAsset() { return thumbAsset; }
    public String getParticles() { return particles; }
    public int[] getPalette() { return palette; }
}
