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

    /**
     * Looks lyrics up by song name only. The artist argument is kept for API compatibility but ignored.
     * Never throws: any failure just means "no lyrics", so the music keeps playing regardless.
     */
    public static List<LyricLine> fetch(String title, String artist, long durationMs) {
        try {
            String name = clean(title == null ? "" : title);
            if (name.isEmpty()) return List.of();

            List<LyricLine> l = search(name, durationMs);
            if (!l.isEmpty()) return l;

            // YouTube titles are often "Artist - Song": retry with just the part after the dash.
            int dash = name.indexOf(" - ");
            if (dash > 0 && dash + 3 < name.length()) {
                l = search(name.substring(dash + 3).trim(), durationMs);
                if (!l.isEmpty()) return l;
            }
        } catch (Throwable t) {
            LyricsSkyMod.LOGGER.warn("Lyrics lookup failed", t);
        }
        return List.of();
    }

    /** Search LRCLIB by name and pick the synced result whose length is closest to the played track. */
    private static List<LyricLine> search(String name, long durationMs) {
        String body = get("https://lrclib.net/api/search?track_name=" + enc(name));
        if (body == null) body = get("https://lrclib.net/api/search?q=" + enc(name));
        if (body == null) return List.of();
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonArray()) return List.of();
            List<LyricLine> best = List.of();
            double bestDiff = Double.MAX_VALUE;
            for (JsonElement e : root.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                List<LyricLine> l = parse(o);
                if (l.isEmpty()) continue;
                double diff = 0;
                JsonElement d = o.get("duration");
                if (d != null && !d.isJsonNull() && durationMs > 0) diff = Math.abs(d.getAsDouble() * 1000 - durationMs);
                if (diff < bestDiff) { bestDiff = diff; best = l; }
            }
            return best;
        } catch (Exception ex) { return List.of(); }
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
