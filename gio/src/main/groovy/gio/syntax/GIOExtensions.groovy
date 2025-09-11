package gio.syntax

import gio.core.Result
import gio.io.GIO
import gio.io.IO
import gio.runtime.Runtime

class GIOExtensions {

    static <A> A unsafeRun(IO<A> self) {
        new Runtime()
            .unsafeRunSync(self)
            .ifFailure { Throwable err ->
                println "::> failure $err"
                err.printStackTrace()
            }
            .get()
    }

    static <T> IO<T> getLift(T self) { GIO.pure(self) }

    static <T> Result<T> getLiftResult(Closure<T> self) { Result.of(self) }

}