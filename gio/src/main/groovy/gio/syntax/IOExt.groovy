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
        use(TimeCategory) { self.milliseconds }
    }

    static BaseDuration getMillisecond(Integer self) {
        use(TimeCategory) { self.millisecond }
    }

    static BaseDuration getSeconds(Integer self) {
        use(TimeCategory) { self.seconds }
    }

    static BaseDuration getSecond(Integer self) {
        use(TimeCategory) { self.second }
    }

    static BaseDuration getMinutes(Integer self) {
        use(TimeCategory) { self.minutes }
    }

    static BaseDuration getMinute(Integer self) {
        use(TimeCategory) { self.minute }
    }

    static BaseDuration getHours(Integer self) {
        use(TimeCategory) { self.hours }
    }

    static BaseDuration getHour(Integer self) {
        use(TimeCategory) { self.hour }
    }

    static BaseDuration getDays(Integer self) {
        use(TimeCategory) { self.days }
    }

    static BaseDuration getDay(Integer self) {
        use(TimeCategory) { self.day }
    }

    static BaseDuration getMonths(Integer self) {
        use(TimeCategory) { self.months }
    }

    static BaseDuration getMonth(Integer self) {
        use(TimeCategory) { self.month }
    }

    static BaseDuration getYears(Integer self) {
        use(TimeCategory) { self.years }
    }

    static BaseDuration getYear(Integer self) {
        use(TimeCategory) { self.year }
    }

}