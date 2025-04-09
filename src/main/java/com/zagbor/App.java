package com.zagbor;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);
    private static final int TASK_COUNT = 200;

    public static void main(String[] args) {
        CustomThreadFactory threadFactory = new CustomThreadFactory("MyPool");
        CustomThreadPool threadPool = new CustomThreadPool(4, 8, 5, 5L, TimeUnit.SECONDS, 2, threadFactory, RejectionPolicy.CALLER_RUNS);
        long startTime = System.currentTimeMillis();
        int rejectedTasks = 0;
        for (int i = 1; i <= TASK_COUNT; i++) {
            final int taskId = i;
            try {
                threadPool.execute(() -> {
                    SimpleDateFormat sdf = new SimpleDateFormat("mm минут ss секунд SSS миллисекунуд");
                    long timeStamp = Instant.now().toEpochMilli();
                    log.info("Начало выполнения задачи #{}", taskId);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    log.info("Task {} finished at {}", taskId, sdf.format(Instant.now().toEpochMilli()));

                });
            } catch (RejectedExecutionException e) {
                rejectedTasks++;
            }
        }
        long endTime = System.currentTimeMillis();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        threadPool.shutdown();
        log.info("Pull task completed");
        log.info(toConsole(endTime - startTime, rejectedTasks, TASK_COUNT));
    }

    private static String toConsole(long time, int rejectedTasks, int TASK_COUNT){
        return "Time elapsed = " + time +
                "ms | Skipped tasks = " + rejectedTasks +
                " | Completed tasks = " + (TASK_COUNT - rejectedTasks) +
                " | Average time per task = " + (double) time / (double) (TASK_COUNT - rejectedTasks) + "ms";
    }
}
