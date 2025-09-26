package io.gio.types

class When {

    static <T> T when(Closure<Boolean> f, Closure<T> then, Closure<T> else_) {
        if (f()) then() else else_?.call()
    }


}
