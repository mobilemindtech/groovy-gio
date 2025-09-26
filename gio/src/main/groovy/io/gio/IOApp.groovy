package io.gio

import io.gio.types.Matcher
import io.gio.mcategories.FunctorCategory
import io.gio.mcategories.MCollectionCategory
import io.gio.mcategories.MListCategory
import io.gio.mcategories.MMapCategory
import io.gio.mcategories.MReaderCategory
import io.gio.monads.Monad
import io.gio.monads.Option
import groovy.util.logging.Slf4j

/**
 * <code>
 *
 *     def suspend = IOEffect.suspend {
 *         effect(IO.attempt { println "test!!" })
 *     }
 *
 *     IOApp.unsafeRunSync {
 *
 *         x = take { some(1) }
 *         y = take { some(2) }
 *
 *         effect { IO.attempt { println "x = $x" } }
 *
 *         effect(IO.attempt { println "test!!" })
 *
 *         z = IO.pure(1).map { i -> i + 2 }
 *
 *         yield { x + y + z}
 *
 *         withOption { Option<Integer> it ->
 *
 *         }
 *
 *         withSome { Integer it ->
 *
 *         }
 *
 *         withNone {
 *
 *         }
 *
 *         withResult { Result<Option<Integer>> it ->
 *
 *         }
 *
 *         withFailure { Throwable t ->
 *
 *         }
 *
 *         withValue { value ->
 *
 *         }
 *
 *     }
 * </code>
 * @param f
 * @return
 */
@Slf4j
class IOApp {


    // choosing the categories to be used on given monads

    private Class category(Collection c) { MCollectionCategory }

    private Class category(Range r) { MListCategory }

    private Class category(Map c) { MMapCategory }

    private Class category(Closure m) { MReaderCategory }

    private Class category(Monad m) { FunctorCategory }

    private Class category(Object o) {
        throw new IllegalArgumentException("Unsupported monad category: ${o.getClass().name}")
    }

    // ----- container storage -----

    private List<String> propNames = []
    private Map<String, Object> propVals = [:]
    private Map<String, Object> currProp = [:]
    private List<Closure> guards = []
    private freeFunctions = []
    private int propsCount = 0

    private Closure withResultFn
    private Closure withFailureFn
    private Closure withOkFn
    private Closure withValueFn
    private Closure yieldFn
    private Closure matchFn
    Boolean _debug
    private Boolean _fork

    // ----- comprehension interface -----

    def withDebug(Boolean b) { _debug = b }

    def withFork(Boolean b) { _fork = b }

    def effect(Closure<? extends Monad> f) {
        def pName = "_p_${propsCount++}".toString()
        propNames << pName
        propVals[pName] = f
    }

    def effect(IO io) {
        def pName = "__prop_${propsCount++}".toString()
        propNames << pName
        propVals[pName] = io
    }

    def take(Closure<? extends Monad> f) { f }

    def guard(Closure<Boolean> f) { guards << f }

    def yield(Closure f) { yieldFn = f }

    def withResult(Closure f) { withResultFn = f }

    def withResultFailure(Closure f) { withFailureFn = f }

    def withResultOk(Closure f) { withOkFn = f }

    def withValue(Closure f) { withValueFn = f }

    def match(@DelegatesTo(Matcher) Closure f) { matchFn = f }

    // ----- comprehension implementation -----

    private inContext(String name, value, Closure action) {
        currProp[name] = value
        ctx(this, action)
    }

    private Closure ctx(delegate, Closure action) {
        action.delegate = delegate
        //action.resolveStrategy = Closure.DELEGATE_FIRST
        action
    }


    private IO runStackIO(IO currIO) {

        if (!_fork) {
            if (currIO instanceof IO.IOFuture) {
                return currIO.toPromise().unsafeRun(null)
            }
        }

        if (currIO.prevIO)
            runStackIO(currIO.prevIO)

        try {
            if (_debug) log.debug "IO>> run next IO"

            if (currIO instanceof IO.IOSuspended) {
                def resultIO = IOApp.unsafeRunSuspended(_debug, currIO.suspended)
                currIO.copy(resultIO).unsafeRun(this)
            } else {
                currIO.unsafeRun(this)
            }


        } catch (NullPointerException e) {
            log.error "IO>> catching NullPointerException", e
            currIO.updateError(Option.of(e))
        } catch (Throwable e) {
            log.error "IO>> catching ${e.message}", e
            currIO.updateError(Option.of(e))
        }
    }


