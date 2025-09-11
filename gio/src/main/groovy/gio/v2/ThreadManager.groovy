package gio.v2

class ThreadManager {
    private static final Map<String, HybridThreadPool> pools = [:]

    static HybridThreadPool getPool(String name, int threads = -1) {
        if (!pools.containsKey(name)) {
            int actualThreads = threads > 0 ? threads : Runtime.runtime.availableProcessors()
            pools[name] = new HybridThreadPool(actualThreads)
        }
        return pools[name]
    }

    static void shutdownAll() {
        pools.each { name, pool ->
            println "Shutting down pool: $name"
            pool.shutdown()
        }
        pools.clear()
    }

    static String stats() {
        pools.collect { name, pool ->
            "Pool '$name': ${pool.numCarrierThreads} carrier threads"
        }.join('\n')
    }
}