package com.fulbito14.arg;

/**
 * Channel data model for M3U-parsed channels
 * v2.4: Added customUserAgent for EXTVLCOPT support, XC server source type
 */
public class Channel {
    public int id;
    public String name;
    public int number;
    public String category;
    public String country;
    public String logoKey;
    public String streamUrl;      // Direct M3U8/HLS URL
    public String logoUrl;        // Channel logo URL from M3U metadata
    public String description;
    public String source;         // Which playlist this came from (iptv-org, xc-server, builtin, etc.)
    public String customUserAgent; // Custom User-Agent from #EXTVLCOPT (v2.4)

    public Channel(int id, String name, int number, String category, String country,
                   String logoKey, String streamUrl, String logoUrl, String description, String source) {
        this.id = id;
        this.name = name;
        this.number = number;
        this.category = category;
        this.country = country;
        this.logoKey = logoKey;
        this.streamUrl = streamUrl;
        this.logoUrl = logoUrl;
        this.description = description;
        this.source = source;
        this.customUserAgent = null;
    }

    /**
     * v2.4: Full constructor with customUserAgent support
     */
    public Channel(int id, String name, int number, String category, String country,
                   String logoKey, String streamUrl, String logoUrl, String description,
                   String source, String customUserAgent) {
        this.id = id;
        this.name = name;
        this.number = number;
        this.category = category;
        this.country = country;
        this.logoKey = logoKey;
        this.streamUrl = streamUrl;
        this.logoUrl = logoUrl;
        this.description = description;
        this.source = source;
        this.customUserAgent = customUserAgent;
    }

    public boolean isPremium() {
        return category != null && category.contains("Premium");
    }

    public boolean isSport() {
        if (category == null) return false;
        String cat = category.toLowerCase();
        return cat.contains("sport") || cat.contains("deporte") || cat.contains("futbol") || cat.contains("football");
    }

    /**
     * v2.4: Check if this channel has a custom user-agent
     */
    public boolean hasCustomUserAgent() {
        return customUserAgent != null && !customUserAgent.isEmpty();
    }
}
