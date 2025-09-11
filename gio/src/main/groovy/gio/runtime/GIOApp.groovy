package gio.runtime

import gio.core.Result
import gio.io.IO

trait GIOApp<A> {
    abstract IO<A> run()

    A unsafeRun() {
        new Runtime().unsafeRunSync(run()).get()
    }

    Result<A> safeRun() {
        new Runtime().unsafeRunSync(run())
    }
}