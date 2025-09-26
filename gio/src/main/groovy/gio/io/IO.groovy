package gio.io

import gio.core.Applicative
import gio.core.Functor
import gio.core.GIOException
import gio.core.Monad
import groovy.time.BaseDuration
import groovy.time.TimeCategory
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

import java.util.function.Function

abstract sealed class IO<A> implements Monad<A>
    permits GIO.IOPure,
    GIO.IOAttempt,
    GIO.IOFilter,
    GIO.IOFilterWith,
    GIO.IOEffect,
            GIO.IOTap,
    GIO.IOAndThen,
    GIO.IOOrElse,
            GIO.FailIf,
            GIO.EffectAsync,
            GIO.IOFailWith,
            GIO.IOFailure,
            GIO.FlatMap,
            GIO.IOMap,
            GIO.Recover,
            GIO.Fork,
            GIO.Join,
            GIO.IOEnsure,
            GIO.IOCatchAll,
            GIO.IOForeach,
            GIO.IODebug,
            GIO.IOHandlerErrorWith,
            GIO.IOSleep,
            GIO.IORetry,
            GIO.IOTimeout,
            GIO.IOFailReplace {

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
        new GIO.Recover(this, {  GIO.attempt { f(it) } })
    }

    def <B> IO<B> orElse(IO<B> other){
        new GIO.IOOrElse(this, other)
    }

    def <B> IO<B> orElse(Closure<B> f){
        new GIO.IOOrElse(this, GIO.attempt(f))
    }

    def <B> IO<B> or(B value){
        new GIO.IOOrElse(this, GIO.pure(value))
    }

    /**
     * Fail with error
     * @param f
     * @return
     */
    IO<A> failWith(Throwable throwable) {
        new GIO.IOFailWith(this, throwable)
    }

    IO<A> failIf(Function<A, IO<Boolean>> f, Throwable exception) {
        new GIO.FailIf(this, f, exception)
    }

    IO<A> failIf(Function<A, Boolean> f, String msg) {
        new GIO.FailIf(this, f, new Exception(msg))
    }

    IO<A> tap(Function<A, IO<Void>> f) {
        new GIO.IOTap(this, f)
    }

    IO<A> tap(@ClosureParams(value = SimpleType, options = "A") Closure<Void> f) {
        new GIO.IOTap(this, { value -> GIO.attempt { f(value) } })
    }


    def <B> IO<B> andThen(IO<B> other) {
        new GIO.IOAndThen(this, other)
    }

    def <B> IO<B> andThen(Closure<B> f) {
        new GIO.IOAndThen(this, GIO.attempt(f))
    }

    /**
     * Apply filter
     * @param f
     * @return
     */
    def <A> IO<A> filter(Function<A, Boolean> f){
        new GIO.IOFilter(this, f)
    }

    /**
     * Filter with IO
     * @param f
     * @return
     */
    def <A> IO<A> filterWith(Function<A, IO<Boolean>> f){
        new GIO.IOFilterWith(this, f)
    }

    /**
     * Ensure operation on success or fail
     * @param f
     * @return
     */
    def IO<A> ensure(Closure<IO<Void>> f) {
        new GIO.IOEnsure(this, f)
    }


    /**
     * Catch all errors, but don't do anything
     * @param f
     * @return
     */
    def IO<A> catchAll(Closure f) {
        new GIO.IOCatchAll(this, f)
    }

    /**
     *
     * @param f
     * @return
     */
    def IO<A> foreach(Closure f) {
        new GIO.IOForeach(this, f)
    }

    /**
     * Log IO operation result
     * @param label
     * @return
     */
    def IO<A> debug(String label = "") {
        new GIO.IODebug(this, label)
    }

    def IO<A> retry(int retryCount, BaseDuration retryInterval, BaseDuration retryTimeout) {
        new GIO.IORetry(this, retryCount, retryInterval, retryTimeout)
    }

    def IO<A> retry(int retryCount, BaseDuration retryInterval) {
        use(TimeCategory) { new GIO.IORetry(this, retryCount, retryInterval, 0.second) }
    }

    def IO<A> retry(int retryCount) {
        use(TimeCategory) { new GIO.IORetry(this, retryCount, 0.second, 0.second) }
    }

    def IO<A> sleep(BaseDuration duration) {
        new GIO.IOSleep(this, duration)
    }

    def IO<A> timeout(BaseDuration duration) {
        new GIO.IOTimeout(this, duration)
    }

    /**
     * Handle error with new IO<A>
     * @param f
     * @return
     */
    def IO<A> handlerErrorWith(Closure<IO<A>> f) {
        new GIO.IOHandlerErrorWith(this, f)
    }

    def <B> IO<B> cast(Class<B> cls = null){
        this as IO<B>
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

