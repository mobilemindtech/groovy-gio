package gio

import io.gio.IO
import io.gio.IOApp
import io.gio.monads.Option
import spock.lang.Specification

import static io.gio.MonadComprehension.foreach
import static io.gio.monads.State.init
import static io.gio.monads.State.state

// test-app unit: io.gio.IOAppSpec -echoOut
class IOAppSpec extends Specification {

    /*
    def 'test simple IOApp runtime'() {
        given:
        def result =
            IOApp.unsafeRunSync {
                x = take { Option.some(1) }
                y = take { Option.some(2) }
                yield {
                    x + y
                }
            }
        expect:
        result.get() == Option.some(3)
    }


    def 'test IOApp runtime with IO pure with take'() {
        given:
        def result =
            IOApp.unsafeRunSync {
                x = take { IO.pure(1) }
                y = take { IO.pure(2) }
                yield {
                    x + y
                }
            }
        expect:
        result.get() == Option.some(3)
    }


    def 'test IOApp runtime with IO pure wit transform'() {
        given:
        def result =
            IOApp.unsafeRunSync {
                x = IO.pure(1).map { it + 1 }
                y = IO.pure(2).map { it + 1 }
                yield {
                    x + y
                }
            }
        expect:
        result.get() == Option.some(5)
    }


    def 'test IOApp runtime with IO with effect'() {
        given:
        def result =
            IOApp.unsafeRunSync {
                x = IO.pure(1).map { it + 1 }
                y = IO.pure(2).map { it + 1 }
                effect { IO.withDebug { println("effect? x = $x, y = $y ") } }
                yield {
                    x + y
                }
            }
        expect:
        result.get() == Option.some(5)
    }

    def 'test IOApp runtime with IO with suspend effects'() {
        given:
        def suspend = IO.suspend {
            effect(IO.ignore { println "test!!" })
            z = IO.pure(1).debug { r -> println("debug into suspended $r") }
            yield {
                z + 3
            }
        }
        def result =
            IOApp.unsafeRunSync {
                x = IO.pure(1).map { it + 1 }
                y = IO.pure(2).map { it + 1 }
                z = suspend.debug { r -> println("debug over suspended $r") }
                effect { IO.withDebug { println("effect? x = $x, y = $y, z = $z ") } }
                yield {
                    x + y + z
                }
            }
        expect:
        result.get() == Option.some((1 + 1) + (2 + 1) + (1 + 3))
    }

    def 'test IOApp runtime with IO with suspend empty effects'() {
        given:
        def suspend = IO.suspend {
            effect(IO.ignore { println "suspended with empty effect" })
        }
        def result =
            IOApp.unsafeRunSync {
                x = IO.pure(1).map { it + 1 }
                y = IO.pure(2).map { it + 1 }
                effect(suspend)
                effect { IO.withDebug { println("effect? x = $x, y = $y ") } }
                yield {
                    x + y
                }
            }
        expect:
        result.get() == Option.some((1 + 1) + (2 + 1))
    }

    def "test monad state with validation"() {

        given:
        def validator = { test, fieldName ->
            state { s ->
                def v = test ?
                    Validation.failure("parametro $fieldName deve ser informado") : Validation.success()
                [[v] + s, v]
            }
        }

        def nota = [tomador: [name: ""], servico: [name: ""]]

        def result =
            foreach {
                v = takeFrom { init() }
                v1 = takeFrom { validator(!nota, "nota") }
                v2 = takeFrom { validator(!nota?.competencia, "nota.competencia") }
                v3 = takeFrom { validator(!nota?.tomador, "nota.tomador") }
                v4 = takeFrom { validator(!nota?.tomador?.endereco, "nota.tomador.endereco") }
                v5 = takeFrom { validator(!nota?.servico, "nota.servico") }

                yield {
                    [v1, v2, v3, v4, v5].findAll { Validation.Failure.isInstance(it) }
                }
            }
        expect:
        result([])[1].size() == 2

    }

    def "test io error recover"() {

        given:
        def result =
            IOApp.unsafeRunSync {
                x = IO.pure(1).map { it + 1 }
                y = IO.pure(1).map { it + 1 }
                effect {
                    IO.attempt {
                        throw new Exception("catch error!")
                    }.catchAll { Exception e ->
                        println(e.message)
                        Unit.unit()
                    }
                }
                yield {
                    x + y
                }
            }
        expect:
        result.get() == Option.some(4)

    }

    def "test io error"() {

        given:
        def result =
            IOApp.unsafeRunSync {
                x = IO.pure(1).map { it + 1 }
                y = IO.pure(1).map { it + 1 }
                effect {
                    IO.attempt {
                        throw new Exception("throw error")
                    }
                }
                yield {
                    x + y
                }
            }
        expect:
        result.ok == false
        result.failure == true
        result.get() instanceof Exception
        result.get().message == "throw error"

    }
*/


    def "test IO pair"() {
        given:
        def result =
            IOApp.unsafeRunSync {

                withDebug(true)

                x = IO.pure(1).map { it + 1 }
                fork = IO.pure(5).par { i ->
                    println "init pair $i"
                    Thread.sleep(1000)
                    i + 5
                }
                y = IO.pure(1).debug().map { it + 1 }
                z = take { fork.join() }
                yield {
                    x + y + z
                }
            }
        expect:
        result.get() == Option.some(14)
    }


}
