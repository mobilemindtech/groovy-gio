package gio.syntax

import gio.io.IO
import gio.test.TIO
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

class ForM {

    static <A> IO<A> forM(Class<A> ignore, @DelegatesTo(value=Scope, strategy=Closure.DELEGATE_FIRST) Closure<IO<A>> c) {
        def scope = new Scope()
        c.delegate = scope
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.call()
    }

    static <A> IO<A> forM(@DelegatesTo(value=Scope, strategy=Closure.DELEGATE_FIRST) Closure c) {
        def scope = new Scope()
        c.delegate = scope
        c.resolveStrategy = Closure.DELEGATE_FIRST
        (c.call() as IO<A>)
            .foreach { scope._successfully?.call(it) }
            .catchAll { scope._catchAll?.call(it) }
    }


    //@CompileStatic
    static class Scope {


        Closure _successfully
        Closure _catchAll

        def successfully(@ClosureParams(value = SimpleType, options = "T") Closure c){
            _successfully = c
        }

        def catchAll(@ClosureParams(value = SimpleType, options = "Throwable") Closure c){
            _catchAll = c
        }

    }
}