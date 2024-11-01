package gio.core


import java.util.function.Function

trait Monad<A> extends Applicative<A>{

    abstract Monad<A> pure(A value)

    abstract  <B,M extends Monad<B>> M bind(Function<A,M> f)

    def <B, M extends Monad<B>> M rightShiftUnsigned(Function<A, M> f) {
        bind(f)
    }
}
