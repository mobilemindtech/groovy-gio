package gio.v3

import java.util.concurrent.CountDownLatch

class Runtime {
    private static WorkStealingThreadPool pool = new WorkStealingThreadPool(Runtime.runtime.availableProcessors())
    private static Random random = new Random()

    static <T> IO<List<T>> parMap(List<IO<T>> ios) {
        IO.of({ ->
            def results = Collections.synchronizedList([] as List<T>)
            def latch = new CountDownLatch(ios.size())

            ios.eachWithIndex { io, index ->
                int targetQueue = random.nextInt(pool.numThreads)

                pool.submit(targetQueue, {
                    try {
                        def result = io.run()
                        results[index] = result
                    } catch (Exception e) {
                        results[index] = null
                    } finally {
                        latch.countDown()
                    }
                } as Runnable)
            }

            latch.await()
            return results
        })
    }

    static <T> IO<T> async(IO<T> io) {
        IO.of({ ->
            def result = null
            def latch = new CountDownLatch(1)
            def exception = null

            int targetQueue = random.nextInt(pool.numThreads)

            pool.submit(targetQueue, {
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
}