package ua.ghibli.tv.launcher.model;

public class Quote {
    private final String id;
    private final String character;
    private final String text;

    public Quote(String id, String character, String text) {
        this.id = id;
        this.character = character;
        this.text = text;
    }

    public String getId() { return id; }
    public String getCharacter() { return character; }
    public String getText() { return text; }
}
