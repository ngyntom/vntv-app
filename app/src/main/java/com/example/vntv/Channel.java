package com.example.vntv;

public class Channel {
    public final String name;
    public final String quality;
    public final String logoUrl;
    public final String streamUrl;
    public final String groupTitle;

    public Channel(String name, String quality, String logoUrl, String streamUrl, String groupTitle) {
        this.name = name;
        this.quality = quality;
        this.logoUrl = logoUrl;
        this.streamUrl = streamUrl;
        this.groupTitle = groupTitle;
    }
}
