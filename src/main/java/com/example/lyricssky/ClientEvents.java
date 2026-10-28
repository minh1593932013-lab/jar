package com.example.lyricssky;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = "lyricssky", value = Dist.CLIENT)
public final class ClientEvents {
    private static final float DISTANCE = 32f, HEIGHT = 18f, SCALE = 0.3f;

    private static void chat(String msg, ChatFormatting color) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> { if (mc.player != null) mc.player.displayClientMessage(Component.literal("[Lyrics] " + msg).withStyle(color), false); });
    }

    // ---------------------------------------------------------------- commands
    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("lyrics")
            .then(Commands.literal("play").then(Commands.argument("query", StringArgumentType.greedyString()).executes(ctx -> {
                String q = StringArgumentType.getString(ctx, "query");
                chat("Searching YouTube...", ChatFormatting.GRAY);
                Thread t = new Thread(() -> LyricsSkyMod.music().play(q, track -> {
                    LyricsSkyMod.STATE.clear();
                    var info = track.getInfo();
                    chat("♪ " + info.title + " - " + info.author, ChatFormatting.AQUA);
                    new Thread(() -> {
                        List<LyricLine> l = LrcLib.fetch(info.title, info.author, info.length);
                        LyricsSkyMod.STATE.set(l);
                        if (l.isEmpty()) chat("No synced lyrics found on LRCLIB.", ChatFormatting.YELLOW);
                    }, "LyricsSky-Lrclib").start();
                }, err -> chat("Failed: " + err, ChatFormatting.RED)), "LyricsSky-Load");
                t.setDaemon(true); t.start();
                return 1;
            })))
            .then(Commands.literal("stop").executes(ctx -> {
                LyricsSkyMod.music().stop(); LyricsSkyMod.STATE.clear();
                chat("Stopped.", ChatFormatting.GREEN); return 1;
            }))
            .then(Commands.literal("toggle").executes(ctx -> {
                LyricsSkyMod.STATE.visible = !LyricsSkyMod.STATE.visible;
                chat("Sky lyrics " + (LyricsSkyMod.STATE.visible ? "ON" : "OFF"), ChatFormatting.GREEN); return 1;
            }))
            .then(Commands.literal("volume").then(Commands.argument("percent", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                int v = IntegerArgumentType.getInteger(ctx, "percent");
                LyricsSkyMod.music().setVolume(v); chat("Volume " + v + "%", ChatFormatting.GREEN); return 1;
            }))));
    }

    // ---------------------------------------------------------------- rendering
    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        LyricsState st = LyricsSkyMod.STATE;
        if (!st.visible || !st.active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || LyricsSkyMod.music().current() == null) return;

        long pos = LyricsSkyMod.music().position();
        List<LyricLine> lines = st.lines;

        Camera cam = e.getCamera();
        float yaw = cam.getYRot() * Mth.DEG_TO_RAD;
        // horizontal forward vector (Minecraft: yaw 0 looks toward +Z)
        Vec3 target = new Vec3(-Mth.sin(yaw) * DISTANCE, HEIGHT, Mth.cos(yaw) * DISTANCE);

        PoseStack ps = e.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        Font font = mc.font;

        int idx = -1;
        for (int i = 0; i < lines.size(); i++) { if (lines.get(i).timeMs() <= pos) idx = i; else break; }

        ps.pushPose();
        ps.translate(target.x, target.y, target.z); // pose stack is already camera-relative
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-SCALE, -SCALE, SCALE);

        if (lines.isEmpty() || idx < 0) {
            drawCentered(font, ps, buffers, Component.literal("♪ ♪ ♪").withStyle(ChatFormatting.AQUA), 0, 1.2f);
        } else {
            LyricLine cur = lines.get(idx);
            long end = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : cur.timeMs() + 5000;
            end = Math.min(end, cur.timeMs() + 9000);
            double frac = Mth.clamp((pos - cur.timeMs()) / (double) Math.max(1, end - cur.timeMs()), 0, 1);

            if (idx > 0) drawCentered(font, ps, buffers, Component.literal(lines.get(idx - 1).text()).withStyle(ChatFormatting.DARK_GRAY), -16, 0.8f);
            drawCentered(font, ps, buffers, words(cur.text().isBlank() ? "♪" : cur.text(), frac), 0, 1.4f);
            if (idx + 1 < lines.size()) drawCentered(font, ps, buffers, Component.literal(lines.get(idx + 1).text()).withStyle(ChatFormatting.GRAY), 18, 0.8f);
        }
        buffers.endBatch();
        ps.popPose();
    }

    private static void drawCentered(Font font, PoseStack ps, net.minecraft.client.renderer.MultiBufferSource buffers,
                                     Component c, float y, float scale) {
        ps.pushPose();
        ps.scale(scale, scale, 1f);
        float x = -font.width(c) / 2f;
        font.drawInBatch(c, x, y / scale, 0xFFFFFFFF, true, ps.last().pose(), buffers,
                Font.DisplayMode.SEE_THROUGH, 0, 15728880);
        ps.popPose();
    }

    /** LRCLIB is line-synced, so per-word timing is interpolated by word length across the line. */
    private static Component words(String line, double frac) {
        String[] w = line.split(" ");
        int total = Math.max(1, line.replace(" ", "").length());
        int acc = 0;
        MutableComponent out = Component.empty();
        for (String word : w) {
            double start = acc / (double) total;
            acc += word.length();
            double end = acc / (double) total;
            ChatFormatting color = frac >= end ? ChatFormatting.AQUA : frac >= start ? ChatFormatting.YELLOW : ChatFormatting.WHITE;
            out.append(Component.literal(word + " ").withStyle(color, ChatFormatting.BOLD));
        }
        return out;
    }
}
