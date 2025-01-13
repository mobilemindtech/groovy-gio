package gio

import gio.core.GIOException
import gio.core.Try

class ForM {

    private List<String> propNames =  []
    private Map<String, ? extends IO> propVals =  [:]
    private Map<String, Object> currProp = [:]
    private List<Closure> guards = []

    // missing on get
    def propertyMissing(String name){
        currProp[name]
    }

    // missing on get
    def propertyMissing(String name, val){
        def pname = name == "_" ? "__discart__${propNames.size() + 1}" : name

        if(propVals[pname])
            throw new GIOException("can't change readonly property $pname")

        propVals[pname] = val as IO
        propNames << pname
    }

    def take(Closure<? extends IO> f){
        f.delegate = this
        f.resolveStrategy = Closure.DELEGATE_FIRST
        f()
    }

    def pure(Closure f){
        f.delegate = this
        f.resolveStrategy = Closure.DELEGATE_FIRST
        GIO.pure(f())
    }

    def attempt(Closure f){
        f.delegate = this
        f.resolveStrategy = Closure.DELEGATE_FIRST
        GIO.attempt(f)
    }

    def guard(Closure<Boolean> f){
        f.delegate = this
        f.resolveStrategy = Closure.DELEGATE_FIRST
        //guards << f
        def lastIO = propVals.lastEntry().value
        def pname = "__guard__${propNames.size() + 1}" as String
        propNames << pname
        propVals[pname] = new GIO.Guard(lastIO, f)
    }

    private IO processBind(List<String> names, Closure yieldAction){

        def (curr, rest) = [names.head(), names.tail()]
        def currMonad = propVals[curr]

        /*
        def lastMonad = propVals[names.tail().first]

        rest.inject(currMonad) {
            acc, pname ->
                println "processBind inject $pname"
                acc.bind { value ->
                    currProp[pname] = value
                    propVals[pname]
                }
        }.fmap(yieldAction) as IO

        currMonad.bind { value ->
            currProp[curr] = value
            lastMonad
        }*/



        currMonad.bind { value ->
            currProp[curr] = value

            if (rest) processBind(rest, yieldAction)
            else currMonad.fmap(yieldAction)
                /*if(!guards) currMonad.fmap(yieldAction)
                else
                    new GIO.Filter(currMonad, {
                        guards.every { f -> f() }
                    }).fmap(yieldAction)
            */
            /*else{
                def m = (guards ?
                    currMonad.filter {
                        guards.every { Closure f -> f() }
                    } : currMonad) as Monad

                m.fmap(yieldAction)
            }
             */
        }

    }

    def yield(Closure f){
        f.delegate = this
        f.resolveStrategy = Closure.DELEGATE_FIRST
        def resultIO = processBind(propNames, f)
        new App(resultIO).tryExec()
    }


    static <T> Try<T> forM(@DelegatesTo(ForM) Closure f){
        f.delegate = new ForM()
        f.resolveStrategy = Closure.DELEGATE_FIRST
        f()
    }


    private static class App implements GIOApp {
        private final IO io

        App(IO io){ this.io = io }

        IO run() { io }
    }


}
