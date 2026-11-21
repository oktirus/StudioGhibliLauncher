package ua.ghibli.tv.launcher.model;

public class HighlightSound {
    private final String id;
    private final String name;
    private final String cueAsset;

    public HighlightSound(String id, String name, String cueAsset) {
        this.id = id;
        this.name = name;
        this.cueAsset = cueAsset;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCueAsset() { return cueAsset; }
}
