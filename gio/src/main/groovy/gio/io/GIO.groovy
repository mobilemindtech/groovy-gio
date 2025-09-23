package gio.io

import gio.core.Result
import gio.core.Unit

import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

class GIO {

    static <A> IO<A> IO(Closure<A> f) { attempt(f) }

    static <A> IO<A> attempt(Closure<A> f) { new Attempt(f) }

    static <A> IO<A> pure(A a) { new Pure(a) }

    static IO<Unit> puts(String msg) {
        new Effect({
            println(msg)
            Unit.unit
        })
    }

    static IO<Unit> puts(Closure<String> f) {
        new Effect({
            println(f())
            Unit.unit
        })
    }

    static IO<Unit> effect(Closure f) {
        new Effect({
            f()
            Unit.unit
        })
    }

    static <A> IOBracket<A> bracket(Closure<A> acquire, Closure release){
        return new IOBracket(acquire, release)
    }

    static <A> IO<A> failure(Throwable throwable) { new Failure(throwable) }

    static <A> IO<A> effectAsync(Consumer<Consumer<Result<A>>> asyncTask) {
        new EffectAsync<A>(asyncTask)
    }

    static <A> IO<A> fromResult(Result<A> t) {
        switch (t) {
            case Result.Ok -> new Attempt(t.&get)
            case Result.Failure -> new Failure(t.failure)
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

    static final class Pure<A> extends IO<A> {
        private A pure
        Pure(A pure){
            this.pure = pure
        }

        @Override
        A getValue() { pure }
    }

    static final class Attempt<A> extends IO<A>{
        private Supplier<A> f
        Attempt(Supplier<A> f){
            this.f = f
        }
        A apply() { f.get() }
    }

    static final class Touch<A> extends IO<A>{
        private Supplier f
        private IO<A> io
        Touch(IO<A> io, Supplier f){
            this.io = io
            this.f = f
        }
        Unit apply() { f.get(); new Unit() }

        IO<A> getRef() { io }
    }

    static final class Filter<A> extends IO<A>{
        private Function<A, Boolean> f
        private IO<A> io
        Filter(IO<A> io, Function<A, Boolean> f){
            this.io = io
            this.f = f
        }
        boolean apply(A value) { f.apply(value) }

        IO<A> getRef() { io }
    }

    static final class FilterWith<A> extends IO<A>{
        private Function<A, IO<Boolean>> f
        private IO<A> io
        FilterWith(IO<A> io, Function<A, IO<Boolean>> f){
            this.io = io
            this.f = f
        }
        IO<Boolean> apply(A value) { f.apply(value) }

        IO<A> getRef() { io }
    }

    static final class Effect extends IO<Unit> {
        private Closure<Unit> f
        Effect(Closure<Unit> f){
            this.f = f
        }
        Unit apply() { f.call() }
    }

    static final class AndThan<A, B> extends IO<A> {
        private IO<A> io
        private IO<B> other
        AndThan(IO<A> io, IO<B> other){
            this.io = io
            this.other = other
        }

        IO<A> getRef(){ io }

        IO<B> getOtherIO(){ other }
    }

    static final class OrElse<A, B> extends IO<A> {
        private IO<A> io
        private IO<B> other
        OrElse(IO<A> io, IO<B> other){
            this.io = io
            this.other = other
        }

        IO<A> getRef(){ io }

        IO<B> getOtherIO(){ other }
    }

    static final class FailWith<A> extends IO<A> {
        private Closure<? extends Throwable> f
        FailWith(Closure<? extends Throwable> f){
            this.f = f
        }
        Throwable apply() { f() }
    }

    static final class FailIf<A> extends IO<A> {
        private IO<A> io
        private Function<A, Boolean> f
        final Throwable throwable
        FailIf(IO<A> io, Function<A, Boolean> f, Throwable throwable){
            this.io = io
            this.f = f
            this.throwable = throwable
        }
        boolean apply(A value) { f.apply(value) }
        IO<A> getRef() { io }
    }

    static final class EffectAsync<A> extends IO<A> {
        private Consumer<Consumer<Result<A>>> f

        EffectAsync(Consumer<Consumer<Result<A>>> f) {
            this.f = f
        }

        def apply(Consumer<Result<A>> cb) { f.accept(cb) }
    }

    static final class Failure<A> extends IO<A>{
        private Throwable throwable
        Failure(Throwable throwable){
            this.throwable = throwable
        }

        Throwable getFailure() { throwable }
    }

    // Effect combinators
    static final class FlatMap<A, B> extends IO<B>{
        private Function<A, IO<B>> f
        private IO<A> io
        FlatMap(IO<A> io, Function<A, IO<B>> f){
            this.io = io
            this.f = f
        }

        IO<A> getRef() { io }

        IO<B> apply(A a) { f.apply(a) }
    }

    static final class IOMap<A, B> extends IO<B>{
        private Function<A, B> f
        private IO<A> io
        IOMap(IO<A> io, Function<A, B> f){
            this.io = io
            this.f = f
        }

        IO<A> getRef() { io }

        IO<B> apply(A a) { new Pure(f.apply(a)) }
    }

    static final class Recover<A> extends IO<A>{
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
        abstract Fiber<A> onDone(Consumer<Result<A>> done)
    }


    static final class Fork<A> extends IO<Fiber<A>> {
        private IO<A> io
        Fork(IO<A> io){
            this.io = io
        }

        IO<A> getRef(){ io }
    }

    static final class Join<A> extends IO<A> {
        Fiber<A> fiber
        Join(Fiber<A> fiber){
            this.fiber = fiber
        }
    }

    static final class IOEnsure<A> extends  IO<A> {
        IO<A> io
        Closure f
        IOEnsure(IO<A> io, Closure f){
            this.io = io
            this.f = f
        }
        void apply() {
            f()
        }
        IO<A> getRef() { io }
    }

    static final class IOBracket<A> extends IO<A> {
        private Closure<A> acquire
        private Closure release
        IOBracket(Closure<A> acquire, Closure release){
            assert acquire
            assert release
            this.acquire = acquire
            this.release = release
        }

        IO<A> use(Closure<IO<A>> f) {

            attempt { acquire() }
                .flatMap { r ->
                    f(r).ensure { release(r) }
                }

        }

    }
}


