package io.gio.mcategories

class FunctorCategory<M> {

    static <M> M fmap(M m, Closure f) { m.bind { a -> m.unit(f(a)) } }

}
