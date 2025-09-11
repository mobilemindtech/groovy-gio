package gio.v3

class IOCategory {
    static <T, U> IO<U> then(IO<T> self, Closure<IO<U>> f) {
        return self.flatMap(f)
    }

    static <T, U> IO<U> andThen(IO<T> self, Closure<U> f) {
        return self.map(f)
    }

    static <T> IO<List<T>> sequence(List<IO<T>> ios) {
        return ios.inject(IO.pure([])) { acc, io ->
            acc.flatMap { list ->
                io.map { value ->
                    list + value
                }
            }
        }
    }
}