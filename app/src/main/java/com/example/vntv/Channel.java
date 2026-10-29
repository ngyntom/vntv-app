package com.example.vntv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Channel {
    public final String name;
    public final String quality;
    public final String logoUrl;
    /** The channel's identity (favorites, last played) as well as its first stream to try. */
    public final String streamUrl;
    public final String groupTitle;
    public final String sourceUrl;
    /** Same channel from other playlists, tried in order when streamUrl fails to play. */
    public final List<String> fallbackUrls;

    public Channel(String name, String quality, String logoUrl, String streamUrl, String groupTitle, String sourceUrl) {
        this(name, quality, logoUrl, streamUrl, groupTitle, sourceUrl, Collections.emptyList());
    }

    public Channel(String name, String quality, String logoUrl, String streamUrl, String groupTitle,
                   String sourceUrl, List<String> fallbackUrls) {
        this.name = name;
        this.quality = quality;
        this.logoUrl = logoUrl;
        this.streamUrl = streamUrl;
        this.groupTitle = groupTitle;
        this.sourceUrl = sourceUrl;
        this.fallbackUrls = Collections.unmodifiableList(new ArrayList<>(fallbackUrls));
    }

    /** Whether url is any of this channel's streams; saved favorites may predate a merge. */
    public boolean hasUrl(String url) {
        return streamUrl.equals(url) || fallbackUrls.contains(url);
    }

    /** streamUrl followed by every fallback. */
    public List<String> allUrls() {
        List<String> urls = new ArrayList<>();
        urls.add(streamUrl);
        urls.addAll(fallbackUrls);
        return urls;
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
                && Objects.equals(sourceUrl, c.sourceUrl)
                && fallbackUrls.equals(c.fallbackUrls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, quality, logoUrl, streamUrl, groupTitle, sourceUrl, fallbackUrls);
    }
}
