package retrystorm;

import retrystorm.engine.Simulator;

// Temporary entry point: a deterministic engine smoke run, replaced by the
// scenario runner in a later step.
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Simulator sim = new Simulator(42L);
        for (int tick = 1; tick <= 5; tick++) {
            sim.schedule(tick * 1_000_000L, () -> System.out.println("tick at " + sim.now() + " us"));
        }
        sim.run(5_000_000L);
        System.out.println("engine smoke run complete at " + sim.now() + " us");
    }
}
