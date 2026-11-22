package com.gameengine;
import com.gameengine.core.GameEngine;
import com.gameengine.example.GameScene;
import com.gameengine.recording.*;
import java.nio.file.*;

/** Optional real-window smoke test; closes itself after 30 frames. */
public class GraphicsSmoke {
    public static void main(String[] args) throws Exception {
        Path file=Files.createTempFile("j06-graphics-", ".jsonl");
        GameEngine engine = new GameEngine(1024,768,"J06 graphics check");
        try {
            GameScene scene=new GameScene(engine) {
                int frames;
                @Override public void update(float dt) {
                    super.update(dt);
                    if (++frames == 30) engine.stop();
                }
            };
            engine.setScene(scene);
            engine.enableRecording(new RecordingService(new RecordingConfig(file.toString())));
            engine.run();engine.cleanup();
            if (Files.readAllLines(file).size()<2) throw new AssertionError("Missing recording");
            System.out.println("PASS OpenGL window, gameplay frames, recording and cleanup");
        } finally { engine.cleanup();Files.deleteIfExists(file); }
    }
}
