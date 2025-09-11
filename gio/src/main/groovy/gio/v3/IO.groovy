package gio.v3

import groovy.transform.Canonical

@Canonical
class IO<T> {
    private final Closure<T> computation

    static <T> IO<T> of(Closure<T> computation) {
        return new IO<T>(computation)
    }

    static <T> IO<T> pure(T value) {
        return new IO<T>({ -> value })
    }

    T run() {
        return computation.call()
    }

    <U> IO<U> map(Closure<U> f) {
        return new IO<U>({ -> f.call(this.run()) })
    }

    <U> IO<U> flatMap(Closure<IO<U>> f) {
        return new IO<U>({ -> f.call(this.run()).run() })
    }
}