    private def run() {
        processOuter(propNames, yieldFn)
    }

    private def processOuter(List<String> names, Closure yieldAction) {
        def (curr, rest) = [names.head(), names.tail()]

        def currMonad = propVals[curr]

        if (_debug)
            log.debug("IO>> $curr = $currMonad")


        if (currMonad instanceof Closure)
            currMonad = currMonad()


        if (currMonad instanceof IO)
            currMonad = runStackIO(currMonad)


        applyOptionalFn(currMonad)

        use(category(currMonad)) {
            if (rest)
                currMonad.bind { elem ->
                    currProp[curr] = elem
                    processOuter(rest, yieldAction)
                }
            else {
                def container = guards ? // filter if at least one guard is given
                        currMonad.filter { elem ->
                            guards.every { inContext(curr, elem, it)() }
                        } : currMonad

                container.fmap { elem ->
                    if (yieldAction) inContext(curr, elem, yieldAction)()
                    else elem
                }
            }
        }
    }

    /**
     * Queries for potential calls to unbound functions that may (or may not)
     * belong to the current monad. If such monad function is found, it is immediately
     * applied to its arguments.
     *
     * @param monad the currently used monad
     */
    private applyOptionalFn(monad) {
        if (freeFunctions) {
            def fnInfo = freeFunctions.head()
            /*try {
             ... to catch the possible exception or not to catch: that is the question */
            monad."${fnInfo.fn}"(*fnInfo.args)
//                monad.metaClass.invokeMethod(monad, fnInfo.fn, fnInfo.args)
            freeFunctions.remove(0) // the function is used up, time for the next one
            /*}
            catch (MissingMethodException ignored) {
                // the method apparently doesn't belong to this monad, maybe next time
            }*/
        }
    }

    // ----- dynamic properties -----

    def propertyMissing(String name) {
        currProp[name]
    }

    def propertyMissing(String name, val) {
        propVals[name] = val
        propNames << name
    }

    def methodMissing(String name, args) {
        freeFunctions << [fn: name, args: args]
    }


    static def forM(@DelegatesTo(IOApp) Closure f) {
        forM(null, null, f)
    }

    static def forM(Boolean debug, Closure f) {
        forM(debug, null, f)
    }

    static def forM(Boolean debug, Boolean fork, Closure f) {
        def executor = new IOApp()

        if (debug != null)
            executor.withDebug(debug)

        if (fork != null)
            executor.withFork(fork)

        f.delegate = executor
        f()

        def result = executor.run()

        if (executor._debug)
            log.debug("IO>> result = $result")

        if (result instanceof IO) {
            def r = result.result

            executor.withResultFn?.call(r)

            if (r instanceof Result.Ok) {
                executor.withOkFn?.call(r)
            } else {
                executor.withFailureFn?.call(r)
            }

            if (executor.matchFn) Matcher.match(r, executor.matchFn)

        } else {
            executor.withValueFn?.call(result)
            executor.matchFn?.call(result)
        }


        result
    }

    protected static IO unsafeRunSuspended(Boolean debug, Closure f) {
        if (debug) log.debug("IO>> run suspension")
        def result = forM(debug, f)
        if (debug) log.debug("IO>> suspension result = $result")
        if (result instanceof IO) result
        else IO.pure(result)
    }

    protected static <T> IO unsafeRunSyncForkIO(Boolean debug, IO io) {
        if (debug) log.info("IO>> run unsafeRunSyncForkIO ${io.class.name}")
        def result = forM(debug, true) {
            fk = io
        }
        if (debug) log.info("IO>> unsafeRunSyncForkIO result = $result")
        if (result instanceof IO) result
        else IO.pure(result)
    }

    static <T> Result<T> unsafeRunSync(@DelegatesTo(IOApp) Closure f) {
        def result = forM(f)
        if (result instanceof IO) result.result as Result<T>
        else Result.ofOk(result) as Result<T>
    }
}
