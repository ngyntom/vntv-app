package com.example.vntv;

import java.util.Objects;

public class Channel {
    public final String name;
    public final String quality;
    public final String logoUrl;
    public final String streamUrl;
    public final String groupTitle;
    public final String sourceUrl;

    public Channel(String name, String quality, String logoUrl, String streamUrl, String groupTitle, String sourceUrl) {
        this.name = name;
        this.quality = quality;
        this.logoUrl = logoUrl;
        this.streamUrl = streamUrl;
        this.groupTitle = groupTitle;
        this.sourceUrl = sourceUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Channel)) return false;
        Channel c = (Channel) o;
        return Objects.equals(name, c.name)
                && Objects.equals(quality, c.quality)
                && Objects.equals(logoUrl, c.logoUrl)
                && Objects.equals(streamUrl, c.streamUrl)
                && Objects.equals(groupTitle, c.groupTitle)
                && Objects.equals(sourceUrl, c.sourceUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, quality, logoUrl, streamUrl, groupTitle, sourceUrl);
    }
}
