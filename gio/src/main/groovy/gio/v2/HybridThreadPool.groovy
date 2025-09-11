package gio.v2

import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock

class HybridThreadPool {
    private final ExecutorService carrierThreadPool
    private final ExecutorService virtualThreadExecutor
    private final BlockingQueue<Runnable> workQueue
    private final int numCarrierThreads
    private volatile boolean running = true
    private final ReentrantLock lock = new ReentrantLock()

    HybridThreadPool(int numCarrierThreads) {
        this.numCarrierThreads = numCarrierThreads
        this.carrierThreadPool = Executors.newFixedThreadPool(numCarrierThreads)
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor()
        this.workQueue = new LinkedBlockingQueue<Runnable>()
        startCarrierThreads()
    }

    private void startCarrierThreads() {
        (0..<numCarrierThreads).each { i ->
            carrierThreadPool.submit({ -> carrierThreadLoop(i) } as Runnable)
        }
    }

    private void carrierThreadLoop(int threadId) {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Runnable task = workQueue.poll(100, TimeUnit.MILLISECONDS)
                if (task != null) {
                    // Executa tarefas pesadas (CPU-bound) diretamente
                    if (isCpuBoundTask(task)) {
                        task.run()
                    } else {
                        // Delega I/O bound para virtual threads
                        virtualThreadExecutor.submit(task)
                    }
                }

                // Work stealing entre carrier threads
                if (workQueue.isEmpty()) {
                    stealWorkFromOtherCarriers(threadId)
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }

    private boolean isCpuBoundTask(Runnable task) {
        // Heurística simples: tarefas que não dormem são CPU-bound
        // Em produção, usar anotações ou metadata mais sofisticada
        return !task.toString().toLowerCase().contains('sleep') &&
            !task.toString().toLowerCase().contains('io') &&
            !task.toString().toLowerCase().contains('wait')
    }

    private void stealWorkFromOtherCarriers(int thiefThreadId) {
        // Implementação simplificada de work stealing
        // Em produção, usar estrutura de dados mais eficiente
        lock.lock()
        try {
            if (!workQueue.isEmpty()) {
                Runnable stolenTask = workQueue.poll()
                if (stolenTask != null) {
                    stolenTask.run()
                }
            }
        } finally {
            lock.unlock()
        }
    }

    void submit(Runnable task) {
        workQueue.offer(task)
    }

    void submitVirtual(Runnable task) {
        virtualThreadExecutor.submit(task)
    }

    <T> CompletableFuture<T> submitVirtualCallable(Callable<T> task) {
        def future = new CompletableFuture<T>()
        virtualThreadExecutor.submit({
            try {
                future.complete(task.call())
            } catch (Exception e) {
                future.completeExceptionally(e)
            }
        } as Runnable)
        return future
    }

    void shutdown() {
        running = false
        carrierThreadPool.shutdown()
        virtualThreadExecutor.shutdown()

        try {
            carrierThreadPool.awaitTermination(5, TimeUnit.SECONDS)
            virtualThreadExecutor.awaitTermination(5, TimeUnit.SECONDS)
        } catch (InterruptedException e) {
            carrierThreadPool.shutdownNow()
            virtualThreadExecutor.shutdownNow()
        }
    }
}