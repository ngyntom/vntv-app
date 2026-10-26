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

    public static List<Channel> parse(InputStream in) throws IOException {
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
                    result.add(new Channel(pendingName, pendingLogo, line, pendingGroup));
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
