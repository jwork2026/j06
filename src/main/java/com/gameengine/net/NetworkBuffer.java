package com.gameengine.net;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NetworkBuffer {
    private static final Deque<Keyframe> buffer = new ArrayDeque<>();
    private static final Object lock = new Object();
    private static final double MAX_AGE_SEC = 2.0;
    private static final double INTERP_DELAY_SEC = 0.12; // 120ms 缓冲

    public static class Entity {
        public String id; public float x; public float y;
    }
    public static class Keyframe {
        public double t;
        public List<Entity> entities = new ArrayList<>();
    }

    private static long receivedAt;
    public static void clear() { synchronized (lock) { buffer.clear(); } }
    public static void push(Keyframe kf) {
        synchronized (lock) {
            if (!buffer.isEmpty() && kf.t < buffer.peekLast().t) buffer.clear();
            buffer.addLast(kf);
            receivedAt = System.nanoTime();
            // 修剪老帧
            double now = kf.t;
            while (buffer.size() > 2 && now - buffer.peekFirst().t > MAX_AGE_SEC) buffer.pollFirst();
        }
    }

    public static Keyframe parseJsonLine(String line) {
        // 极简 JSON 解析（假设格式固定）：{"type":"kf","t":X,"entities":[{"id":"...","x":N,"y":N},...]}
        if (line == null || !line.contains("\"type\":\"kf\"")) return null;
        Keyframe kf = new Keyframe();
        try {
            String ts = com.gameengine.recording.RecordingJson.field(line, "t");
            kf.t = com.gameengine.recording.RecordingJson.parseDouble(ts);
            int idx = line.indexOf("\"entities\":[");
            if (idx >= 0) {
                int bracket = line.indexOf('[', idx);
                String arr = bracket >= 0 ? com.gameengine.recording.RecordingJson.extractArray(line, bracket) : "";
                String[] parts = com.gameengine.recording.RecordingJson.splitTopLevel(arr);
                for (String p : parts) {
                    Entity e = new Entity();
                    e.id = com.gameengine.recording.RecordingJson.stripQuotes(com.gameengine.recording.RecordingJson.field(p, "id"));
                    e.x = (float)com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(p, "x"));
                    e.y = (float)com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(p, "y"));
                    kf.entities.add(e);
                }
            }
        } catch (Exception ignored) {}
        return kf;
    }

    public static Map<String, float[]> sample() {
        double target;
        Keyframe a = null, b = null;
        synchronized (lock) {
            if (buffer.isEmpty()) return new HashMap<>();
            target = buffer.peekLast().t + (System.nanoTime() - receivedAt) / 1e9 - INTERP_DELAY_SEC;
            a = buffer.peekFirst();
            b = buffer.peekLast();
            for (Keyframe k : buffer) {
                if (k.t <= target) a = k; else { b = k; break; }
            }
        }
        if (a == null) return new HashMap<>();
        if (b == null) b = a;
        double span = Math.max(1e-6, b.t - a.t);
        double u = Math.max(0.0, Math.min(1.0, (target - a.t) / span));
        Map<String, float[]> out = new HashMap<>();
        Map<String, Entity> next = new HashMap<>();
        for (Entity e : b.entities) next.put(e.id, e);
        for (Entity ea : (u >= 1 ? b.entities : a.entities)) {
            Entity eb = next.getOrDefault(ea.id, ea);
            out.put(ea.id, new float[]{(float)(ea.x + u * (eb.x - ea.x)),
                                     (float)(ea.y + u * (eb.y - ea.y))});
        }
        return out;
    }
}


