package gio

import gio.core.GIOEmptyException
import gio.core.GIOException
import gio.core.Option
import gio.core.Try

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

import static gio.core.Try.tryOf


trait IRuntime {
    def <A> Try<A> unsafeRunSync(IO<A> io, long timeout = Long.MAX_VALUE, TimeUnit timeUnit = TimeUnit.MILLISECONDS){
            unsafeRunToFuture(io).get(timeout, timeUnit)
    }

    def <A> Future<Try<A>> unsafeRunToFuture(IO<A> io) {
        final future = new CompletableFuture<Try<A>>()
        unsafeRunAsync(io){
            future.complete(it)
        }
        future
    }

    abstract  <A> void unsafeRunAsync(IO<A> io, Consumer<Try<A>> callback)
}

class Runtime implements IRuntime {


    @Override
    <A> void unsafeRunAsync(IO<A> io, Consumer<Try<A>> callback) {
        new FiberRuntime(io)
            .onDone(callback)
            .start()
    }

    private static class FiberRuntime implements GIO.Fiber {

        private final executor = Executors.newVirtualThreadPerTaskExecutor()
        private IO io

        FiberRuntime(IO io){
            this.io = io
        }

        private final joined = new AtomicReference<Set<Consumer<Try>>>([] as Set)
        // cached fiber result
        private final result = new AtomicReference<Option<Try>>(Option.ofNone())

        @Override
        FiberRuntime onDone(Consumer<Try> done) {
            joined.updateAndGet {it + done }
            result.get().foreach(done)
            this
        }

        def fiberDone(Try res){
            result.set Option.ofSome(res)
            joined.get().forEach {it.accept(res)}
        }

        FiberRuntime start() {
            eval(io) { fiberDone(it) }
            this
        }

        void eval(IO io, Consumer<Try> done) {
            executor.submit {
                switch (io) {
                    case GIO.Effect ->
                        done.accept(tryOf io.&getValue)
                    case GIO.Filter ->
                        eval(io.ref) { result ->
                            switch (result) {
                                case Try.Success ->
                                    if(io.apply(result.get())) done.accept(result)
                                    else done.accept Try.ofFailure(new GIOEmptyException())
                                case Try.Failure ->
                                    done.accept(Try.ofFailure(result.failure))
                            }
                        }
                    case GIO.Guard ->
                        eval(io.ref) { result ->
                            switch (result) {
                                case Try.Success ->
                                    if(io.apply()) done.accept(result)
                                    else done.accept(Try.ofFailure(new GIOEmptyException()))
                                case Try.Failure ->
                                    done.accept Try.ofFailure(result.failure)
                            }
                        }
                    case GIO.FlatMap ->
                        eval(io.ref) { result ->
                            switch (result) {
                                case Try.Success ->
                                    eval(io.apply(result.get()), done)
                                case Try.Failure ->
                                    done.accept Try.ofFailure(result.failure)
                            }
                        }
                    case GIO.AndThan ->
                        eval(io.ref) { result ->
                            switch (result) {
                                case Try.Success ->
                                    eval(io.otherIO, done)
                                case Try.Failure ->
                                    done.accept Try.ofFailure(result.failure)
                            }
                        }
                    case GIO.OrElse ->
                        eval(io.ref) { result ->
                            switch (result) {
                                case Try.Success ->
                                    done.accept(result)
                                case Try.Failure ->
                                    switch (result.failure) {
                                        case GIOEmptyException ->
                                            eval(io.otherIO, done)
                                        default ->
                                            done.accept Try.ofFailure(result.failure)
                                    }
                            }
                        }
                    case GIO.Fail ->
                        done.accept(Try.ofFailure(io.failure))
                    case GIO.FailWith ->
                        done.accept(Try.ofFailure(io.apply()))
                    case GIO.Recover ->
                        eval(io.ref) { result ->
                            switch (result) {
                                case Try.Success ->
                                    done.accept(result)
                                case Try.Failure ->
                                    eval(io.apply(result.failure), done)
                            }
                        }
                    case GIO.EffectAsync ->
                        io.apply(done)
                    case GIO.Fork ->
                        done.accept(Try.ofSuccess(new FiberRuntime(io).start()))
                    case GIO.Join ->
                        io.fiber.onDone(done)
                    default ->
                        done.accept Try.ofFailure(new GIOException("IO type unhandled"))
                }
            }
        }
    }

    static <T> T debug(T value, String msg) {
        println msg
        value
    }
}

trait GIOApp<A> {
    abstract IO<A> run()

    A exec() {
        new Runtime().unsafeRunSync(run()).get()
    }

    Try<A> tryExec() {
        new Runtime().unsafeRunSync(run())
    }
}

