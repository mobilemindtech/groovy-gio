package gio.io

import gio.core.Result
import gio.core.Unit
import groovy.time.BaseDuration
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

class GIO {

    static <A> IO<A> IO(Closure<A> f) { attempt(f) }

    static <A> IO<A> IO(A value) { pure(value) }

    static <A> IO<A> attempt(Closure<A> f) { new IOAttempt(f) }

    static <A> IO<A> pure(A a) { new IOPure(a) }

    static IO<Unit> puts(String msg) {
        new IOEffect({
            println(msg)
            Unit.unit
        })
    }

    static IO<Unit> puts(Closure<String> f) {
        new IOEffect({
            println(f())
            Unit.unit
        })
    }

    static IO<Unit> effect(Closure f) {
        new IOEffect({
            f()
            Unit.unit
        })
    }

    static <A> IOBracket<A> bracket(Supplier<IO<A>> acquire, Function<A, IO<Void>> release){
        new IOBracket(acquire, release)
    }

    static <A> IOTransaction<A> transaction(
        Supplier<IO<A>> acquire,
        Function<A, IO<Void>> release,
        Function<A, IO<Void>> onSuccess,
        BiFunction<A, Throwable, IO<Void>> onError){
        new IOTransaction(acquire, release, onSuccess, onError)
    }

    static <A> IO<A> failWith(Throwable throwable) { new IOFailure(throwable ) }

    static <A> IO<A> effectAsync(Consumer<Consumer<Result<A>>> asyncTask) {
        new EffectAsync<A>(asyncTask)
    }

