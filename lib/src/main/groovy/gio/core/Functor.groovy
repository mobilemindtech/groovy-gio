package gio.core


import java.util.function.Function

trait Functor<A> {

    abstract  <B, M extends Functor<B>> M fmap(Function<A, B> f)

    def <B, M extends Functor<B>> M rightShift(Function<A, B> f) {
        fmap(f)
    }
}