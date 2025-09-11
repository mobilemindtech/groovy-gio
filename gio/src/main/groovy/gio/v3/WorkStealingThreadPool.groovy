package gio.v3

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class WorkStealingThreadPool {
    private final BlockingQueue<Runnable>[] queues
    protected final int numThreads
    private final ExecutorService executor
    private volatile boolean running = true

    WorkStealingThreadPool(int numThreads) {
        this.numThreads = numThreads
        this.queues = new ArrayBlockingQueue[numThreads]
        (0..<numThreads).each { i ->
            queues[i] = new LinkedBlockingQueue<Runnable>()
        }
        this.executor = Executors.newFixedThreadPool(numThreads)
        startWorkers()
    }

    private void startWorkers() {
        (0..<numThreads).each { i ->
            executor.submit({ -> workerLoop(i) } as Runnable)
        }
    }

    private void workerLoop(int workerId) {
        while (running) {
            try {
                // Tenta pegar trabalho da própria fila
                Runnable task = queues[workerId].poll()
                if (task != null) {
                    task.run()
                    continue
                }

                // Rouba trabalho de outras filas
                boolean stoleWork = false
                for (int j = 0; j < numThreads; j++) {
                    if (j != workerId) {
                        task = queues[j].poll()
                        if (task != null) {
                            task.run()
                            stoleWork = true
                            break
                        }
                    }
                }

                if (!stoleWork) {
                    // Se não roubou trabalho, espera um pouco
                    Thread.sleep(10)
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }

    void submit(int queueId, Runnable task) {
        queues[queueId].offer(task)
    }

    void shutdown() {
        running = false
        executor.shutdown()
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS)
        } catch (InterruptedException ignored) {
            executor.shutdownNow()
        }
    }
}