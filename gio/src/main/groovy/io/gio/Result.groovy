package io.gio

import io.gio.monads.Option

abstract class Result<T> {

    /**
     * Construct new OK
     * @param value
     * @return
     */


    static <A> Ok<A> ofOk(A value) { new Ok(value) }

    static <A> Ok<A> ofOkNull() { new Ok(null) }

    /**
     * Construct new failure
     * @param value
     * @return
     */
    static Failure ofFailure(Throwable value) { new Failure(value) }

    static Failure ofFailure(String value) { ofFailure(new RuntimeException(value)) }

    static <A> Result<A> tryOf(Closure<A> f) {
        try {
            ofOk(f())
        } catch (Throwable e) {
            ofFailure(e)
        }
    }


    abstract T get()

    T getValue() { get() }


    abstract Throwable getFailure()

    // use or
    @Deprecated
    T getOr(T value) {
        if (isOk()) get()
        else value
    }

    T or(T value) {
        if (isOk()) get()
        else value
    }

    T getOrNull() {
        if (isOk()) get()
        else null
    }

    // If not ok, run another try
    Result<T> orElse(Closure<T> f) {
        if (isOk()) this
        else tryOf f
    }

    abstract boolean isOk()

    abstract boolean isFail()


    abstract Result<T> ifFailure(Closure f)

    abstract Result<T> ifOk(Closure f)

    Result<T> resolve(Closure success, Closure failure) {
        if (isOk()) success(get())
        else failure(getFailure())
        this
    }

    def <A> Result<A> errorConvert() {
        ofFailure(this.failure)
    }


    Result<T> getRethrow() {
        if (isFail()) throw getFailure()
        else this
    }

    Result<T> catchAll(Closure<Result<T>> f) {
        if (fail) f(this.failure)
        else this
    }

    Result<T> rethrowWith(Closure<Throwable> f) {
        if (fail) throw f(this.failure)
        else this
    }

    T getOrThrow() {
        if (isFail()) throw getFailure()
        else value
    }

    Result<T> throwIfNull(String msg = "value can't be null") {
        if (isOk() && value == null) ofFailure(msg)
        else this
    }

    Result<T> throwIf(String msg, Closure<Boolean> c) {
        if (isOk() && c.call(value)) ofFailure(msg)
        else this
    }

    /**
     * Fail with a new exception or nothing. Executes only current state is failure
     * <code>
     *     rethrowWithMaybe { ex ->
     *          if(ex == ?)
     *              throw new MyCustomException(ex)
     *     }
     * </code>
     * @param closure
     * @return
     */
    Result<T> rethrowWithMaybe(Closure<Throwable> f) {
        if (isFail()) {
            def err = f(getFailure())
            if(err) return ofFailure(err)
        }
        this
    }

    /**
     * Fail with a new exception or nothing. Executes only current state is success
     * <code>
     *     failIf { value ->
     *          if(value == ?)
     *              throw new MyCustomException()
     *     }
     * </code>
     * @param closure
     * @return
     */
    Result<T> failIf(Closure f) {
        if (isOk()) {
            try {
                f(get())
            } catch (err) {
                return ofFailure(err)
            }
        }
        this
    }


    /**
     * Convert Ok to Some or None
     * @return
     */
    Option<T> toOption() {
        isOk() ? Option.of(get()) : Option.ofNone()
    }

    Result<T> foreach(Closure f) {
        if (isOk()) f(value)
        this
    }


    /**
     * Like flatMap, other result executes only this result is Ok
     * @param other then other result
     * @return the other result if ok, otherwise a failure
     */
    def <A> Result<A> rightShift(Result<A> other) {
        map(other)
    }


    /**
     * Map, Closure f executes only this result is Ok
     * @param f closure to eval
     * @return Result of f, otherwise a failure
     */
    def <A> Result<A> rightShift(Closure<A> f) {
        map(f)
    }

    def <A> Result<A> map(Closure<A> f) {
        if (isFail()) return ofFailure(getFailure())
        try {
            ofOk(f(value))
        }
        catch (Exception e) {
            ofFailure(e)
        }
    }

    def <A> Result<A> map(Result<A> other) {
        if (isFail()) return ofFailure(getFailure())
        other
    }

    /**
     * FlatMap Closure f should return a Result<A>
     * @param f
     * @return
     */
    def <A> Result<A> rightShiftUnsigned(Closure<Result<A>> f) {
        flatMap(f)
    }

    def <A> Result<A> rightShiftUnsigned(Result<A> other) {
        flatMap(other)
    }

    def <A> Result<A> flatMapF(Closure<A> f) {
        if (isOk()) flatMap(tryOf { f(get()) })
        else this
    }

    def <A> Result<A> flatMap(Closure<Result<A>> f) {
        if (isFail()) return ofFailure(getFailure())
        try {
            f(value)
        }
        catch (Exception e) {
            ofFailure(e)
        }
    }

    def <A> Result<A> flatMap(Result<A> other) {
        if (isFail()) return ofFailure(getFailure())
        other
    }


    static class Ok<T> extends Result<T> {
        private T value

        Ok(T value) {
            this.value = value
        }

        @Override
        boolean isFail() { false }

        @Override
        boolean isOk() { true }

        @Override
        Result<T> ifOk(Closure f) {
            f(this.value)
            this
        }

        @Override
        Result<T> ifFailure(Closure f) {
            this
        }

        @Override
        T get() { value }

        @Override
        Throwable getFailure() { throw new RuntimeException("can't get failure from Ok") }

        def <S> S getFlatten() {
            if (value instanceof Option.Some) value.get()
            else null
        }

        @Override
        String toString() {
            "Ok($value)"
        }
    }

    static class Failure<T> extends Result<T> {
        private Throwable exception

        Failure(Throwable ex) {
            this.exception = ex
        }

        @Override
        boolean isOk() { false }

        @Override
        boolean isFail() { true }

        @Override
        T get() { throw new RuntimeException("can't get get value Failure") }

        @Override
        Throwable getFailure() { this.exception }

        Throwable getError() { this.exception }


        @Override
        Result<Throwable> ifOk(Closure f) { this }

        @Override
        Result<Throwable> ifFailure(Closure f) {
            f(this.exception); this
        }

        String getStackTrace() { getStackTrace(getFailure()) }

        @Override
        String toString() { "Failure(${exception?.message})" }
    }

    static String getStackTrace(Throwable e) {
        def sw = new StringWriter();
        def pw = new PrintWriter(sw);
        e.printStackTrace(pw)
        try {
            sw.toString()
        } finally {
            pw.close()
        }
    }

    static String getExceptionMessage(Throwable t) {
        t.message ?: t.cause?.message ?: t.class.name
    }
}