/*
 * This source file was generated by the Gradle 'init' task
 */
package gio

import spock.lang.Specification

import static gio.ForM.forM

class Console {
    IO puts(String str)  {
        GIO.attempt { println(str) }
    }
}

class SequenceEffects implements GIOApp {
    IO run() {
        GIO.pure (1).flatMap {
            value -> GIO.pure(value + 1)
        }
    }
}

class GIOTest extends Specification {
    def "gio exec return 1"() {
        setup:
        def app = new SequenceEffects()

        when:
        def result = app.tryExec()

        then:
        result.isSuccess() && result.get() == 2
    }

    def "gio forM"() {
        setup:


        when:
        def result = forM {
            x = pure { 1 }
            y = pure { 1 }
            guard {x > 0}
            yield {x + y}
        }

        then:
        result.isSuccess() && result.get() == 2
    }

    def "gio forM IO"() {
        setup:


        when:
        def result = forM {
            x = GIO.pure(1) >> { it + 2 }
            yield { x }
        }

        then:
        result.isSuccess() && result.get() == 3
    }
}
