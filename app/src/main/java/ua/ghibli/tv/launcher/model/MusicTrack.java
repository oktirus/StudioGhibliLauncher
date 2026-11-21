package ua.ghibli.tv.launcher.model;

public class MusicTrack {
    private final String id;
    private final String name;
    private final String assetPath;

    public MusicTrack(String id, String name, String assetPath) {
        this.id = id;
        this.name = name;
        this.assetPath = assetPath;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getAssetPath() { return assetPath; }
}
