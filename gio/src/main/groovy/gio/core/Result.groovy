package gio.core

import java.util.function.Consumer;


abstract sealed class Result<T> permits Ok, Failure {

    abstract T get()

    T getValue() { get() }

    T getOrThrow(){
        if(isOk()) value
        else throw failure
    }

    T or(T other){ if(isOk()) value else other }

    Result<T> orElse(Result<T> other){ if(isOk()) this else other }

    Result<T> orElseF(Closure<T> other){ if(isOk()) this else tryOf(other) }

    T getOrNull(){ if(isOk()) value else null }

    Result<T> getRethrow(){
        if(isFailed()) throw failure
        this
    }

    abstract Throwable getFailure()

    abstract boolean isOk()

    abstract boolean isFailed()

    Result<T> foreach(Consumer<T> f){
        if(isOk()) f(value)
        this
    }

    Option<T> toOption() {
        if(isOk()) Option.ofSome(get()) else Option.ofNone()
    }

    Result<T> ifOk(Consumer<T> f){
        if(isOk()) f(value)
        this
    }

    Result<T> ifFailure(Closure f){
        if(isFailed()) f(failure)
        this
    }

    Result<T> resolve(Consumer<T> okFn, Closure failureFn, Closure alwaysFn = null){
        if(isOk()) okFn(value)
        else failureFn(failure)
        alwaysFn?.call()
        this
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







