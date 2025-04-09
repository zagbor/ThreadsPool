package com.zagbor;

import java.util.concurrent.RejectedExecutionException;

@FunctionalInterface
public interface RejectionPolicy {
    void reject(Runnable task, CustomThreadPool pool);

    // Стандартные политики отказа
    RejectionPolicy ABORT = (task, pool) -> {
        System.out.printf("[Rejected] Task %s was rejected due to overload!%n", task);
        throw new RejectedExecutionException("Task " + task + " rejected from " + pool);
    };

    RejectionPolicy CALLER_RUNS = (task, pool) -> {
        System.out.printf("[Rejected] Task %s executed in caller thread due to overload%n", task);
        task.run();
    };

    RejectionPolicy DISCARD = (task, pool) ->
            System.out.printf("[Rejected] Task %s was discarded due to overload%n", task);

    RejectionPolicy DISCARD_OLDEST = (task, pool) -> {
        Runnable discarded = pool.pollOldestTask();
        if (discarded != null) {
            System.out.printf("[Rejected] Task %s was discarded due to overload%n", discarded);
        }
        if (!pool.offerToAnyQueue(task)) {
            ABORT.reject(task, pool);
        }
    };
}