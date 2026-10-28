package com.example.lyricssky;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod("lyricssky")
public final class LyricsSkyMod {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final LyricsState STATE = new LyricsState();
    private static MusicPlayer music;

    public static synchronized MusicPlayer music() {
        if (music == null) music = new MusicPlayer();
        return music;
    }
}
