package com.example.vntv;

import java.util.ArrayList;
import java.util.List;

/** Shares the current channel list with PlayerActivity so it can offer in-player switching. */
public class ChannelHolder {
    public static List<Channel> current = new ArrayList<>();
}
