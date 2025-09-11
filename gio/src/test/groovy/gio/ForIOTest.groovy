package gio

import gio.ast.ForM
import gio.test.TIO
import spock.lang.Specification
import static gio.syntax.ForM.forM

class ForIOTest extends Specification {

    def "test forIO"() {
        setup:
        println ">> setup"

        def j = 1

        @ForM
        def prog = forM(Integer) {
            def k = 1
            TIO.puts("start")
            x = TIO.pure(5)
            y = TIO.pure(5)
            z = TIO.pure(x + y)
            guard { x > 1 && y > 1 }
            yield { z * 2 + k + j }
        }
        println "done"

        when:


            def x = prog.unsafeRun()
            println "x = $x"
        then:
            x == 22
    }

}
