package gio.runtime

import gio.core.GIOEmptyException
import gio.core.GIOException
import gio.core.GIOTimeoutException
import gio.core.Option
import gio.core.Result
import gio.core.GIORetryException
import gio.io.GIO
import gio.io.IO
import groovy.time.BaseDuration
import groovy.util.logging.Slf4j

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

import static gio.core.Result.tryOf




@Slf4j
class Runtime implements IRuntime {

    def <A> Result<A> unsafeRun(IO<A> io) {
        final future = new CompletableFuture<Result<A>>()
        new FiberRuntime(io)
            .onDone({future.complete(it)})
            .start()
        future.get()
    }

    def <A> Future<A> unsafeRunAsync(IO<A> io){
        final future = new CompletableFuture<A>()
        new FiberRuntime(io)
            .onDone({
                switch (it){
                    case Result.Ok -> future.complete(it.value as A)
                    case Result.Failure -> future.completeExceptionally(it.failure)
                }
            })
            .startAsync()
        future
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

        FiberRuntime startAsync() {
            executor.submit {
                eval(io) { fiberDone(it) }
            }
            this
        }

        void eval(IO io, Consumer<Result> done) {

            try {
                evalIO(io, done)
            } catch (Throwable throwable) {
                done.accept Result.ofFailure(throwable)
            }

            /*
            executor.submit {
                try{
                    evalIO(io, done)
                }catch (Throwable throwable){
                    done.accept(Result.ofFailure(throwable))
                }
            }
             */
        }

        void evalIO(IO io, Consumer<Result> done){

            switch (io) {
                case GIO.IOPure ->
                    done.accept(Result.ofOk(io.value))
                case GIO.IOEffect ->
                    done.accept(tryOf(io.&apply))
                case GIO.IOTap ->
                    eval(io.ref) { result ->
                        switch (result) {
                            case Result.Ok ->
                                eval(io.apply(result.value)) {
                                    switch (it) {
                                        case Result.Ok ->
                                            done.accept(result)
                                        case Result.Failure ->
                                            done.accept(it)
                                    }
                                }
                            case Result.Failure ->
                                done.accept(Result.ofFailure(result.failure))
                        }
                    }
                case GIO.IOAttempt ->
                    done.accept(tryOf(io.&apply))
                case GIO.IOFilter ->
                    eval(io.ref) { result ->
                        switch (result) {
                            case Result.Ok ->
                                if(io.apply(result.value)) done.accept(result)
                                else done.accept Result.ofFailure(new GIOEmptyException())
                            case Result.Failure ->
                                done.accept(Result.ofFailure(result.failure))
                        }
                    }
                case GIO.IOFilterWith ->
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
                case GIO.IOAndThen ->
                    eval(io.ref) { result ->
                        switch (result) {
                            case Result.Ok ->
                                eval(io.otherIO, done)
                            case Result.Failure ->
                                done.accept Result.ofFailure(result.failure)
                        }
                    }
                case GIO.IOOrElse ->
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
                case GIO.IOFailure ->
                    done.accept(Result.ofFailure(io.throwable))
                case GIO.FailIf ->
                    eval(io.ref) { result ->
                        switch (result) {
                            case Result.Ok ->
                                eval(io.apply(result.value)) {
                                    switch(it){
                                        case Result.Ok ->
                                            it.value ? Result.ofFailure(io.throwable) : done.accept (result)
                                        case Result.Failure -> {
                                            done.accept it
                                        }
                                    }
                                }


                            case Result.Failure ->
                                done.accept(result)
                        }
                    }
                case GIO.IOFailWith ->
                    eval(io.ref) { result ->
                        done.accept(Result.ofFailure(io.throwable))
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
                case GIO.IODebug ->
                    eval(io.ref) { result ->
                        log.debug("::> Debug::${io.label}::> $result")
                        done.accept(result)
                    }
                case GIO.IOForeach ->
                    eval(io.ref) { result ->
                        switch (result) {
                            case Result.Ok ->
                                ignoreAndContinue io.apply(result.value), done.accept(result)
                            case Result.Failure ->
                                done.accept (result)
                        }
                    }
                case GIO.IOHandlerErrorWith ->
                    eval(io.ref) { result ->
                        switch (result) {
                            case Result.Ok -> done.accept(result)
                            case Result.Failure ->
                                eval(io.apply(result.failure)) {
                                    done.accept(it)
                                }

                        }
                    }
                case GIO.IOCatchAll ->
                    eval(io.ref) { result ->
                        switch (result) {
                            case Result.Ok ->
                                done.accept(result)
                            case Result.Failure ->
                                ignoreAndContinue io.apply(result.failure), done.accept(result)
                        }
                    }
                case GIO.IOEnsure ->
                    eval(io.ref) { ioResult ->
                        eval(io.apply()) {ensureResult ->
                            switch(ensureResult){
                                case Result.Failure ->
                                    done.accept(ensureResult)
                                case Result.Ok ->
                                    done.accept(ioResult)
                            }
                        }
                    }
                case GIO.IOSleep ->
                    eval(io.ref) {result ->
                        switch (result) {
                            case Result.Ok ->
                                schedule(io.duration) { done.accept(result) }
                            case Result.Failure ->
                                done.accept(result)
                        }
                    }
                case GIO.IOTimeout -> {
                    done.accept timeout(io)
                }
                case GIO.IORetry ->
                    done.accept retry(io, io.retryCount)
                case GIO.Fork ->
                    done.accept(Result.ofOk(new FiberRuntime(io).start()))
                case GIO.Join ->
                    io.fiber.onDone(done)
                default ->
                    done.accept Result.ofFailure(new GIOException("IO type unhandled: ${io.getClass()}"))
            }
        }

        private CompletableFuture<Void> schedule(BaseDuration duration, Closure c) {
            CompletableFuture.runAsync({
                try {
                    Thread.sleep(duration.toMilliseconds())
                    c.call()
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt()
                }
            }, executor)
        }

        private <A> Result<A> timeout(GIO.IOTimeout<A> io) {
            Future future = new CompletableFuture<Result<A>>()
            CompletableFuture.runAsync({
                eval(io.ref) { future.complete(it) }
            }, executor)
            try{
                future.get(io.duration.toMilliseconds(), TimeUnit.MILLISECONDS)
            }catch (TimeoutException ex){
                Result.ofFailure(new GIOTimeoutException(ex.message))
            }
        }

        private <A> Result<A> retry(GIO.IORetry<A> io, int retryCount){

            if(retryCount <= 0)
                return Result.ofFailure(new GIORetryException("retry error"))

            Future future = new CompletableFuture<Result<A>>()
            CompletableFuture.runAsync({
                eval(io.ref) { result ->
                    switch (result){
                        case Result.Ok ->
                            future.complete(result)
                        case Result.Failure ->
                            schedule(io.retryInterval) { retry(io, retryCount-1) }
                    }
                }
            }, executor)

            try{
                future.get(io.retryTimeout.toMilliseconds(), TimeUnit.MILLISECONDS)
            }catch (TimeoutException ignored){
                this.<A>retry(io, retryCount-1)
            }
        }
    }



    static <T> T debug(T value, String msg) {
        println msg
        value
    }

    static <T> T ignoreAndContinue(Object _, T r) { r }


}



