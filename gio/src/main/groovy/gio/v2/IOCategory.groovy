package gio.v2

class IOCategory {
    static <T> IO<T> virtual(IO<T> self) {
        return Runtime.async(self)
    }

    static <T> IO<T> cpuBound(IO<T> self) {
        return Runtime.asyncCpuBound(self)
    }

    static <T> IO<List<T>> par(List<IO<T>> self) {
        return Runtime.parMap(self)
    }
}
