package com.gameengine;

import com.gameengine.components.*;
import com.gameengine.core.*;
import com.gameengine.example.*;
import com.gameengine.graphics.IRenderer;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.net.*;
import com.gameengine.recording.*;
import com.gameengine.scene.Scene;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Headless integration checks; no external test framework required. */
public class IntegrationChecks {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void near(float a, float b) { check(Math.abs(a-b) < 0.001, a + " != " + b); }
    static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        check(condition.getAsBoolean(), "Condition timed out");
    }
    static void parallel() {
        Scene serial = ParallelBenchmark.createScene(30), parallel = ParallelBenchmark.createScene(30);
        PhysicsSystem a = new PhysicsSystem(serial,1024,768), b = new PhysicsSystem(parallel,1024,768);
        GameLogic la = new GameLogic(serial), lb = new GameLogic(parallel);
        try {
            for (int i=0;i<20;i++) {
                System.setProperty("game.parallel","false"); la.handleAIPlayerAvoidance(1f/60); a.update(1f/60);
                System.setProperty("game.parallel","true"); lb.handleAIPlayerAvoidance(1f/60); b.update(1f/60);
            }
            for(int i=0;i<30;i++) {
                Vector2 x=serial.getGameObjects().get(i).getComponent(TransformComponent.class).getPosition();
                Vector2 y=parallel.getGameObjects().get(i).getComponent(TransformComponent.class).getPosition();
                near(x.x,y.x);near(x.y,y.y);
            }
        } finally { a.cleanup();b.cleanup();la.cleanup();lb.cleanup(); }
    }
    static class NoWindow implements IRenderer {
        public void beginFrame() {} public void endFrame() {} public void pollEvents() {} public void cleanup() {}
        public boolean shouldClose(){ return false; }
        public int getWidth(){ return 1024; } public int getHeight(){return 768;} public String getTitle(){return "test";}
        public void drawRect(float x,float y,float w,float h,float r,float g,float b,float a){}
        public void drawCircle(float x,float y,float radius,int n,float r,float g,float b,float a){}
        public void drawLine(float x,float y,float xx,float yy,float r,float g,float b,float a){}
        public void drawText(float x,float y,String s,float r,float g,float b,float a){}
    }
    static void recordingAndReplay() throws Exception {
        Path file=Files.createTempFile("j06-check-", ".jsonl");
        Locale previous = Locale.getDefault(); Locale.setDefault(Locale.GERMANY);
        Scene scene=ParallelBenchmark.createScene(2);
        RecordingConfig config=new RecordingConfig(file.toString()); config.queueCapacity=1;
        RecordingService service=new RecordingService(config);
        GameEngine engine=new GameEngine(new NoWindow());
        try {
            service.start(scene,1024,768);
            service.update(0.5,scene,InputManager.getInstance());
            GameObject added=new GameObject("AIPlayer");
            added.addComponent(new TransformComponent(new Vector2(91.25f,80)));
            RenderComponent render=added.addComponent(new RenderComponent(RenderComponent.RenderType.RECTANGLE,
                new Vector2(20,20),new RenderComponent.Color(0.1f,0.2f,0.3f,1)));
            scene.addGameObject(added);scene.update(0);
            service.update(0.5,scene,InputManager.getInstance());
            scene.getGameObjects().get(0).setActive(false);scene.update(0);
            service.update(0.5,scene,InputManager.getInstance());service.stop();
            List<String> lines=Files.readAllLines(file);
            check(lines.size()>=5,"Header and final keyframe must be drained");
            check(lines.get(2).contains("91.25"),"JSON must be locale independent");
            check("[0.1,0.2,0.3,1]".equals(RecordingJson.field("{\"color\":[0.1,0.2,0.3,1]}","color")),"Array parser");
            ReplayScene replay=new ReplayScene(engine,file.toString());
            replay.initialize();replay.update(1.5f);replay.render();
            check(replay.getGameObjects().size()==2,"Spawn/despawn in replay");
            GameObject visual=replay.getGameObjects().stream().filter(o->o.getName().equals(added.getEntityId())).findFirst().orElseThrow();
            near(visual.getComponent(TransformComponent.class).getPosition().x,91.25f);
            near(visual.getComponent(RenderComponent.class).getColor().g,0.2f);
            // Same recorder can be reused: time resets, and no previous scene data leaks.
            service.start(scene,1024,768);service.update(0.5,scene,InputManager.getInstance());service.stop();
            check(Files.readAllLines(file).get(1).contains("\"t\":0.5"),"Restart clock");
        } finally { service.stop();engine.cleanup();Locale.setDefault(previous);Files.deleteIfExists(file); }
    }
    static NetworkBuffer.Entity entity(String id,float x) {
        NetworkBuffer.Entity e=new NetworkBuffer.Entity();e.id=id;e.x=x;return e;
    }
    static void network() throws Exception {
        NetworkBuffer.clear();
        NetworkBuffer.Keyframe a=new NetworkBuffer.Keyframe();a.t=10;a.entities.add(entity("a",0));a.entities.add(entity("b",100));
        NetworkBuffer.Keyframe b=new NetworkBuffer.Keyframe();b.t=10.24;b.entities.add(entity("b",110));b.entities.add(entity("a",10));
        NetworkBuffer.push(a);NetworkBuffer.push(b);
        Map<String,float[]> sample=NetworkBuffer.sample();
        check(sample.get("a")[0] < 11 && sample.get("b")[0] >= 100,"Interpolate by ID, not array position or local clock");
        NioServer server=new NioServer(0);server.start();
        try {
            try(Socket socket=new Socket("127.0.0.1",server.getPort())) {
                socket.setSoTimeout(3000);
                OutputStream out=socket.getOutputStream();
                BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                out.write("JO".getBytes(StandardCharsets.UTF_8));out.flush();Thread.sleep(30);
                out.write("IN:test\nINPUT:12,".getBytes(StandardCharsets.UTF_8));out.flush();Thread.sleep(30);
                out.write("34\nINPUT:56,78\n".getBytes(StandardCharsets.UTF_8));out.flush();
                check("JOIN-ACK".equals(in.readLine()),"Split JOIN line");
                await(()->NetState.getP2Velocity()[0]==56);
                near(NetState.getP2Velocity()[1],78);
                NetState.setLastKeyframeJson("{\"type\":\"kf\",\"t\":10,\"entities\":[{\"id\":\"a\",\"x\":1,\"y\":2}]}");
                check(in.readLine().contains("\"entities\""),"Broadcast");
            }
            await(()->NetState.getClientCount()==0);near(NetState.getP2Velocity()[0],0);
            try(NioClient client=new NioClient()) {
                check(client.connect("127.0.0.1",server.getPort()) && client.join("Player2"),"Real client handshake");
                client.startReceiveLoop();
                await(()->NetworkBuffer.sample().containsKey("a"));
            }
            await(()->NetState.getClientCount()==0);
        } finally { server.stop();NetState.setLastKeyframeJson(null); }
    }
    public static void main(String[] args) throws Exception {
        parallel();System.out.println("PASS serial/parallel result equivalence");
        recordingAndReplay();System.out.println("PASS recording drain, locale, reuse, replay identity/appearance/spawn/despawn");
        network();System.out.println("PASS TCP framing, broadcast, disconnect, client handshake, interpolation");
    }
}
