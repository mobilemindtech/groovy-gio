package gio.runtime

import gio.core.GIOEmptyException
import gio.core.GIOException
import gio.core.Option
import gio.core.Result
import gio.io.GIO
import gio.io.IO

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

import static gio.core.Result.tryOf




class Runtime implements IRuntime {


    @Override
    <A> void unsafeRunAsync(IO<A> io, Consumer<Result<A>> callback) {
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

        private final joined = new AtomicReference<Set<Consumer<Result>>>([] as Set)
        // cached fiber result
        private final result = new AtomicReference<Option<Result>>(Option.ofNone())

        @Override
        FiberRuntime onDone(Consumer<Result> done) {
            joined.updateAndGet {it + done }
            result.get().foreach(done)
            this
        }

        def fiberDone(Result res){
            result.set Option.ofSome(res)
            joined.get().forEach {it.accept(res)}
        }

        FiberRuntime start() {
            eval(io) { fiberDone(it) }
            this
        }

        void eval(IO io, Consumer<Result> done) {
            executor.submit {
                try{
                    evalIO(io, done)
                }catch (Throwable throwable){
                    done.accept(Result.ofFailure(throwable))
                }
            }
        }

        void evalIO(IO io, Consumer<Result> done){
            switch (io) {
                case GIO.Pure ->
                    done.accept(Result.ofOk(io.value))
                case GIO.Effect ->
                    done.accept(tryOf(io.&apply))
                case GIO.Attempt ->
                    done.accept(tryOf(io.&apply))
                case GIO.Filter ->
                    eval(io.ref) { result ->
                        switch (result) {
                            case Result.Ok ->
                                if(io.apply(result.value)) done.accept(result)
                                else done.accept Result.ofFailure(new GIOEmptyException())
                            case Result.Failure ->
                                done.accept(Result.ofFailure(result.failure))
                        }
                    }
                case GIO.FilterWith ->
                    eval(io.ref) { result ->
                        switch (result) {
                            case Result.Ok ->
                                eval(io.apply(result.value), {
                                    switch(it){
                                        case Result.Ok ->
                                            if(it.value) done.accept(result)
                                            else done.accept Result.ofFailure(new GIOEmptyException())
                                        case Result.Failure ->
                                            done.accept(Result.ofFailure(it.failure))
                                    }
                                })
                            case Result.Failure ->
                                done.accept(Result.ofFailure(result.failure))
                        }
                    }
                case GIO.FlatMap ->
                    eval(io.ref) { result ->
                        switch (result) {
                            case Result.Ok ->
                                eval(io.apply(result.value), done)
                            case Result.Failure ->
                                done.accept Result.ofFailure(result.failure)
                        }
                    }
                case GIO.IOMap ->
                    eval(io.ref) { result ->
                        switch (result) {
                            case Result.Ok ->
                                eval(io.apply(result.value), done)
                            case Result.Failure ->
                                done.accept Result.ofFailure(result.failure)
                        }
                    }
                case GIO.AndThan ->
                    eval(io.ref) { result ->
                        switch (result) {
                            case Result.Ok ->
                                eval(io.otherIO, done)
                            case Result.Failure ->
                                done.accept Result.ofFailure(result.failure)
                        }
                    }
                case GIO.OrElse ->
                    eval(io.ref) { result ->
                        switch (result) {
                            case Result.Ok ->
                                done.accept(result)
                            case Result.Failure ->
                                switch (result.failure) {
                                    case GIOEmptyException ->
                                        eval(io.otherIO, done)
                                    default ->
                                        done.accept Result.ofFailure(result.failure)
                                }
                        }
                    }
                case GIO.Failure ->
                    done.accept(Result.ofFailure(io.failure))
                case GIO.FailWith ->
                    done.accept(Result.ofFailure(io.apply()))
                case GIO.FailIf ->
                    eval(io.ref) { result ->
                        switch (result) {
                            case Result.Ok ->
                                io.apply(result.value) ?
                                    done.accept(Result.ofFailure(io.throwable)) : done.accept(result)
                            case Result.Failure ->
                                done.accept(result)
                        }
                    }
                case GIO.Recover ->
                    eval(io.ref) { result ->
                        switch (result) {
                            case Result.Ok ->
                                done.accept(result)
                            case Result.Failure ->
                                eval(io.apply(result.failure), done)
                        }
                    }
                case GIO.EffectAsync ->
                    io.apply(done)
                case GIO.Fork ->
                    done.accept(Result.ofOk(new FiberRuntime(io).start()))
                case GIO.Join ->
                    io.fiber.onDone(done)
                default ->
                    done.accept Result.ofFailure(new GIOException("IO type unhandled"))
            }
        }
    }



    static <T> T debug(T value, String msg) {
        println msg
        value
    }
}



