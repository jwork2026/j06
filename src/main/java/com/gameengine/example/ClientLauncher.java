package com.gameengine.example;
import com.gameengine.core.GameEngine;
import com.gameengine.net.NioClient;
public class ClientLauncher {
    public static void main(String[] args) {
        GameEngine engine = null;
        try (NioClient client = new NioClient()) {
            if (!client.connect(args.length > 0 ? args[0] : "127.0.0.1", 7777) || !client.join("Player2"))
                throw new IllegalStateException("Cannot join server");
            engine = new GameEngine(1024, 768, "J06 Client");
            engine.setScene(new GameScene(engine, GameScene.Mode.CLIENT));
            engine.attachClient(client);
            client.startInputLoop(engine.getInputManager());
            client.startReceiveLoop();
            engine.run();
        } finally { if (engine != null) engine.cleanup(); }
    }
}
