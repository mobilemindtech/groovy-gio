package gio.runtime

import gio.core.Result
import gio.io.IO

import java.util.concurrent.Future
import java.util.function.Consumer

trait IRuntime {

    abstract <A> Result<A> unsafeRun(IO<A> io)

    abstract  <A> Future<A> unsafeRunAsync(IO<A> io)

}