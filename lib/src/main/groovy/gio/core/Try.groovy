package gio.core


import java.util.function.Consumer;


abstract class Try<T> {

    abstract T get()

    T getValue() { get() }

    abstract Throwable getFailure()

    abstract boolean isSuccess()

    void foreach(Consumer<T> f){
        if(success) f(value)
    }

    Option<T> toOption() {
        if(success) Option.ofSome(get())
        else Option.ofNone()
    }

    Option<T> toResult() { Option.<T>fromTry(this) }

    Try<T> ifOk(Consumer<T> f){
        if(success) f(value)
        this
    }

    Try<T> ifFailure(Closure f){
        if(failure) f(value)
        this
    }

    Try<T> resolve(Consumer<T> fs, Closure fn){
        if(success) fs(value)
        else fn()
        this
    }


    static class Success<A> extends Try<A> {
        private A value

        private Success(A value){
            this.value = value
        }

        @Override
        A get() { value }

        @Override
        Throwable getFailure() { throw new GIOException("Try#getFailure method not implemented") }

        @Override
        boolean isSuccess() { true }

        @Override
        String toString() {
            "Success($value)"
        }
    }

    static class Failure<A> extends Try<A> {

        private Throwable throwable

        private Failure(Throwable throwable){
            this.throwable = throwable
        }

        @Override
        A get() { throw new GIOException("Try#get method not implemented") }

        @Override
        Throwable getFailure() { throwable }

        @Override
        boolean isSuccess() { false }

        @Override
        String toString() {
            "Failure($failure.message)"
        }
    }

    static <A> Try<A> tryOf(Closure<A> f){
        try {
            new Success(f())
        }catch(Throwable e){
            new Failure(e)
        }
    }

     static <A> Try<A> ofSuccess(A value){
         new Success<A>(value)
     }

     static <A> Try<A> ofFailure(Throwable throwable){
         new Failure<A>(throwable)
     }
}
