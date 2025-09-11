package gio.v2

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

class Runtime {
    private static HybridThreadPool pool = new HybridThreadPool(Runtime.runtime.availableProcessors())

    static <T> IO<List<T>> parMap(List<IO<T>> ios) {
        return new IO<List<T>>({ ->
            def futures = ios.collect { io ->
                pool.submitVirtualCallable({ -> io.run() } as Callable<T>)
            }

            // Espera todas as futures completarem
            CompletableFuture.allOf(*futures.toArray(new CompletableFuture[0])).join()

            return futures.collect { it.get() }
        })
    }

    static <T> IO<T> async(IO<T> io) {
        return new IO<T>({ ->
            def future = pool.submitVirtualCallable({ -> io.run() } as Callable<T>)
            return future.get()
        })
    }

    static <T> IO<T> asyncCpuBound(IO<T> io) {
        return new IO<T>({ ->
            def latch = new CountDownLatch(1)
            def result = null
            def exception = null

            pool.submit({
                try {
                    result = io.run()
                } catch (Exception e) {
                    exception = e
                } finally {
                    latch.countDown()
                }
            } as Runnable)

            latch.await()
            if (exception != null) {
                throw exception
            }
            return result
        })
    }

    static void shutdown() {
        pool.shutdown()
    }

    // Método para obter estatísticas do pool
    static String getPoolStats() {
        return "Hybrid Pool - Carrier Threads: ${pool.numCarrierThreads}"
    }
}