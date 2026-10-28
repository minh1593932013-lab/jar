package com.example.lyricssky;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.*;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import dev.lavalink.youtube.YoutubeAudioSourceManager;

import javax.sound.sampled.*;
import java.util.function.Consumer;

/** Lavaplayer (the engine inside Lavalink) decoding YouTube straight into the game's audio output. */
public final class MusicPlayer {
    private final AudioPlayerManager manager = new DefaultAudioPlayerManager();
    private final AudioPlayer player;
    private volatile AudioTrack current;
    private volatile boolean running = true;

    public MusicPlayer() {
        manager.getConfiguration().setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_BE);
        manager.registerSourceManager(new YoutubeAudioSourceManager());
        player = manager.createPlayer();
        player.setVolume(60);
        player.addListener(new AudioEventAdapter() {
            @Override public void onTrackEnd(AudioPlayer p, AudioTrack t, AudioTrackEndReason r) {
                if (r.mayStartNext) { current = null; LyricsSkyMod.STATE.clear(); }
            }
        });
        Thread t = new Thread(this::audioLoop, "LyricsSky-Audio");
        t.setDaemon(true);
        t.start();
    }

    public AudioTrack current() { return current; }
    public long position() { AudioTrack t = current; return t == null ? 0 : t.getPosition(); }

    public void setVolume(int v) { player.setVolume(v); }

    public void stop() { current = null; player.stopTrack(); }

    public void play(String query, Consumer<AudioTrack> onStart, Consumer<String> onError) {
        String id = query.startsWith("http") ? query : "ytsearch:" + query;
        manager.loadItem(id, new AudioLoadResultHandler() {
            @Override public void trackLoaded(AudioTrack track) { start(track, onStart); }
            @Override public void playlistLoaded(AudioPlaylist pl) {
                if (pl.getTracks().isEmpty()) onError.accept("No results.");
                else start(pl.getTracks().get(0), onStart);
            }
            @Override public void noMatches() { onError.accept("No results."); }
            @Override public void loadFailed(FriendlyException e) { onError.accept(e.getMessage()); }
        });
    }

    private void start(AudioTrack track, Consumer<AudioTrack> onStart) {
        current = track;
        onStart.accept(track);
        player.playTrack(track);
    }

    private void audioLoop() {
        AudioFormat fmt = new AudioFormat(48000f, 16, 2, true, true);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(fmt)) {
            line.open(fmt, 48000 * 4 / 4); // ~250ms buffer
            line.start();
            while (running) {
                AudioFrame f = player.provide();
                if (f == null) { Thread.sleep(5); continue; }
                byte[] d = f.getData();
                line.write(d, 0, d.length);
            }
        } catch (Exception e) {
            LyricsSkyMod.LOGGER.error("Audio output failed", e);
        }
    }
}
