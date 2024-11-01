package gio.core

import java.util.function.Function

trait Applicative<A> extends Functor<A> {

    /**
     *
     * @param ap Applicative<{A -> B}>
     * @return
     */
    abstract <B> Applicative<B> fapply(Applicative<Function<A, B>> ap)

    abstract A getValue()
}