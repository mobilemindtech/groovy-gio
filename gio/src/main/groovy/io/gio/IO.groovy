package io.gio

import io.gio.monads.Monad
import io.gio.monads.Option
import groovy.transform.TupleConstructor
import groovy.util.logging.Slf4j

import static groovyx.gpars.dataflow.Dataflow.task

@Slf4j
class IO<T> implements Monad<IO<T>> {

    protected Option<T> _value = Option.none()
    protected Option<Throwable> _error = Option.none()
    private Closure computation
    protected IO prevIO
    protected boolean debug
    protected boolean async
    protected long timeout = -1

    /**
     * Pure value
     * @param value
     * @return
     */
    static <T> IO<T> pure(T value) {
        new IO(value)
    }

    /**
     * Create new empty IO
     * @return
     */
    protected static <A> IO<A> empty() { new IO() }

    /**
     * Suspended computation
     * @param f
     * @return
     */
    static <T> IO<T> attempt(Closure f) {
        new IO(f)
    }

    /**
     * Print debug info about IO
     * @param f
     * @return
     */
    static <T> IO<T> withDebug(Closure f) {
        attempt {
            f()
            Unit.unit()
        }
    }

    /**
     * Execute closure and ignore result (unit)
     * @param f
     * @return
     */
    static <T> IO<T> ignore(Closure f) {
        attempt {
            f()
            Unit.unit()
        }
    }

    /**
     * Execute closure without return (unit)
     * @param f
     * @return
     */
    static <T> IO<T> exec(Closure f) {
        attempt {
            f()
            Unit.unit()
        }
    }

    /**
     * Create new IO with error
     * @param t
     * @return
     */
    static <T> IO<T> fail(Throwable t) {
        new IO(t)
    }

    /**
     * Create new IOSuspended
     * @param f
     * @return
     */
    static <T> IO<T> suspend(Closure f) {
        new IOSuspended(suspended: f)
    }

    /**
     * Create new IO with unit
     * @return
     */
    static <S> IO<Unit<S>> unit() {
        pure(Unit.unit())
    }

    /**
     * Create new IO that can fail
     * @param f Closure that return a Throwable or null
     * @return
     */
    static IO<Unit> failWithError(Closure<Throwable> f) {
        unit().failWith(f)
    }

    static IO<Unit> failWithErrorOrUnit(Closure<Throwable> f) {
        unit().failWith(f)
    }

    /**
     * Create new IO that fail with the message if Closure return is true
     * @param f
     * @param message
     * @return
     */
    static IO<Unit> failIf(Closure<Boolean> f, String message) {
        unit().failWith {
            if (f()) new RuntimeException(message)
        }
    }

    /**
     * Create new IO that fail with the message if b true
     * @param b
     * @param message
     * @return
     */
    static IO<Unit> failIf(Boolean b, String message) {
        unit().failWith {
            if (b) new RuntimeException(message)
        }
    }


    protected IO() {}

    /**
     * Create new IO with suspended closure
     * @param f
     */
    protected IO(Closure f) {
        this.computation = f
    }

    /**
     * Create new IO with pure value
     * @param value
     */
    protected IO(T value) {
        this._value = Option.of(value)
    }

    /**
     * Create new IO with error
     * @param t
     */
    protected IO(Throwable t) {
        this._error = Option.of(t)
    }

    Closure getComputation() { computation }

    Option getValue() { _value }

    T getOrNull() { isSome() ? get() : null }

    /**
     *
     * @return trie if value and error is empty
     */
    boolean isEmpty() { _value.isNone() && _error.isNone() }

    /**
     * Get value as option
     * @return
     */
    Option<T> getOption() { _value }

    /**
     * Error is present
     * @return
     */
    boolean isError() { _error.isSome() }

    /**
     * Value is present
     * @return
     */
    boolean isSome() { _value.isSome() }

    /**
     * Get error value
     * @return
     */
    Throwable getUnwrapError() { _error.get() }

    /**
     *
     * @return Ok<Option<T>> or Failure
     */
    Result getResult() {
        if (isError()) Result.ofFailure(unwrapError)
        else Result.ofOk(_value)
    }
    /**
     * Update IO value
     * @param value
     */
    protected IO updateValue(Option<T> value) {
        this._value = value
        this
    }

    /**
     * Update IO error
     * @param t
     */
    protected IO updateError(Option<Throwable> t) {
        this._error = t
        this
    }


