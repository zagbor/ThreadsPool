package com.zagbor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomThreadPool implements CustomExecutor {
    private static final Logger log = LoggerFactory.getLogger(CustomThreadPool.class);

    // Конфигурационные параметры
    private final long keepAliveTime;
    private final TimeUnit timeUnit;

    // Компоненты пула
    private final List<Worker> workers;
    private final List<BlockingQueue<Runnable>> taskQueues;
    private final CustomThreadFactory threadFactory;
    private final RejectionPolicy rejectionPolicy;

    // Состояние
    private volatile boolean isShutdown;
    private final AtomicInteger totalTasks = new AtomicInteger(0);
    private int currentQueueIndex = 0;
    private volatile boolean isInterrupted = false;

    public CustomThreadPool(int corePoolSize, int maxPoolSize, int minSpareThreads,
                            long keepAliveTime, TimeUnit timeUnit, int queueSize,
                            CustomThreadFactory threadFactory, RejectionPolicy rejectionPolicy) {

        // Валидация параметров
        if (corePoolSize <= 0 || maxPoolSize < corePoolSize || minSpareThreads < 0 ||
                minSpareThreads > corePoolSize || queueSize <= 0) {
            throw new IllegalArgumentException("Invalid thread pool parameters");
        }

        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.threadFactory = threadFactory;
        this.rejectionPolicy = rejectionPolicy;

        this.workers = new ArrayList<>(maxPoolSize);
        this.taskQueues = new ArrayList<>(corePoolSize);
        this.isShutdown = false;

        // Инициализация очередей и рабочих потоков
        for (int i = 0; i < corePoolSize; i++) {
            BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(queueSize);
            taskQueues.add(queue);
            createWorker(queue);
        }
    }

    @Override
    public void execute(Runnable command) {
        if (isShutdown) {
            log.error("ThreadPool is shutting down - rejecting task {}", command);
            throw new IllegalStateException("Pool is shutting down");
        }

        totalTasks.incrementAndGet();

        // Round-Robin распределение
        BlockingQueue<Runnable> targetQueue = taskQueues.get(
                currentQueueIndex++ % taskQueues.size()
        );

        if (!targetQueue.offer(command)) {
            log.warn("Queue {} is full (size: {})",
                    currentQueueIndex, targetQueue.size());
            rejectionPolicy.reject(command, this);
        } else {
            log.debug("Task routed to queue {} (size: {})",
                    currentQueueIndex, targetQueue.size());
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        FutureTask<T> futureTask = new FutureTask<>(callable);
        execute(futureTask);
        return futureTask;
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        log.info("[Pool] Shutdown initiated");
    }

    @Override
    public void shutdownNow() {
        isShutdown = true; // Атомарно устанавливаем флаг завершения

        // 1. Прерываем все рабочие потоки
        for (Worker worker : workers) {
            worker.interrupt();
        }

        // 2. Очищаем все очереди задач
        for (BlockingQueue<Runnable> queue : taskQueues) {
            queue.clear();
        }

        // 3. Собираем невыполненные задачи для возврата
        List<Runnable> drainedTasks = new ArrayList<>();
        for (BlockingQueue<Runnable> queue : taskQueues) {
            queue.drainTo(drainedTasks);
        }

        log.info("ShutdownNow completed. Interrupted {} workers, discarded {} tasks",
                workers.size(), drainedTasks.size());

        // Можно добавить возврат невыполненных задач:
        // return drainedTasks;
    }

    private void createWorker(BlockingQueue<Runnable> assignedQueue) {
        Worker worker = new Worker(assignedQueue);
        Thread thread = threadFactory.newThread(worker);
        worker.bindThread(thread);
        workers.add(worker);
        thread.start();
    }

    public boolean offerToAnyQueue(Runnable task) {
        for (BlockingQueue<Runnable> queue : taskQueues) {
            if (queue.offer(task)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Извлекает самую старую задачу из всех очередей
     */
    public Runnable pollOldestTask() {
        for (BlockingQueue<Runnable> queue : taskQueues) {
            Runnable task = queue.poll();
            if (task != null) {
                return task;
            }
        }
        return null;
    }

    private class Worker implements Runnable {
        private final BlockingQueue<Runnable> myQueue;
        private Thread thread;

        Worker(BlockingQueue<Runnable> queue) {
            this.myQueue = queue;
        }

        void bindThread(Thread thread) {
            this.thread = thread;
        }

        void interrupt() {
            if (thread != null) {
                thread.interrupt();
            }
        }

        @Override
        public void run() {
            try {
                while (!isInterrupted && (!isShutdown || !myQueue.isEmpty())) {
                    Runnable task = myQueue.poll(keepAliveTime, timeUnit);
                    if (task != null) {
                        task.run();
                    }
                }
            } catch (InterruptedException e) {
                isInterrupted = true;
                Thread.currentThread().interrupt();
            } finally {
                workers.remove(this);
                log.info("Worker {} shutdown completed", thread.getName());
            }
        }
    }
}
