package com.zagbor;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadFactory implements ThreadFactory {
    private final String poolName;
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    public CustomThreadFactory(String poolName) {
        this.poolName = poolName;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, poolName + "-worker-" + threadNumber.getAndIncrement());
        System.out.printf("[ThreadFactory] Creating new thread: %s%n", thread.getName());
        thread.setUncaughtExceptionHandler((t, e) -> 
            System.out.printf("[Worker] %s encountered exception: %s%n", t.getName(), e));
        return thread;
    }
}