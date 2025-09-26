package gio.v1

import gio.core.GIOException
import gio.io.GIO
import gio.io.IO
import gio.runtime.GIOApp

class ForM {

    private List<String> propNames =  []
    private Map<String, ? extends IO> propVals =  [:]
    private Map<String, Object> currProp = [:]

    // missing on get
    def propertyMissing(String name){
        currProp[name]
    }

    // missing on get
    def propertyMissing(String name, val){
        def propName = name.startsWith("_") ? "__discart__${propNames.size() + 1}" : name

        if(propVals[propName])
            throw new GIOException("can't change readonly property $propName")

        propVals[propName] = val as IO
        propNames << propName
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

    def pure(Object value){
        GIO.pure(value)
    }

    def attempt(Closure f){
        f.delegate = this
        f.resolveStrategy = Closure.DELEGATE_FIRST
        GIO.attempt(f)
    }

    def effect(Closure f){
        f.delegate = this
        f.resolveStrategy = Closure.DELEGATE_FIRST
        registerProp(GIO.effect(f))
    }

    def puts(String msg){
        registerProp(GIO.puts(msg))
    }

    def puts(Closure<String> f){
        f.delegate = this
        f.resolveStrategy = Closure.DELEGATE_FIRST
        registerProp(GIO.puts(f))
    }

    def guard(Closure<Boolean> f) {
        f.delegate = this
        f.resolveStrategy = Closure.DELEGATE_FIRST
        def lastIO = removeLastProperty()
        registerProp(new GIO.IOFilter(lastIO, f))
    }

    private IO removeLastProperty(){
        // remove last
        def last = propVals.lastEntry().value
        propVals = propVals.subMap(propVals.keySet().toList().subList(0, propVals.size()-1))
        propNames = propNames.subList(0, propNames.size()-1)
        last
    }

    private void registerProp(IO io){
        def propName = generateUniqueName()
        propNames << propName
        propVals[propName] = io
    }

    private String generateUniqueName(){
        "__var__${propNames.size() + 1}" as String
    }

    private IO processBind(List<String> names, Closure yieldAction){

        final curr = names.head()
        final rest = names.tail()
        final currMonad = propVals[curr]

        currMonad.flatMap { value ->
            currProp[curr] = value
            if (rest) processBind(rest, yieldAction)
            else currMonad.map(yieldAction)
        }

    }

    IO yield(Closure f){
        f.delegate = this
        f.resolveStrategy = Closure.DELEGATE_FIRST
        processBind(propNames, f)
        //new App(resultIO).safeRun()
    }


    @Deprecated
    static <T> IO<T> forM(Class<T> ignore, @DelegatesTo(ForM) Closure f){
        f.delegate = new ForM()
        f.resolveStrategy = Closure.DELEGATE_FIRST
        f()
    }

    @Deprecated
    static <T> IO<T> forM(@DelegatesTo(ForM) Closure f){
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
