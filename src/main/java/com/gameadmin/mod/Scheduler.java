package com.gameadmin.mod;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/** Tiny "run this later" helper (real-time milliseconds), driven by the server tick. */
public final class Scheduler {
    private Scheduler() {}

    private static final class Task {
        final long due;
        final Runnable action;

        Task(long due, Runnable action) {
            this.due = due;
            this.action = action;
        }
    }

    private static final List<Task> TASKS = new ArrayList<>();

    public static void after(long millis, Runnable action) {
        TASKS.add(new Task(System.currentTimeMillis() + millis, action));
    }

    /** Called every server tick. */
    public static void tick(MinecraftServer server) {
        if (TASKS.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<Task> due = new ArrayList<>();
        for (Task t : TASKS) {
            if (t.due <= now) due.add(t);
        }
        TASKS.removeAll(due);
        for (Task t : due) {
            try {
                t.action.run();
            } catch (Exception ex) {
                GameAdminMod.LOGGER.warn("Scheduled task failed", ex);
            }
        }
    }
}
