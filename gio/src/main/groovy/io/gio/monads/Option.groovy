package io.gio.monads

import io.gio.Result
import groovy.util.logging.Slf4j

@Slf4j
abstract class Option<A> implements Monad<Option<A>> {


    abstract A get()

    static <T> Option<T> some(final T a) { new Some<T>(a) }

    static <T> Option<T> none() { new None<T>() }

    static None none = none()

    static <T> Option<T> ofSome(T value) { new Some(value) }

    static <T> Option<T> ofNone() { new None() }

    static <T> Option<T> of(T a) {
        a != null ? some(a) : none()
    }

    static <T> Option<T> from(Closure<T> f) {
        final a = f()
        a != null ? some(a) : none()
    }

    abstract Boolean isSome()

    abstract Boolean isNone()

    boolean getEmpty() { isNone() }

    boolean getNonEmpty() { isSome() }

    def <B> Option<B> flatMap(Closure<Option<B>> f) { bind f }

    def <B> Option<B> map(Closure<B> f) {
        isSome() ? of(f(get())) : none
    }

    A getOrNull() {
        if (isSome()) get()
        else null
    }

    Option<A> resolve(Closure someCb, Closure noneCb, Closure always = null) {
        if (isSome()) someCb(get())
        else noneCb()
        always?.call()
        this
    }

    Option<A> ifEmpty(Closure f) {
        if (isNone()) f()
        this
    }

    Option<A> ifNonEmpty(Closure f) {
        if (isSome()) f(get())
        this
    }


    def foreach = { Closure f ->
        if (isSome()) f(get())
        this
    }

    Option<A> orThen(final A value) { isSome() ? this : of(value) }

    Option<A> orElse(final Option<A> o) { isSome() ? this : o }

    Option<A> orElse(final Closure<Option<A>> f) { isSome() ? this : f() }

    Option<A> orElseF(final Closure<A> f) { isSome() ? this : from(f) }

    A or(final A o) { isSome() ? get() : o }

    Option<A> throwIfEmpty(String msg = "required value") {
        if (empty) throw new RuntimeException(msg)
        this
    }


    static final class Some<A> extends Option<A> {
        private final A value

        private Some(final A val) {
            value = val
        }

        @Override
        Boolean isSome() { return true }

        @Override
        Boolean isNone() { return false }

        A get() { value }

        @Override
        String toString() { "Some($value)" }

        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            Some some = (Some) o;

            if (value != some.value) return false

            return true
        }

        int hashCode() {
            return (value != null ? value.hashCode() : 0)
        }
    }

    static final class None<A> extends Option<A> {

        private None() {}

        A get() { throw new RuntimeException('Cannot resolve value on None') }

        @Override
        Boolean isSome() { false }

        @Override
        Boolean isNone() { true }

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

    // --- Monad interface implementation ---

    Option unit(Object a) { some(a) }

    Option bind(Closure f) {
        someOrNone { f(get()) }
    }

    Option filter(Closure f) {
        isSome() && f(get()) ? this : none
    }

    protected Option someOrNone(Closure someVal) {
        isNone() ? this : someVal()
    }
}
