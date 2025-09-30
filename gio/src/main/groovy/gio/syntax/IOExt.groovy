package gio.syntax

import gio.core.Option
import gio.core.Result
import gio.io.GIO
import gio.io.IO
import gio.runtime.Runtime
import groovy.time.BaseDuration
import groovy.time.TimeCategory
import groovy.util.logging.Slf4j

import java.util.concurrent.Future

@Slf4j
class IOExt {

    static <A> A unsafeRun(IO<A> self) {
        new Runtime()
            .unsafeRun(self)
            .rethrow
            .get()
    }

    static <A> Future<A> unsafeRunAsync(IO<A> self) {
        new Runtime().unsafeRunAsync(self)
    }

    static <A> Result<A> safeRun(IO<A> self) {
        new Runtime().unsafeRun(self)
    }

    static <T> IO<T> getLift(T self) { GIO.pure(self) }

    static <T> IO<T> getAttempt(Closure<T> self) { GIO.attempt(self) }

    static <T> Result<T> getAsResult(Closure<T> self) { Result.of(self) }

    static <T> Result<T> getAsOk(T self) { Result.ofOk(self) }

    static <T> Result<T> getAsFailure(String self) { Result.ofFailure(new Exception(self)) }

    static <T> Result<T> getAsFailure(Throwable self) { Result.ofFailure(self) }

    static <T> Option<T> getAsSome(T self) { Option.ofSome(self) }

    // utils

    static BaseDuration getMilliseconds(Integer self) {
        TimeCategory.getMilliseconds(self)
    }

    static BaseDuration getMillisecond(Integer self) {
        TimeCategory.getMillisecond(self)
    }

    static BaseDuration getSeconds(Integer self) {
        TimeCategory.getSeconds(self)
    }

    static BaseDuration getSecond(Integer self) {
        TimeCategory.getSecond(self)
    }

    static BaseDuration getMinutes(Integer self) {
        TimeCategory.getMinutes(self)
    }

    static BaseDuration getMinute(Integer self) {
        TimeCategory.getMinute(self)
    }

    static BaseDuration getHours(Integer self) {
        TimeCategory.getHours(self)
    }

    static BaseDuration getHour(Integer self) {
        TimeCategory.getHour(self)
    }

    static BaseDuration getDays(Integer self) {
        TimeCategory.getDays(self)
    }

    static BaseDuration getDay(Integer self) {
        TimeCategory.getDay(self)
    }

    static BaseDuration getMonths(Integer self) {
        TimeCategory.getMonths(self)
    }

    static BaseDuration getMonth(Integer self) {
        TimeCategory.getMonth(self)
    }

    static BaseDuration getYears(Integer self) {
        TimeCategory.getYears(self)
    }

    static BaseDuration getYear(Integer self) {
        TimeCategory.getYear(self)
    }

}