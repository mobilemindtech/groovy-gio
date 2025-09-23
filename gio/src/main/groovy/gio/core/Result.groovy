package gio.core

import java.util.function.Consumer;


abstract sealed class Result<T> permits Ok, Failure {

    /**
     * Get value
     * @return
     */
    abstract T get()

    abstract Throwable getFailure()

    abstract boolean isOk()

    abstract boolean isFailed()

    @Deprecated
    boolean isFail() { isFailed() }

    /**
     * Get value
     * @return
     */
    T getValue() { get() }

    /**
     * Get valie or rethrow failure
     * @return
     */
    T getOrThrow(){
        if(isOk()) value
        else throw failure
    }

    /**
     * Get value or null
     * @return
     */
    T getOrNull(){ if(isOk()) value else null }

    /**
     * Get value or other if is failure
     * @param other
     * @return
     */
    T or(T other){ if(isOk()) value else other }

    /**
     * If is failure, map to new value of closure
     * @param other
     * @return
     */
    Result<T> orElseF(Closure<T> other){ if(isOk()) this else tryOf(other) }

    /**
     * If is failure, map to new value Result
     * @param other
     * @return
     */
    Result<T> orElse(Result<T> other){ if(isOk()) this else other }

    /**
     * Call f is Ok
     * @param f
     * @return
     */
    Result<T> foreach(Consumer<T> f){
        if(isOk()) f(value)
        this
    }

    /**
     * Map value to option
     * @return
     */
    Option<T> toOption() {
        if(isOk()) Option.ofSome(get()) else Option.ofNone()
    }

    /**
     * Call f if Ok
     * @param f
     * @return
     */
    Result<T> ifOk(Consumer<T> f){
        if(isOk()) f(value)
        this
    }

    /**
     * Call f if Failure
     * @param f
     * @return
     */
    Result<T> ifFailure(Closure f){
        if(isFailed()) f(failure)
        this
    }

    /**
     * Result resolve
     * @param okFn
     * @param failureFn
     * @param alwaysFn
     * @return
     */
    Result<T> resolve(Consumer<T> okFn, Closure failureFn, Closure alwaysFn = null){
        if(isOk()) okFn(value)
        else failureFn(failure)
        alwaysFn?.call()
        this
    }

    /**
     * Convert result to new type if of Failure
     * @return
     */
    def <A> Result<A> errorConvert() {
        ofFailure(this.failure)
    }

    /**
     * Throw exception if is a Failure
     */
    Result<T> getRethrow() {
        if (isFailed()) throw getFailure()
        else this
    }

    Result<T> rethrowIf(Closure<Boolean> f, Throwable throwable) {
        if (isFailed()) return this
        if (f(get())) ofFailure(throwable)
        else this
    }

    /**
     * If result is failure, can throw f(failure)
     * @param f
     * @return
     */
    Result<T> rethrowWith(Closure<Throwable> f) {
        if (failed) throw f(this.failure)
        else this
    }

    /**
     * Fail with a new exception or nothing. Executes only current state is failure
     * <code>
     *     rethrowWithMaybe { ex ->
     *          if(ex == ?)
     *              return new MyCustomException(ex)
     *     }
     * </code>
     * @param closure
     * @return
     */
    Result<T> rethrowWithMaybe(Closure<Throwable> f) {
        if (isFailed()) {
            def err = f(getFailure())
            if(err) return ofFailure(err)
        }
        this
    }

    /**
     * Fail with a new exception or nothing. Executes only current state is Ok
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
     * Catch exception and return a new Result
     * @param f
     * @return
     */
    Result<T> catchAll(Closure<Result<T>> f) {
        if (failed) f(this.failure)
        else this
    }

    /**
     * Catch exception and return a new T
     * @param f
     * @return
     */
    Result<T> catchAllF(Closure<T> f) {
        if (failed) tryOf { f(this.failure) }
        else this
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
        if (isFailed()) return ofFailure(getFailure())
        try {
            ofOk(f(value))
        }
        catch (Exception e) {
            ofFailure(e)
        }
    }

    def <A> Result<A> map(Result<A> other) {
        if (isFailed()) return ofFailure(getFailure())
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
        if (isFailed()) return ofFailure(getFailure())
        try {
            f(value)
        }
        catch (Exception e) {
            ofFailure(e)
        }
    }

    def <A> Result<A> flatMap(Result<A> other) {
        if (isFailed()) return ofFailure(getFailure())
        other
    }


    static <A> Result<A> of(Closure<A> f){
        try {
            new Ok(f())
        }catch(Throwable e){
            new Failure(e)
        }
    }

    static <A> Result<A> tryOf(Closure<A> f){
        of(f)
    }

    static <A> Ok<A> ofOkNull() { new Ok(null) }

    static <A> Result<A> ofOk(A value){
        new Ok<A>(value)
    }

    static <A> Result<A> ofFailure(Throwable throwable){
        new Failure(throwable)
    }

    static <A> Result<A> ofFailure(String msg){
        new Failure(new Exception(msg))
    }

    static final class Ok<A> extends Result<A> {
        private A value

        private Ok(A value){
            this.value = value
        }

        @Override
        A get() { value }

        @Override
        Throwable getFailure() { throw new GIOException("Ok#getFailure: method not implemented") }

        @Override
        boolean isFailed() { false }

        @Override
        boolean isOk() { true }

        @Override
        String toString() { "Ok($value)" }
    }

    static final class Failure<A> extends Result<A> {

        private Throwable throwable

        private Failure(Throwable throwable){
            this.throwable = throwable
        }

        @Override
        A get() { throw new GIOException("Failure#get method not implemented") }

        @Override
        Throwable getFailure() { throwable }

        @Override
        boolean isOk() { false }

        @Override
        boolean isFailed() { true }

        @Override
        String toString() { "Failure($failure.message)" }
    }
}