    /**
     * Copy value and error from another IO
     * @param other
     * @return
     */
    IO copy(IO other) {
        updateValue(other._value)
        updateError(other._error)
        this
    }

    IO copyWithStack(IO other) {
        updateValue(other._value)
        updateError(other._error)
        prevIO = other.prevIO
        this
    }

    /**
     * Create new IO with pure value
     * @param a
     * @return
     */
    @Override
    IO<T> unit(Object a) {
        new IO(a as T)
    }

    /**
     * flatMap
     * @param f
     * @return
     */
    @Override
    IO<T> bind(Closure f) {
        if (isSome()) f(get())
        else this
    }


    /**
     * Filter IO value.
     * @param f
     * @return Current IO value or empty IO
     */
    IO<T> filter(Closure<Boolean> f) {
        new IOFilter(prevIO: this, callable: f)
    }

    def <A> IO<A> par(Closure<A> f) {
        new IOPar(prevIO: this, callable: f)
    }


    /**
     * Map IO value to another value
     * @param f A -> A
     * @return
     */
    def <A> IO map(Closure<A> f) {
        new IOMap<T, A>(prevIO: this, callable: f)
    }

    /**
     * Map IO to another IO
     * @param f A -> IO<A>
     * @return
     */
    def <A> IO flatMap(Closure<IO<A>> f) {
        new IOFlatMap<T, A>(prevIO: this, callable: f)
    }

    /**
     * Execute callable that can fail. Closure context value
     * can be a value or null if IO is empty.
     * Not execute if IO is on error
     * If callable return null, the last value remains
     * @param f callable that return Throwable or null
     * @return
     */
    IO failWith(Closure<Throwable> f) {
        new IOFailWith(prevIO: this, callable: f)
    }

    /**
     * Fail with message if IO is empty
     * @param message
     * @return
     */
    IO failIfEmpty(String message) {
        new IOFailIfEmpty(prevIO: this, message: message)
    }

    /**
     * Try get value. Is is not present, return a RuntimeException
     * @return
     */
    def T get() {
        value.get()
    }

    /**
     * If IO is empty, return a new IO with callable result
     * @param f
     * @return
     */
    def IO orElse(Closure f) {
        new IOOrElse(prevIO: this, callable: f)
    }

    /**
     * If IO is empty, return other IO
     * @param io
     * @return
     */
    def IO orIO(IO other) {
        new IOOrIO(prevIO: this, otherIO: other)
    }

    /**
     * Capture any error. Closure should return a value or throw a new exception
     * @param f
     * @return
     */
    def IO catchAll(Closure f) {
        new IOCatchAll(prevIO: this, callable: f)
    }

    /**
     * Recover if error. Should return a new value to IO
     * @param f
     * @return
     */
    def <S> IO<S> recoverWith(Closure<S> f) {
        new IOCatchAll(prevIO: this, callable: f)
    }

    /**
     * Prints last content
     * @return
     */
    def IO debug(String message = null) {
        new IODebug(prevIO: this, message: message)
    }

    def IO debug(Closure f) {
        new IODebug(prevIO: this, callable: f)
    }

    protected IO<T> unsafeRun(Object delegate) {
        if (computation) {  // if no computation, is pure
            computation.delegate = delegate
            updateValue(Option.of(computation()))
        }

        this
    }

    @Override
    String toString() {
        if (isError()) "IO($_error)"
        else "IO($_value)"
    }


    @TupleConstructor
    protected final static class IOFilter<T> extends IO<T> {
        Closure<Boolean> callable

        @Override
        protected IO<T> unsafeRun(Object delegate) {

            callable.delegate = delegate

            if (prevIO.isSome() && callable(prevIO.get()))
                copy(prevIO)
            else if (prevIO.isError())
                copy(prevIO)
            else this
        }
    }

    @TupleConstructor
    protected final static class IOMap<T, S> extends IO<T> {
        Closure<S> callable

        @Override
        protected IO<T> unsafeRun(Object delegate) {

            callable.delegate = delegate

            if (prevIO.isSome()) updateValue(Option.of(callable(prevIO.get())))
            else copy(prevIO)
        }
    }

    @TupleConstructor
    protected final static class IOFlatMap<T, S> extends IO<T> {
        Closure<IO<S>> callable

        @Override
        protected IO<T> unsafeRun(Object delegate) {

            callable.delegate = delegate

            if (prevIO.isSome()) copy(callable(prevIO.get()))
            else copy(prevIO)
        }
    }

