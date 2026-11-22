package com.gameengine.net;

import com.gameengine.input.InputManager;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/** 客户端使用阻塞 SocketChannel 和独立收发线程；服务端使用 Selector。 */
public class NioClient implements AutoCloseable {
    private SocketChannel channel;
    private BufferedReader reader;
    private BufferedWriter writer;
    private volatile boolean running;
    private Thread sender, receiver;
    public boolean connect(String host, int port) {
        try {
            channel = SocketChannel.open();
            channel.socket().connect(new InetSocketAddress(host, port), 3000);
            channel.socket().setSoTimeout(3000);
            reader = new BufferedReader(new InputStreamReader(channel.socket().getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(channel.socket().getOutputStream(), StandardCharsets.UTF_8));
            NetworkBuffer.clear();
            running = true;
            return true;
        } catch (IOException e) { System.err.println(e.getMessage()); close(); return false; }
    }
    private synchronized void send(String line) throws IOException {
        writer.write(line); writer.newLine(); writer.flush();
    }
    public boolean join(String name) {
        try {
            send("JOIN:" + name.replaceAll("[\\r\\n]", ""));
            if (!"JOIN-ACK".equals(reader.readLine())) { close(); return false; }
            channel.socket().setSoTimeout(0);
            return true;
        } catch (IOException e) { System.err.println(e.getMessage()); close(); return false; }
    }
    public void startInputLoop(InputManager input) {
        if (!running || sender != null) return;
        sender = new Thread(() -> {
            try {
                while (running) {
                    float x = 0, y = 0;
                    if (input.isKeyPressed(87) || input.isKeyPressed(265)) y--;
                    if (input.isKeyPressed(83) || input.isKeyPressed(264)) y++;
                    if (input.isKeyPressed(65) || input.isKeyPressed(263)) x--;
                    if (input.isKeyPressed(68) || input.isKeyPressed(262)) x++;
                    double scale = 200 / Math.max(1, Math.hypot(x, y));
                    send("INPUT:" + (float)(x * scale) + "," + (float)(y * scale));
                    Thread.sleep(50);
                }
            } catch (IOException e) { if (running) System.err.println(e.getMessage()); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { close(); }
        }, "client-input");
        sender.setDaemon(true); sender.start();
    }
    public void startReceiveLoop() {
        if (!running || receiver != null) return;
        receiver = new Thread(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    NetworkBuffer.Keyframe frame = NetworkBuffer.parseJsonLine(line);
                    if (frame != null) NetworkBuffer.push(frame);
                }
            } catch (IOException e) { if (running) System.err.println(e.getMessage()); }
            finally { close(); }
        }, "client-receive");
        receiver.setDaemon(true); receiver.start();
    }
    @Override public void close() {
        running = false;
        try { if (channel != null) channel.close(); } catch (IOException ignored) { }
        if (sender != null && sender != Thread.currentThread()) sender.interrupt();
    }
}
