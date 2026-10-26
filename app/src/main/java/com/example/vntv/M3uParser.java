package com.example.vntv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M3uParser {
    private static final Pattern ATTR_LOGO = Pattern.compile("tvg-logo=\"([^\"]*)\"");
    private static final Pattern ATTR_GROUP = Pattern.compile("group-title=\"([^\"]*)\"");
    // Trailing quality tag in the display name, e.g. "VTV1 (720)", "HTV7 HD", "K+ (1080p)".
    private static final Pattern QUALITY_SUFFIX = Pattern.compile(
            "\\s*[\\(\\[]?\\s*(4K|FHD|UHD|HD|SD|\\d{3,4}p?)\\s*[\\)\\]]?\\s*$",
            Pattern.CASE_INSENSITIVE);
    // Trailing bracketed notes some sources tack on, e.g. "An Giang TV 1 [Geo-blocked]";
    // stripped entirely (unlike the quality tag, there's no info worth keeping).
    private static final Pattern BRACKET_SUFFIX = Pattern.compile("\\s*\\[[^\\[\\]]*\\]\\s*$");

    public static List<Channel> parse(InputStream in, String sourceUrl) throws IOException {
        List<Channel> result = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        String pendingName = null, pendingLogo = null, pendingGroup = null;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF")) {
                int comma = line.lastIndexOf(',');
                pendingName = comma >= 0 ? line.substring(comma + 1).trim() : "Unknown";
                Matcher logoM = ATTR_LOGO.matcher(line);
                pendingLogo = logoM.find() ? logoM.group(1) : null;
                Matcher groupM = ATTR_GROUP.matcher(line);
                pendingGroup = groupM.find() ? groupM.group(1) : null;
            } else if (!line.startsWith("#")) {
                if (pendingName != null) {
                    // A bracketed note (if any) always trails the quality tag in these sources
                    // ("Name (1080p) [Geo-blocked]"), so strip it first.
                    Matcher bracketM = BRACKET_SUFFIX.matcher(pendingName);
                    if (bracketM.find()) {
                        pendingName = pendingName.substring(0, bracketM.start()).trim();
                    }
                    String quality = null;
                    Matcher qualityM = QUALITY_SUFFIX.matcher(pendingName);
                    if (qualityM.find() && qualityM.start() > 0) {
                        quality = qualityM.group(1).toUpperCase();
                        pendingName = pendingName.substring(0, qualityM.start()).trim();
                    }
                    result.add(new Channel(pendingName, quality, pendingLogo, line, pendingGroup, sourceUrl));
                    pendingName = null;
                    pendingLogo = null;
                    pendingGroup = null;
                }
            }
        }
        reader.close();
        return result;
    }
}
