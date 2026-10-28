package com.example.lyricssky;

import com.google.gson.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Minimal LRCLIB (https://lrclib.net) client. Blocking - call off the render thread. */
public final class LrcLib {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern LRC = Pattern.compile("^\\[(\\d+):(\\d+(?:\\.\\d+)?)](.*)$");

    private static String get(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "LyricsSky/1.0 (Minecraft Forge mod)").GET().build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200 ? res.body() : null;
        } catch (Exception e) { return null; }
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    /** Strip YouTube noise like "(Official Video)", "[Lyrics]", " - Topic". */
    public static String clean(String s) {
        return s.replaceAll("(?i)[(\\[][^)\\]]*(official|video|audio|lyrics?|visualizer|mv|hd|4k)[^)\\]]*[)\\]]", "")
                .replaceAll("(?i)\\s*-\\s*topic$", "").replaceAll("\\s+", " ").trim();
    }

    public static List<LyricLine> fetch(String title, String artist, long durationMs) {
        title = clean(title); artist = clean(artist);
        String body = get("https://lrclib.net/api/get?track_name=" + enc(title) + "&artist_name=" + enc(artist)
                + "&duration=" + (durationMs / 1000));
        if (body != null) {
            List<LyricLine> l = parse(JsonParser.parseString(body).getAsJsonObject());
            if (!l.isEmpty()) return l;
        }
        body = get("https://lrclib.net/api/search?q=" + enc(title + " " + artist));
        if (body != null) {
            for (JsonElement e : JsonParser.parseString(body).getAsJsonArray()) {
                List<LyricLine> l = parse(e.getAsJsonObject());
                if (!l.isEmpty()) return l;
            }
        }
        return List.of();
    }

    private static List<LyricLine> parse(JsonObject o) {
        JsonElement synced = o.get("syncedLyrics");
        if (synced == null || synced.isJsonNull()) return List.of();
        List<LyricLine> out = new ArrayList<>();
        for (String raw : synced.getAsString().split("\n")) {
            Matcher m = LRC.matcher(raw.trim());
            if (!m.matches()) continue;
            long ms = (long) ((Integer.parseInt(m.group(1)) * 60 + Double.parseDouble(m.group(2))) * 1000);
            out.add(new LyricLine(ms, m.group(3).trim()));
        }
        return out;
    }
}
