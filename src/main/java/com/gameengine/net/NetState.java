package com.gameengine.net;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class NetState {
    private static final AtomicInteger clientCount = new AtomicInteger(0);
    private static volatile float[] p2velocity = new float[]{0, 0};
    private static volatile String lastState = null; // 旧文本
    private static volatile String lastKeyframeJson = null; // 新JSON KF
    private static final ConcurrentHashMap<String, float[]> clientMirror = new ConcurrentHashMap<>();
    private NetState() {}
    public static void clientConnected() { clientCount.incrementAndGet(); }
    public static int getClientCount() { return clientCount.get(); }
    public static boolean hasClient() { return clientCount.get() > 0; }
    public static void setP2Velocity(float vx, float vy) { p2velocity = new float[]{vx, vy}; }
    public static float[] getP2Velocity() { return p2velocity.clone(); }
    public static void clientDisconnected() {
        clientCount.updateAndGet(n -> Math.max(0, n - 1));
        setP2Velocity(0, 0);
    }

    // server 侧设置当前状态（文本行，形如 STATE:id,x,y;id2,x,y）
    public static void setLastState(String s) { lastState = s; }
    public static String getLastState() { return lastState; }
    public static void setLastKeyframeJson(String s) { lastKeyframeJson = s; }
    public static String getLastKeyframeJson() { return lastKeyframeJson; }

    // client 侧接收状态并更新镜像
    public static void updateMirrorFromState(String line) {
        if (line == null) return;
        if (!line.startsWith("STATE:")) return;
        String payload = line.substring(6).trim();
        if (payload.isEmpty()) return;
        String[] ents = payload.split(";");
        for (String e : ents) {
            String[] parts = e.split(",");
            if (parts.length < 3) continue;
            String id = parts[0];
            try {
                float x = Float.parseFloat(parts[1]);
                float y = Float.parseFloat(parts[2]);
                clientMirror.put(id, new float[]{x,y});
            } catch (Exception ignored) {}
        }
    }

    public static java.util.Map<String, float[]> getMirrorSnapshot() {
        return new java.util.HashMap<>(clientMirror);
    }
}