    @TupleConstructor
    protected final static class IOOrElse<T> extends IO<T> {
        Closure<T> callable

        @Override
        protected IO<T> unsafeRun(Object delegate) {

            callable.delegate = delegate

            if (prevIO.isEmpty()) updateValue(Option.of(callable()))
            else copy(prevIO)
        }
    }

    @TupleConstructor
    protected final static class IOOrIO<T> extends IO<T> {
        IO otherIO

        @Override
        protected IO<T> unsafeRun(Object delegate) {
            if (prevIO.isEmpty()) copy(otherIO)
            else copy(prevIO)
        }
    }

    @TupleConstructor
    protected final static class IOFailWith<T> extends IO<T> {
        Closure<Throwable> callable

        @Override
        protected IO<T> unsafeRun(Object delegate) {

            callable.delegate = delegate

            if (!prevIO.isError()) {
                def cr = callable(prevIO.orNull)
                def opt = Option.<Throwable> of(cr)
                if (opt.isSome()) {
                    updateValue(Option.none())
                    return updateError(opt)
                }
            }
            copy(prevIO)
        }
    }

    @TupleConstructor
    protected final static class IOFailIfEmpty<T> extends IO<T> {
        String message

        @Override
        protected IO<T> unsafeRun(Object delegate) {
            if (prevIO.isEmpty()) updateError(Option.some(new RuntimeException(message)))
            else copy(prevIO)
        }
    }

    @TupleConstructor
    protected final static class IOCatchAll<T> extends IO<T> {
        Closure callable

        @Override
        protected IO<T> unsafeRun(Object delegate) {

            callable.delegate = delegate

            if (prevIO.isError()) {
                updateError(Option.none())
                updateValue(Option.of(callable(prevIO.unwrapError)))
            } else copy(prevIO)
        }
    }

    @TupleConstructor
    protected final static class IORecover<T> extends IO<T> {
        Closure callable

        @Override
        protected IO<T> unsafeRun(Object delegate) {

            callable.delegate = delegate

            if (prevIO.isError()) {
                updateError(Option.none())
                updateValue(Option.of(callable(prevIO.unwrapError)))
            } else copy(prevIO)
        }
    }

    @TupleConstructor
    protected final static class IODebug<T> extends IO<T> {

        String message
        Closure callable

        @Override
        protected IO<T> unsafeRun(Object delegate) {
            callable?.delegate = delegate

            if (callable) callable(prevIO.result)
            else if (message) log.info("IO>> $message - ${prevIO}")
            else log.info("IO>> ${prevIO}")

            copy(prevIO)
        }
    }


    @TupleConstructor
    protected final static class IOSuspended extends IO {
        Closure suspended
    }

    protected static interface IOFuture<T> {
        IOPromise<T> toPromise()
    }

    @TupleConstructor
    protected final static class IOPar<T> extends IO<T> implements IOFuture<T> {
        Closure<T> callable

        @Override
        protected IO<T> unsafeRun(Object delegate) {
            callable.delegate = delegate
            if (prevIO.isSome()) updateValue(Option.some(callable(prevIO.get())))
            else copy(prevIO)
        }

        @Override
        IOPromise<T> toPromise() {
            new IOPromise<T>(prevIO: this)
        }
    }

    @TupleConstructor
    protected final static class IOPromise<T> extends IO<IOJoin<T>> {

        private boolean completed

        @Override
        protected IO<IOJoin<T>> unsafeRun(Object delegate) {
            if (isEmpty()) {
                run()
                updateValue(Option.some(new IOJoin(promise: this, prevIO: this)))
            } else this
        }

        void join() {
            if (!completed) {
                synchronized (this) {
                    this.wait()
                }
            }
        }

        def run() {
            task {
                println("task run")
                try {
                    def resultIO = IOApp.unsafeRunSyncForkIO(true, prevIO)

                    copy(resultIO)
                } catch (Throwable e) {
                    println Result.getStackTrace(e)
                    updateError(Option.some(e))
                }
                finally {
                    this.completed = true
                    synchronized (this) {
                        this.notify()
                    }
                    prevIO = null
                }
            }
        }
    }

    @TupleConstructor
    protected final static class IOJoin<T> {

        IO prevIO
        IOPromise promise

        IO join() {
            promise.join()
            prevIO
        }

    }
}
