package gio.io

import gio.core.Applicative
import gio.core.Functor
import gio.core.GIOException
import gio.core.Monad

import java.util.function.Function

abstract sealed class IO<A> implements Monad<A>
    permits GIO.Pure,
            GIO.Attempt,
            GIO.Filter,
            GIO.FilterWith,
            GIO.Effect,
            GIO.AndThan,
            GIO.OrElse,
            GIO.FailWith,
            GIO.FailIf,
            GIO.EffectAsync,
            GIO.Failure,
            GIO.FlatMap,
            GIO.IOMap,
            GIO.Recover,
            GIO.Fork,
            GIO.Join {

    def <B> IO<B> flatMap(Function<A, IO<B>> f) {
        new GIO.FlatMap(this, f)
    }

    def <B> IO<B> map(Function<A, B> f){
        new GIO.IOMap(this, f)
    }

    def <B extends A> IO<B> recover(Function<Throwable, IO<B>> f){
        new GIO.Recover(this, f)
    }

    def <B extends A> IO<B> recoverWith(Function<Throwable, B> f){
        new GIO.Recover(this, {f(it)})
    }

    def <B> IO<B> orElse(IO<B> other){
        new GIO.OrElse(this, other)
    }

    def <B> IO<B> orElse(Closure<B> f){
        new GIO.OrElse(this, GIO.attempt(f))
    }

    def <B> IO<B> or(B value){
        new GIO.OrElse(this, GIO.pure(value))
    }

    def failWith(Closure<? extends Throwable> f) {
        new GIO.FailWith(f)
    }

    def failIf(Function<A, Boolean> f, Throwable exception) {
        new GIO.FailIf(this, f, exception)
    }

    def failIf(Function<A, Boolean> f, String msg) {
        new GIO.FailIf(this, f, new Exception(msg))
    }

    def <B> IO<B> andThan(IO<B> other) {
        new GIO.AndThan(this, other)
    }
    def <B> IO<B> andThan(Closure<B> f) {
        new GIO.AndThan(this, GIO.attempt(f))
    }

    def <A> IO<A> filter(Function<A, Boolean> f){
        new GIO.Filter(this, f)
    }

    def <A> IO<A> filterWith(Function<A, IO<Boolean>> f){
        new GIO.FilterWith(this, f)
    }

    IO<GIO.Fiber<A>> fork(){
        new GIO.Fork<A>(this)
    }

    // a convenience operator for sequencing effects, where the result of the
    // first effect is ignored
    def <B> IO<B> rightShift(IO<B> that) {
        flatMap { that }
    }

    @Override
    Monad<A> pure(A value) { GIO.pure(value)}

    @Override
    def <B, M extends Monad<B>> M bind(Function<A, M> f) {
        flatMap { f(it) } as M
    }

    @Override
    def <B, M extends Functor<B>> M fmap(Function<A, B> f) {
        map { f(it) } as M
    }

    @Override
    def <B> Applicative<B> fapply(Applicative<Function<A, B>> ap) {
        fmap(ap.value)
    }

    @Override
    A getValue() {
        throw new GIOException("IO#getValue not implemented")
    }
}

