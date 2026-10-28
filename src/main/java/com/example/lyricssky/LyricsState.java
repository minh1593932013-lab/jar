package com.example.lyricssky;

import java.util.List;

public final class LyricsState {
    public volatile List<LyricLine> lines = List.of();
    public volatile boolean visible = true;
    public volatile boolean active = false;

    public void set(List<LyricLine> l) { lines = l; active = true; }
    public void clear() { lines = List.of(); active = false; }
}
