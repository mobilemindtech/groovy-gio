package gio.core

import java.util.function.Consumer
import java.util.function.Function


abstract sealed class Option<T> implements Monad<T> permits Some, None  {

    abstract T get();

    abstract boolean isEmpty()

    abstract boolean isNonEmpty()

    boolean isNone() { isEmpty() }

    boolean isSome() { isNonEmpty() }

    T getValue() { get() }

    T getOrNull() { if(nonEmpty) value else null }

    T or(T v){ if(nonEmpty) value else v }


    Option<T> orThen(final T value) { isSome() ? this : of(value) }

    Option<T> orElse(Option<T> v){
        if(nonEmpty ) this else v
    }

    Option<T> orElse(final Closure<Option<T>> f) { isSome() ? this : f() }

    Option<T> orElseF(Closure<T> c){
        if(nonEmpty) this else of(c())
    }

    void foreach(Consumer<T> f){
        if(nonEmpty) f(value)
    }

    Option<T> ifNonEmpty(Consumer<T> f){
        if(nonEmpty) f(value)
        this
    }

    Option<T> ifEmpty(Closure f){
        if(empty) f(value)
        this
    }

    Option<T> resolve(Consumer<T> someFn, Closure noneFn, Closure alwaysFn = null){
        if(nonEmpty) someFn(value)
        else noneFn()
        alwaysFn?.call()
        this
    }

    Option<T> filter(Closure<Boolean> f){
        if(nonEmpty && f(value)) this else ofNone()
    }

    Option<T> throwIfEmpty(String msg = "required value") {
        if (empty) throw new RuntimeException(msg)
        this
    }


    def <A> Option<A> flatMap(Function<T, Option<A>> f){
        if(nonEmpty) f(value) else ofNone()
    }

    def <A> Option<A> map(Function<T, A> f){
        this.<A>flatMap { of(f(it)) }
    }


    @Override
    Option<T> pure(T value) { of(value) }

    @Override
    def <B, M extends Monad<B>> Option<B> bind(Function<T, M> f) {
        this.<B>flatMap { f(it)  }
    }

    @Override
    def <B, M extends Functor<B>> Option<B> fmap(Function<T, B> f) {
        this.<B>map { f(it) }
    }

    @Override
    def <B> Applicative<B> fapply(Applicative<Function<T, B>> ap) {
        fmap(ap.value)
    }

    static class Some<T> extends Option<T> {

        private T _value

        private Some(T value){ this._value = value }

        @Override
        T get() { _value }

        @Override
        boolean isEmpty() { false }

        @Override
        boolean isNonEmpty() { true }

        @Override
        String toString() { "Some($value)" }

        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            Some some = (Some) o

            if (value != some.value) return false

            return true
        }

        int hashCode() {
            return (value != null ? value.hashCode() : 0)
        }
    }

    static class None<T> extends Option<T> {


        private None(){}

        @Override
        T get() {
            throw new GIOException("None#get method not implemented")
        }

        @Override
        boolean isEmpty() { true }

        @Override
        boolean isNonEmpty() { false }

        @Override
        String toString() { 'None' }

        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false
            return true
        }

        int hashCode() {
            return super.hashCode()
        }

    }

    static <A> Option<A> of(A value){
        if(value == null) ofNone()
        else ofSome(value)
    }

    static <A> Option<A> from(Closure<A> f) {
        final a = f()
        a != null ? ofSome(a) : new None()
    }


    static <A> Option<A> ofSome(A value){
        assert value != null
        new Some(value)
    }

    static <A> Option<A> ofNone(Class<A> cls = null){
        new None()
    }

    @Deprecated
    static <T> Option<T> none() { new None<T>() }

    @Deprecated
    static None none = new None()

}