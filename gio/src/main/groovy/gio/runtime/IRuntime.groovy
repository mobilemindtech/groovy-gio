package gio.runtime

import gio.core.Result
import gio.io.IO

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

trait IRuntime {
    def <A> Result<A> unsafeRunSync(IO<A> io, long timeout = Long.MAX_VALUE, TimeUnit timeUnit = TimeUnit.MILLISECONDS){
        unsafeRunToFuture(io).get(timeout, timeUnit)
    }

    def <A> Future<Result<A>> unsafeRunToFuture(IO<A> io) {
        final future = new CompletableFuture<Result<A>>()
        unsafeRunAsync(io){
            future.complete(it)
        }
        future
    }

    abstract  <A> void unsafeRunAsync(IO<A> io, Consumer<Result<A>> callback)
}