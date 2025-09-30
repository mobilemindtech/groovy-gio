package gio.runtime

import gio.core.Result
import gio.io.IO

trait GIOApp<A> {
    abstract IO<A> run()

    A unsafeRun() {
        new Runtime().unsafeRun(run()).get()
    }

    Result<A> safeRun() {
        new Runtime().unsafeRun(run())
    }
}