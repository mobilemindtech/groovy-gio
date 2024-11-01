package gio.core

/**
 * A monoid is an algebraic structure with a single associative binary operation and an identity element.
 */
trait Monoid<T> {
    abstract Monoid<T> plus(final T Val)
}