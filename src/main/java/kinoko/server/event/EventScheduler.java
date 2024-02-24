package kinoko.server.event;

import java.util.concurrent.*;

public final class EventScheduler {
    private static ScheduledExecutorService scheduler;
    private static ExecutorService executor;

    public static void initialize() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public static ScheduledFuture<?> addEvent(Runnable runnable, long delay) {
        return scheduler.schedule(runnable, delay, TimeUnit.MILLISECONDS);
    }

    public static ScheduledFuture<?> addEvent(Runnable runnable, long delay, TimeUnit timeUnit) {
        return scheduler.schedule(() -> executor.submit(runnable), delay, timeUnit);
    }

    public static ScheduledFuture<?> scheduleWithFixedDelay(Runnable runnable, long initialDelay, long delay, TimeUnit timeUnit) {
        return scheduler.scheduleWithFixedDelay(() -> executor.submit(runnable), initialDelay, delay, timeUnit);
    }
}
