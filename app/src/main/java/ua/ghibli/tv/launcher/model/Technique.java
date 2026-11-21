package ua.ghibli.tv.launcher.model;

public class Technique {
    private final String id;
    private final String name;
    private final String owner;
    private final int color;
    private final String cue;
    private final String description;

    public Technique(String id, String name, String owner, int color, String cue, String description) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.color = color;
        this.cue = cue;
        this.description = description;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getOwner() { return owner; }
    public int getColor() { return color; }
    public String getCue() { return cue; }
    public String getDescription() { return description; }
}
