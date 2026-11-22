package com.gameengine.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 单 Reactor：每连接独立读缓冲、待写队列；仅第一个 JOIN 客户端控制 Player2。 */
public class NioServer implements Runnable {
    private final int port;
    private volatile boolean running;
    private Selector selector;
    private ServerSocketChannel listener;
    private Thread thread;
    private Peer controller;
    private static final int MAX_PENDING = 1024 * 1024;
    private static class Peer {
        final SocketChannel channel;
        final StringBuilder input = new StringBuilder();
        final Deque<ByteBuffer> output = new ArrayDeque<>();
        int pending;
        boolean joined;
        Peer(SocketChannel channel) { this.channel = channel; }
    }
    public NioServer(int port) { this.port = port; }
    public int getPort() {
        try { return ((InetSocketAddress) listener.getLocalAddress()).getPort(); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }
    public void start() {
        if (running) return;
        try {
            selector = Selector.open();
            listener = ServerSocketChannel.open();
            listener.configureBlocking(false);
            listener.bind(new InetSocketAddress(port));
            listener.register(selector, SelectionKey.OP_ACCEPT);
            running = true;
            thread = new Thread(this, "nio-server");
            thread.setDaemon(true);
            thread.start();
        } catch (IOException e) {
            try { if (listener != null) listener.close(); if (selector != null) selector.close(); }
            catch (IOException ignored) { }
            throw new IllegalStateException("Cannot bind server port " + port, e);
        }
    }
    public void stop() {
        running = false;
        if (selector != null && selector.isOpen()) selector.wakeup();
        if (thread != null && thread != Thread.currentThread()) {
            try { thread.join(2000); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
    private void close(SelectionKey key) {
        Peer p = (Peer) key.attachment();
        if (p != null && p.joined) { p.joined = false; NetState.clientDisconnected(); }
        if (p == controller) { controller = null; NetState.setP2Velocity(0, 0); }
        key.cancel();
        try { key.channel().close(); } catch (IOException ignored) { }
    }
    private void enqueue(SelectionKey key, String line) throws IOException {
        Peer p = (Peer) key.attachment();
        byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
        if (p.pending + bytes.length > MAX_PENDING) throw new IOException("Slow client queue full");
        p.output.add(ByteBuffer.wrap(bytes)); p.pending += bytes.length;
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
    }
    private void handle(SelectionKey key, String line) throws IOException {
        Peer p = (Peer) key.attachment();
        if (line.startsWith("JOIN:") && !p.joined) {
            p.joined = true; NetState.clientConnected();
            if (controller == null) controller = p;
            enqueue(key, "JOIN-ACK");
        } else if (line.startsWith("INPUT:") && p == controller) {
            String[] fields = line.substring(6).split(",");
            if (fields.length != 2) return;
            try {
                float x = Float.parseFloat(fields[0]), y = Float.parseFloat(fields[1]);
                if (!Float.isFinite(x) || !Float.isFinite(y)) return;
                double scale = Math.max(1, Math.hypot(x, y) / 200);
                NetState.setP2Velocity((float)(x / scale), (float)(y / scale));
            } catch (NumberFormatException ignored) { }
        }
    }
    @Override public void run() {
        ByteBuffer bytes = ByteBuffer.allocate(4096);
        long broadcast = System.nanoTime();
        try {
            while (running) {
                selector.select(20);
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next(); iterator.remove();
                    if (!key.isValid()) continue;
                    try {
                        if (key.isAcceptable()) {
                            SocketChannel channel = listener.accept();
                            if (channel != null) {
                                channel.configureBlocking(false);
                                channel.register(selector, SelectionKey.OP_READ, new Peer(channel));
                            }
                            continue;
                        }
                        Peer p = (Peer) key.attachment();
                        if (key.isReadable()) {
                            bytes.clear(); int n = p.channel.read(bytes);
                            if (n < 0) { close(key); continue; }
                            // Client commands use ASCII; retain incomplete lines between reads.
                            if (n > 0) p.input.append(new String(bytes.array(), 0, n, StandardCharsets.UTF_8));
                            int end;
                            while ((end = p.input.indexOf("\n")) >= 0) {
                                String line = p.input.substring(0, end).trim();
                                p.input.delete(0, end + 1); handle(key, line);
                            }
                            if (p.input.length() > 8192) throw new IOException("Input line too long");
                        }
                        if (key.isWritable()) {
                            while (!p.output.isEmpty()) {
                                ByteBuffer out = p.output.peek();
                                int n = p.channel.write(out); p.pending -= n;
                                if (out.hasRemaining()) break; // Wait for next OP_WRITE, never spin.
                                p.output.remove();
                            }
                            if (p.output.isEmpty()) key.interestOps(SelectionKey.OP_READ);
                        }
                    } catch (IOException | CancelledKeyException e) { close(key); }
                }
                if (System.nanoTime() - broadcast >= 50_000_000L) {
                    broadcast = System.nanoTime();
                    String frame = NetState.getLastKeyframeJson();
                    if (frame != null) {
                        for (SelectionKey key : new ArrayList<>(selector.keys())) {
                            if (key.isValid() && key.attachment() instanceof Peer && ((Peer)key.attachment()).joined) {
                                try { enqueue(key, frame); } catch (IOException e) { close(key); }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (running) e.printStackTrace();
        } finally {
            for (SelectionKey key : new ArrayList<>(selector.keys())) close(key);
            try { selector.close(); } catch (IOException ignored) { }
            running = false;
        }
    }
}
