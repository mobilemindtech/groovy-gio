package io.gio.types

import gio.core.Result

class When {

    static <T> T when(Closure<Boolean> f, Closure<T> then, Closure<T> else_) {
        if (f()) then() else else_?.call()
    }

    static <T> T when(Boolean b, Closure<T> then, Closure<T> else_) {
        if (b) then() else else_?.call()
    }

    static <T> Result<T> whenResult(Boolean b, Closure<Result<T>> then, Closure<Result<T>> else_) {
        if (b) then() else else_?.call()
    }

    static <T> Result<T> whenResult(Closure<Boolean> f, Closure<Result<T>> then, Closure<Result<T>> else_) {
        if (f()) then() else else_?.call()
    }


}
