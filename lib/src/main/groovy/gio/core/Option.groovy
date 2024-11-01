package gio.core


import java.util.function.Consumer
import java.util.function.Function

abstract class Option<T> implements Monad<T> {

    abstract T get();

    T getValue() { get() }

    abstract boolean isEmpty()

    abstract boolean isNonEmpty()

    T getOrNull() {
        if(nonEmpty) value
        else null
    }

    Option<T> or(Option<T> v){
        if(nonEmpty ) this
        else v
    }

    Option<T> orElse(T v){
        if(nonEmpty ) this
        else of(v)
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

    Option<T> resolve(Consumer<T> fs, Closure fn){
        if(nonEmpty) fs(value)
        else fn()
        this
    }

    def <A> Option<A> flatMap(Function<T, Option<A>> f){
        if(nonEmpty) f(value)
        else ofNone()
    }

    def <A> Option<A> map(Function<T, A> f){
        if(nonEmpty) this.<A>flatMap { of(it) }
        else ofNone()
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

    Option<T> filter(Closure<Boolean> f){
        if(nonEmpty && f(value)) this
        else ofNone()
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

    }

    static <A> Option<A> of(A value){
        if(value == null) ofNone()
        else ofSome(value)
    }

    static <A> Option<A> ofSome(A value){
        new Some(value)
    }

    static <A> Option<A> ofNone(){
        new None()
    }
}