    static <A> IO<A> fromResult(Result<A> t) {
        switch (t) {
            case Result.Ok -> new IOAttempt(t.&get)
            case Result.Failure -> new IOFailure(t.failure)
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

    static final class IOPure<A> extends IO<A> {
        private A pure

        IOPure(A pure){
            this.pure = pure
        }

        @Override
        A getValue() { pure }
    }

    static final class IOAttempt<A> extends IO<A>{
        private Supplier<A> f

        IOAttempt(Supplier<A> f){
            this.f = f
        }
        A apply() { f.get() }
    }

    static final class IOTap<A> extends IO<A>{
        private Function<A, IO<Void>> f
        private IO<A> io
        IOTap(IO<A> io, Function<A, IO<Void>> f){
            this.io = io
            this.f = f
        }
        IO<Void> apply(A value) { f.apply(value) }

        IO<A> getRef() { io }
    }

    static final class IOFailReplace<A> extends IO<A>{
        private Throwable throwable
        IOFailReplace(Throwable throwable) {
            this.throwable = throwable
        }
    }

    static final class IORetry<A> extends IO<A>{
        final int retryCount
        final BaseDuration retryInterval
        final BaseDuration retryTimeout
        private IO<A> io
        IORetry(IO<A> io,
                int retryCount,
                BaseDuration retryInterval,
                BaseDuration retryTimeout){
            this.io = io
            this.retryCount = retryCount
            this.retryInterval = retryInterval
            this.retryTimeout = retryTimeout
        }

        IO<Boolean> apply(A value) { condition.apply(value) }

        IO<A> getRef() { io }
    }

    static final class IOTimeout<A> extends IO<A>{
        final BaseDuration duration
        private IO<A> io
        IOTimeout(IO<A> io, BaseDuration duration){
            this.io = io
            this.duration = duration
        }

        IO<A> getRef() { io }
    }

    static final class IOSleep<A> extends IO<A>{
        final BaseDuration duration
        private IO<A> io
        IOSleep(IO<A> io, BaseDuration duration){
            this.io = io
            this.duration = duration
        }

        IO<A> getRef() { io }
    }

    static final class IOFilter<A> extends IO<A>{
        private Function<A, Boolean> f
        private IO<A> io

        IOFilter(IO<A> io, Function<A, Boolean> f){
            this.io = io
            this.f = f
        }
        boolean apply(A value) { f.apply(value) }

        IO<A> getRef() { io }
    }

    static final class IOFilterWith<A> extends IO<A>{
        private Function<A, IO<Boolean>> f
        private IO<A> io

        IOFilterWith(IO<A> io, Function<A, IO<Boolean>> f){
            this.io = io
            this.f = f
        }
        IO<Boolean> apply(A value) { f.apply(value) }

        IO<A> getRef() { io }
    }

    static final class IOEffect extends IO<Unit> {
        private Closure<Unit> f

        IOEffect(Closure<Unit> f){
            this.f = f
        }
        Unit apply() { f.call() }
    }

    static final class IOAndThen<A, B> extends IO<A> {
        private IO<A> io
        private IO<B> other

        IOAndThen(IO<A> io, IO<B> other){
            this.io = io
            this.other = other
        }

        IO<A> getRef(){ io }

        IO<B> getOtherIO(){ other }
    }

    static final class IOOrElse<A, B> extends IO<A> {
        private IO<A> io
        private IO<B> other

        IOOrElse(IO<A> io, IO<B> other){
            this.io = io
            this.other = other
        }

        IO<A> getRef(){ io }

        IO<B> getOtherIO(){ other }
    }

    static final class IOFailWith<A> extends IO<A> {
        private IO<A> io
        final Throwable throwable
        IOFailWith(IO<A> io, Throwable throwable){
            this.throwable = throwable
            this.io = io
        }
        IO<A> getRef(){ io }
    }

    static final class IOFailure<A> extends IO<A> {
        final Throwable throwable
        IOFailure(Throwable throwable){
            this.throwable = throwable
        }

    }

    static final class FailIf<A> extends IO<A> {
        private IO<A> io
        private Function<A, IO<Boolean>> f
        final Throwable throwable
        FailIf(IO<A> io, Function<A, IO<Boolean>> f, Throwable throwable){
            this.io = io
            this.f = f
            this.throwable = throwable
        }
        IO<Boolean> apply(A value) { f.apply(value) }
        IO<A> getRef() { io }
    }

    static final class EffectAsync<A> extends IO<A> {
        private Consumer<Consumer<Result<A>>> f

        EffectAsync(Consumer<Consumer<Result<A>>> f) {
            this.f = f
        }

        def apply(Consumer<Result<A>> cb) { f.accept(cb) }
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

        IO<B> apply(A a) { new IOPure(f.apply(a)) }
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

    static final class IOForeach<A> extends IO<A> {
        private IO<A> io
        private Closure f
        IOForeach(IO<A> io, Closure f){
            this.io = io
            this.f = f
        }

        void apply(A value) {  f(value) }

        IO<A> getRef(){ io }
    }

    static final class IODebug<A> extends IO<A> {
        private IO<A> io
        final String label
        IODebug(IO<A> io, String label){
            this.io = io
            this.label = label
        }

        IO<A> getRef(){ io }
    }

    static final class IOCatchAll<A> extends IO<A> {
        private IO<A> io
        private Closure f
        IOCatchAll(IO<A> io, Closure f){
            this.io = io
            this.f = f
        }

        void apply(Throwable err) {  f(err) }

        IO<A> getRef(){ io }
    }

    static final class IOEnsure<A> extends  IO<A> {
        IO<A> io
        Closure<IO<Void>> f
        IOEnsure(IO<A> io, Closure<IO<Void>> f){
            this.io = io
            this.f = f
        }
        IO<Void> apply() { f() }
        IO<A> getRef() { io }
    }


    static final class IOHandlerErrorWith<A> extends  IO<A> {
        IO<A> io
        Closure<IO<A>> f

        IOHandlerErrorWith(IO<A> io, Closure<IO<A>> f){
            this.io = io
            this.f = f
        }

        IO<A> apply(Throwable error) { f(error) }

        IO<A> getRef() { io }
    }

    static final class IOTransaction<A> {
        private Supplier<IO<A>> acquire
        private Function<A, IO<Void>> release
        BiFunction<A, Throwable, IO<Void>> _onError
        private Function<A, IO<Void>> _onSuccess

        IOTransaction(Supplier<IO<A>> acquire,
                      Function<A, IO<Void>> release,
                      Function<A, IO<Void>> onSuccess,
                      BiFunction<A, Throwable, IO<Void>> onError) {
            this.acquire = acquire
            this.release = release
            this._onSuccess = onSuccess
            this._onError = onError
        }

        def <B> IO<B> use(Function<A, IO<B>> f) {
            bracket(acquire, release) use { tx ->
                f.apply(tx) flatMap { that ->
                    this._onSuccess.apply(tx) map {
                        that
                    }
                } handlerErrorWith { Throwable err ->
                    this._onError.apply(tx, err) failWith err
                }
            }
        }
    }

    static final class IOBracket<A>{
        private Supplier<IO<A>> acquire
        private Function<A, IO<Void>> release
        IOBracket(Supplier<IO<A>> acquire, Function<A, IO<Void>> release){
            assert acquire
            assert release
            this.acquire = acquire
            this.release = release
        }

        def <B> IO<B> use(Function<A, IO<B>> f) {
            acquire.get() flatMap { r ->
                f.apply(r) ensure { release.apply(r) }
            }
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
}


