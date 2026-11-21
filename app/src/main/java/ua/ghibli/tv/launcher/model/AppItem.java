package ua.ghibli.tv.launcher.model;

import android.graphics.drawable.Drawable;

public class AppItem {
    private final String packageName;
    private final String name;
    private final String kind;
    private final String description;
    private Drawable systemIcon;
    private boolean pinned;

    public AppItem(String packageName, String name, String kind, String description, Drawable systemIcon) {
        this.packageName = packageName;
        this.name = name;
        this.kind = kind;
        this.description = description;
        this.systemIcon = systemIcon;
        this.pinned = false;
    }

    public String getPackageName() { return packageName; }
    public String getName() { return name; }
    public String getKind() { return kind; }
    public String getDescription() { return description; }
    public String getAssetPath() { return "icons/icon-" + kind + ".webp"; }
    public Drawable getSystemIcon() { return systemIcon; }
    public void setSystemIcon(Drawable icon) { this.systemIcon = icon; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
}
