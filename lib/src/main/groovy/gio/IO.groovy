package gio

import gio.core.*

import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

trait IO<A> extends Monad<A>{

    def <B> IO<B> flatMap(Function<A, IO<B>> f) {
        new GIO.FlatMap<A, B>(this, f)
    }

    def <B> IO<B> map(Function<A, B> f){
        this.<B>flatMap { GIO.pure(f(it))}
    }

    def <B extends A> IO<B> recover(Function<Throwable, IO<B>> f){
        new GIO.Recover<B>(this, f)
    }

    def <B extends A> IO<B> recoverWith(Function<Throwable, B> f){
        new GIO.Recover<B>(this, {f(it)})
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

    def <B> IO<B> andThan(IO<B> other) {
        new GIO.AndThan(this, other)
    }
    def <B> IO<B> andThan(Closure<B> f) {
        new GIO.AndThan(this, GIO.attempt(f))
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
    Monad<A> pure(A value) { GIO.succeed(value)}

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

class GIO {

    static <A> IO<A> pure(A a) { new Effect({ a }) }

    static <A> IO<A> attempt(Closure<A> f) { new Effect(f) }

    static <A> IO<A> fail(Throwable throwable) { new Fail(throwable) }

    static <A> IO<A> effectAsync(Consumer<Consumer<Try<A>>> asyncTask) {
        new EffectAsync<A>(asyncTask)
    }

    static <A> IO<A> fromTry(Try<A> t) {
        switch (t) {
            case Try.Success -> new Effect(t.&get)
            case Try.Failure -> new Fail(t.failure)
        }
    }

    static <A, B> IO<Collection<B>> foreach(Collection<A> xs, Function<A, IO<B>> f) {
        xs.inject(pure(new LinkedList<B>())) {
            IO<Collection<B>> acc, A curr ->
                acc.map { it + [f(curr)] }
        }
    }

    static <A, B> IO<Collection<B>> foreachPar(Collection<A> xs, Function<A, IO<B>> f) {
        foreach(xs) {
            f.apply(it).fork()
        }.flatMap { fibers ->
                foreach(fibers) {it.join()}
        }
    }

    // Initial algebra
    static class Effect<A> implements IO<A>{
        private Supplier<A> f
        Effect(Supplier<A> f){
            this.f = f
        }
        A getValue() { f.get() }
    }

    static class Filter<A> implements IO<A>{
        private Function<A, Boolean> f
        private IO<A> io
        Filter(IO<A> io, Function<A, Boolean> f){
            this.io = io
            this.f = f
        }
        boolean apply(A value) { f.apply(value) }

        IO<A> getRef() { io }
    }

    static class Guard<A> implements IO<A>{
        private Closure<Boolean> f
        private IO<A> io
        Guard(IO<A> io, Closure<Boolean> f){
            this.io = io
            this.f = f
        }
        boolean apply() { f() }
        IO<A> getRef(){ io }
    }

    static class AndThan<A, B> implements IO<A> {
        private IO<A> io
        private IO<B> other
        AndThan(IO<A> io, IO<B> other){
            this.io = io
            this.other = other
        }

        IO<A> getRef(){ io }

        IO<B> getOtherIO(){ other }
    }

    static class OrElse<A, B> implements IO<A> {
        private IO<A> io
        private IO<B> other
        OrElse(IO<A> io, IO<B> other){
            this.io = io
            this.other = other
        }

        IO<A> getRef(){ io }

        IO<B> getOtherIO(){ other }
    }

    static class FailWith<A> implements IO<A> {
        private Closure<? extends Throwable> f
        FailWith(Closure<? extends Throwable> f){
            this.f = f
        }
        Throwable apply() { f() }
    }

    static class EffectAsync<A> implements IO<A> {
        private Consumer<Consumer<Try<A>>> f

        EffectAsync(Consumer<Consumer<Try<A>>> f) {
            this.f = f
        }

        def apply(Consumer<Try<A>> cb) { f.accept(cb) }
    }

    static class Fail<A> implements IO<A>{
        private Throwable throwable
        Fail(Throwable throwable){
            this.throwable = throwable
        }

        Throwable getFailure() { throwable }
    }

    // Effect combinators
    static class FlatMap<A, B> implements IO<B>{
        private Function<A, IO<B>> f
        private IO<A> io
        FlatMap(IO<A> io, Function<A, IO<B>> f){
            this.io = io
            this.f = f
        }

        IO<A> getRef() { io }

        IO<B> apply(A a) { f.apply(a) }
    }

    static class Recover<A> implements IO<A>{
        private Function<Throwable, IO<A>> f
        private IO<A> io
        Recover(IO<A> io, Function<Throwable, IO<A>> f){
            this.io = io
            this.f = f
        }

        IO<A> getRef() { io }

        IO<A> apply(Throwable throwable){
            f.apply(throwable)
        }
    }

    // Fiber related effects
    static trait Fiber<A> {
        IO<A> join() {
            new Join(this)
        }
        abstract Fiber<A> onDone(Consumer<Try<A>> done)
    }


    static class Fork<A> implements IO<Fiber<A>> {
        private IO<A> io
        Fork(IO<A> io){
            this.io = io
        }

        IO<A> getRef(){ io }
    }

    static class Join<A> implements IO<A> {
        Fiber<A> fiber
        Join(Fiber<A> fiber){
            this.fiber = fiber
        }

    }
}



