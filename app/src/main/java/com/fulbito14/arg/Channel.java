package com.fulbito14.arg;

/**
 * Channel data model - each sports channel
 */
public class Channel {
    public int id;
    public String name;
    public int number;
    public String category;
    public String country;
    public String logoKey;
    public String embedUrl;
    public String embedBackup;
    public String description;

    public Channel(int id, String name, int number, String category, String country,
                   String logoKey, String embedUrl, String embedBackup, String description) {
        this.id = id;
        this.name = name;
        this.number = number;
        this.category = category;
        this.country = country;
        this.logoKey = logoKey;
        this.embedUrl = embedUrl;
        this.embedBackup = embedBackup;
        this.description = description;
    }

    public boolean isPremium() {
        return category != null && category.contains("Premium");
    }
}
