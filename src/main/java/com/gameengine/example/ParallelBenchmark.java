package com.gameengine.example;

import com.gameengine.components.*;
import com.gameengine.core.*;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;
import java.util.*;

/** 无窗口对照实验：固定种子/时间步/对象规模，测量物理 + O(n²) 避障。 */
public class ParallelBenchmark {
    public static Scene createScene(int count) {
        Scene scene = new Scene("Benchmark");
        Random random = new Random(2026);
        for (int i = 0; i < count; i++) {
            GameObject obj = new GameObject("AIPlayer");
            obj.addComponent(new TransformComponent(new Vector2(30 + random.nextFloat() * 940, 30 + random.nextFloat() * 700)));
            PhysicsComponent physics = obj.addComponent(new PhysicsComponent());
            physics.setFriction(1);
            physics.setVelocity(random.nextFloat() * 100 - 50, random.nextFloat() * 100 - 50);
            scene.addGameObject(obj);
        }
        scene.initialize(); scene.update(0);
        return scene;
    }
    private static double run(boolean parallel, int count, int steps) {
        System.setProperty("game.parallel", Boolean.toString(parallel));
        Scene scene = createScene(count);
        PhysicsSystem physics = new PhysicsSystem(scene, 1024, 768);
        GameLogic logic = new GameLogic(scene);
        try {
            for (int i = 0; i < 100; i++) { logic.handleAIPlayerAvoidance(1f/60); physics.update(1f/60); }
            long start = System.nanoTime();
            for (int i = 0; i < steps; i++) { logic.handleAIPlayerAvoidance(1f/60); physics.update(1f/60); }
            return (System.nanoTime() - start) / 1e6 / steps;
        } finally { logic.cleanup(); physics.cleanup(); }
    }
    public static void main(String[] args) {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 500;
        int steps = args.length > 1 ? Integer.parseInt(args[1]) : 300;
        if (count < 1 || steps < 1) throw new IllegalArgumentException("Positive objects and steps required");
        double serial = run(false, count, steps), parallel = run(true, count, steps);
        System.out.printf(Locale.ROOT, "objects=%d steps=%d serial=%.3f ms/step parallel=%.3f ms/step speedup=%.2fx%n",
            count, steps, serial, parallel, serial / parallel);
        System.out.println("Microbenchmark only; repeat measurements and include your hardware/JDK in the report.");
    }
}
