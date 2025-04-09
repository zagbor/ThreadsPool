package com.zagbor;

import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.concurrent.TimeUnit.SECONDS;

public class ThreadPoolBenchmark {
    private static final Logger log = LoggerFactory.getLogger(ThreadPoolBenchmark.class);
    private static final int TASK_COUNT = 100;
    private static final int TASK_DURATION_MS = 1000;

    public static void main(String[] args) {
        log.info("Test CustomThreadPool...");
        ParamsTest paramsCustom = testCustomPool();

        log.info("Test ThreadPoolExecutor...");
        ParamsTest paramsStandard = testStandardPool();

        log.info("\n=== Итоги тестирования ===");
        log.info("CustomThreadPool: {}", paramsCustom);
        log.info("ThreadPoolExecutor: {}", paramsStandard);
    }

    private static ParamsTest testCustomPool() {
        CustomThreadFactory threadFactory = new CustomThreadFactory("MyPool");
        CustomThreadPool customPool = new CustomThreadPool(4, 10, 0, 5L, SECONDS, 5, threadFactory,
                RejectionPolicy.ABORT);
        long startTime = System.currentTimeMillis();

        int rejectedTasks = 0;
        for (int i = 0; i < TASK_COUNT; i++) {
            try {
                customPool.execute(createTask());
            } catch (RejectedExecutionException e) {
                log.warn("Task {} rejected due to overload!", Optional.of(i));
                rejectedTasks++;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        long endTime = System.currentTimeMillis();
        customPool.shutdown();

        return new ParamsTest(endTime - startTime, rejectedTasks);
    }

    private static ParamsTest testStandardPool() {
        ThreadPoolExecutor standardPool = new ThreadPoolExecutor(
                4, 10, 5, SECONDS,
                new LinkedBlockingQueue<>(5),
                new ThreadPoolExecutor.AbortPolicy()
        );

        long startTime = System.currentTimeMillis();

        int rejectedTasks = 0;
        for (int i = 0; i < TASK_COUNT; i++) {
            try {
                standardPool.execute(createTask());
            } catch (RejectedExecutionException e) {
                log.warn("Task {} was rejected due to overload!", Optional.of(i));
                rejectedTasks++;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        long endTime = System.currentTimeMillis();
        standardPool.shutdown();
        return new ParamsTest(endTime - startTime, rejectedTasks);
    }

    private static Runnable createTask() {
        return () -> {
            try {
                Thread.sleep(TASK_DURATION_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    private static class ParamsTest {
        long time;
        int rejectedTasks = 0;

        public ParamsTest(long time, int rejectedTasks) {
            this.time = time;
            this.rejectedTasks = rejectedTasks;
        }

        @Override
        public String toString() {
            return "Time elapsed = " + time +
                    "ms | Skipped tasks = " + rejectedTasks +
                    " | Completed tasks = " + (TASK_COUNT - rejectedTasks) +
                    " | Average time per task = " + (double) time / (double) (TASK_COUNT - rejectedTasks) + "ms";
        }
    }
}
