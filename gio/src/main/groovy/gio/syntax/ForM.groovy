package gio.syntax

import gio.io.IO
import gio.test.TIO

class ForM {

    static <A> IO<A> forM(Class<A> ignore, @DelegatesTo(value=Scope, strategy=Closure.DELEGATE_FIRST) Closure<IO<A>> c) {
        def scope = new Scope()
        c.delegate = scope
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.call()
    }

    static <A> IO<A> forM(@DelegatesTo(value=Scope, strategy=Closure.DELEGATE_FIRST) Closure<IO<A>> c) {
        def scope = new Scope()
        c.delegate = scope
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.call()
    }


    //@CompileStatic
    static class Scope {


    }
}