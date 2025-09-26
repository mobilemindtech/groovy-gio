package gio

import gio.ast.ForM
import gio.core.GIORetryException
import gio.core.GIOTimeoutException
import gio.io.GIO
import gio.syntax.IOExt
import spock.lang.Specification

import static gio.io.GIO.IO
import static gio.syntax.ForM.forM

class Transaction {
    static Transaction open() {
        println "transaction open"
        new Transaction()
    }
    void commit() { println "transaction commit" }
    void rollback() { println "transaction rollback" }
    void close() { println "transaction close" }
}



class ForIOTest extends Specification {

    def "test forIO"() {
        setup:
        def j = 1
        @ForM
        def prog = forM(Integer) {
            def k = 1
            x = GIO.pure(5)
            y = GIO.pure(5)
            z = GIO.pure(x + y)
            guard { x > 1 && y > 1 }
            yield { z * 2 + k + j }
        }
        when:
            def x = use(IOExt) { prog.unsafeRun() }
        then:
            x == 22
    }

    def "test transaction success"() {
        setup:

        def prog = GIO.transaction {
            IO { Transaction.open() }
        } { Transaction tr ->
            IO { tr.close() }
            // release
        } { Transaction tr ->
            // on success
            IO { tr.commit() }
        } { Transaction tr, Throwable err ->
            // on error
            IO { tr.rollback() }
        } use { Transaction tr ->
             IO(1)
        } map { it + 1 }

        when:
        def x = use(IOExt) { prog.unsafeRun() }
        then:
        x == 2
    }

    def "test transaction with failure"() {
        setup:

        def prog = GIO.transaction {
            IO { Transaction.open() }
        } { Transaction tr ->
            IO { tr.close() }
            // release
        } { Transaction tr ->
            // on success
            IO { tr.commit() }
        } { Transaction tr, Throwable err ->
            // on error
            IO { tr.rollback() }
        } use { Transaction tr ->
            GIO.failWith(new Exception("transaction error!"))
        } map { it + 1 }

        when:
        def result = use(IOExt) { prog.safeRun() }
        then:
        result.failed
        result.failure.message == "transaction error!"
    }

    def "test sleep"() {
        setup:
        def time = System.currentTimeMillis()
        def prog = use(IOExt){
            IO(1).sleep(2.seconds)
        }
        when:
        def result = use(IOExt) { prog.unsafeRun() }
        def delay = System.currentTimeMillis() - time
        then:
        result == 1
        delay > 2000
    }

    def "test timeout"() {
        setup:
        def prog = use(IOExt) {
            IO(1)
                .sleep(2.seconds)
                .timeout(1.second)
        }
        when:
        def result = use(IOExt) { prog.safeRun() }
        then:
        result.failed
        result.failure instanceof GIOTimeoutException
    }

    def "test retry failure"() {
        setup:
        def i = 3
        def prog = use(IOExt){
            IO { i-- }
                .tap { println(it) }
                .failIf(x -> IO(x != 0),  "retry error")
                .retry(3, 1.second, 1.second)
        }
        when:
        def result = use(IOExt) { prog.safeRun() }
        then:
        result.failed
        result.failure  instanceof GIORetryException
    }

    def "test retry success"() {
        setup:
        def i = 3
        def prog = use(IOExt){
            IO { --i }
                .tap { println(it) }
                .failIf(x -> IO(x != 0),  "retry error")
                .retry(3, 1.second, 1.second)
        }
        when:
        def result = use(IOExt) { prog.safeRun() }
        then:
        result.ok
        result.value == 0
    }



}
