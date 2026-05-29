package com.fulbito14.arg;

import java.util.ArrayList;
import java.util.List;

/**
 * All channel data - embedded in the app
 * v1.5: Updated with verified working channel slugs
 * Each channel has embed URLs from la12hd.com (primary) and la14hd.com (backup)
 * The app extracts M3U8 dynamically at runtime using OkHttp
 */
public class ChannelData {

    public static final String USERNAME = "limonsin14";
    public static final String PASSWORD = "1276";

    public static List<Channel> getChannels() {
        List<Channel> channels = new ArrayList<>();

        // ESPN Channels
        channels.add(new Channel(1,  "ESPN",               1,  "Deportes",        "Argentina",      "espn",    "https://la14hd.com/vivo/canales.php?stream=espn",              "https://la12hd.com/vivo/canal.php?stream=espn",              "ESPN Argentina"));
        channels.add(new Channel(2,  "ESPN 2",             2,  "Deportes",        "Argentina",      "espn",    "https://la12hd.com/vivo/canal.php?stream=espn2",             "https://la14hd.com/vivo/canales.php?stream=espn2",             "ESPN 2 Argentina"));
        channels.add(new Channel(3,  "ESPN 3",             3,  "Deportes",        "Argentina",      "espn",    "https://la14hd.com/vivo/canales.php?stream=espn3",             "https://la12hd.com/vivo/canal.php?stream=espn3",             "ESPN 3 Argentina"));
        channels.add(new Channel(4,  "ESPN 4",             4,  "Deportes",        "Argentina",      "espn",    "https://la12hd.com/vivo/canal.php?stream=espn4",             "https://la14hd.com/vivo/canales.php?stream=espn4",             "ESPN 4 Argentina"));
        channels.add(new Channel(5,  "ESPN 5",             5,  "Deportes",        "Argentina",      "espn",    "https://la12hd.com/vivo/canal.php?stream=espn5",             "https://la14hd.com/vivo/canales.php?stream=espn5",             "ESPN 5 Argentina"));
        channels.add(new Channel(6,  "ESPN 6",             6,  "Deportes",        "Argentina",      "espn",    "https://la14hd.com/vivo/canales.php?stream=espn6",             "https://la12hd.com/vivo/canal.php?stream=espn6",             "ESPN 6 Argentina"));
        channels.add(new Channel(7,  "ESPN 7",             7,  "Deportes",        "Argentina",      "espn",    "https://la14hd.com/vivo/canales.php?stream=espn7",             "https://la12hd.com/vivo/canal.php?stream=espn7",             "ESPN 7 Argentina"));
        channels.add(new Channel(8,  "ESPN Premium",       8,  "Deportes Premium", "Argentina",      "espn",    "https://la14hd.com/vivo/canales.php?stream=espnpremium",       "https://la12hd.com/vivo/canal.php?stream=espnpremium",       "ESPN Premium - Futbol Argentino"));

        // Fox Sports Channels
        channels.add(new Channel(9,  "Fox Sports",         9,  "Deportes",        "Latinoamérica",  "fox",     "https://la12hd.com/vivo/canal.php?stream=foxsports",         "https://la14hd.com/vivo/canales.php?stream=foxsports",         "Fox Sports Latinoamerica"));
        channels.add(new Channel(10, "Fox Sports 2",       10, "Deportes",        "Latinoamérica",  "fox",     "https://la12hd.com/vivo/canal.php?stream=foxsports2",        "https://la14hd.com/vivo/canales.php?stream=foxsports2",        "Fox Sports 2 Latinoamerica"));
        channels.add(new Channel(11, "Fox Sports 3",       11, "Deportes",        "Latinoamérica",  "fox",     "https://la12hd.com/vivo/canal.php?stream=foxsports3",        "https://la14hd.com/vivo/canales.php?stream=foxsports3",        "Fox Sports 3 Latinoamerica"));
        channels.add(new Channel(12, "Fox Sports Premium",  12, "Deportes Premium", "Argentina",      "fox",     "https://la14hd.com/vivo/canales.php?stream=foxsportspremium",  "https://la12hd.com/vivo/canal.php?stream=foxsportspremium",  "Fox Sports Premium"));

        // DSports Channels
        channels.add(new Channel(13, "DSports",            13, "Deportes",        "Argentina",      "dsports", "https://la14hd.com/vivo/canales.php?stream=dsports",           "https://la12hd.com/vivo/canal.php?stream=dsports",           "DSports Argentina"));
        channels.add(new Channel(14, "DSports+",           14, "Deportes Premium", "Argentina",      "dsports", "https://la14hd.com/vivo/canales.php?stream=dsportsplus",       "https://la12hd.com/vivo/canal.php?stream=dsportsplus",       "DSports+ Premium"));

        // TNT Sports Channels
        channels.add(new Channel(15, "TNT Sports",         15, "Deportes",        "Argentina",      "tnt",     "https://la14hd.com/vivo/canales.php?stream=tntsports",         "https://la12hd.com/vivo/canal.php?stream=tntsports",         "TNT Sports Argentina"));

        // TyC Sports
        channels.add(new Channel(16, "TyC Sports",         16, "Deportes",        "Argentina",      "tyc",     "https://la14hd.com/vivo/canales.php?stream=tycsports",         "https://la12hd.com/vivo/canal.php?stream=tycsports",         "TyC Sports - Futbol 24hs"));

        // Win Sports Channels (Colombia)
        channels.add(new Channel(17, "Win Sports",         17, "Deportes",        "Colombia",       "win",     "https://la14hd.com/vivo/canales.php?stream=winsports",         "https://la12hd.com/vivo/canal.php?stream=winsports",         "Win Sports Colombia"));
        channels.add(new Channel(18, "Win Sports+",        18, "Deportes",        "Colombia",       "win",     "https://la14hd.com/vivo/canales.php?stream=winplus",           "https://la12hd.com/vivo/canal.php?stream=winplus",           "Win Sports+ Colombia"));
        channels.add(new Channel(19, "Win Sports+ Premium", 19, "Deportes Premium", "Colombia",       "win",     "https://la14hd.com/vivo/canales.php?stream=winsportsplus",     "https://la12hd.com/vivo/canal.php?stream=winsportsplus",     "Win Sports+ Premium"));

        // TUDN
        channels.add(new Channel(20, "TUDN",              20, "Deportes",        "Mexico",         "tudn",    "https://la14hd.com/vivo/canales.php?stream=tudn",              "https://la12hd.com/vivo/canal.php?stream=tudn",              "TUDN Mexico"));

        return channels;
    }
}
