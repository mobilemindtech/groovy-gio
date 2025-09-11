package gio.ast

import java.lang.annotation.*
import org.codehaus.groovy.transform.GroovyASTTransformationClass

/**
 *
 * Rewrite closure to for monad.
 *
 * <blockquote><pre>
 * {@code
 * @ForM
 * def prog = forM {
 *     x = IO.pure(1)
 *     y = IO.pure(1)
 *     guard { x > 0 && y > 0 }
 *     yield { x + y }
 * } == IO(2)
 * }
 * </blockquote></pre>
 *
 * The monad should implements this methods:
 * <ul>
 *     <li><code>flatMap</code></li>
 *     <li><code>map</code></li>
 *     <li><code>filter</code> to use guard</li>
 *  </ul>
 */
@Retention(RetentionPolicy.SOURCE)
@Target([ElementType.LOCAL_VARIABLE, ElementType.FIELD, ElementType.TYPE_USE])
@GroovyASTTransformationClass(classes = ForMTransform)
@interface ForM {}