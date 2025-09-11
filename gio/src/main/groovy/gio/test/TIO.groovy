package gio.test

import gio.core.Unit


class TIO<A> {
    Closure<A> _thunk
    TIO(Closure<A> thunk){
        this._thunk = thunk
    }


    def <B> TIO<B> map(Closure<B> c){
        pure(c(_thunk()))
    }

    def <B> TIO<B> flatMap(Closure<TIO<B>> c){
        c(_thunk())
    }

    TIO<A> filter(Closure<Boolean> c){
        if(c(_thunk())) this
        else throw new Exception("filter not match")
    }


    A unsafeRun() {
        _thunk()
    }

    static TIO<Unit> puts(String msg) {
        new TIO({ println(msg); Unit.unit })
    }

    static <B> TIO<B> pure(B value) {
        new TIO({ value })
    }

}
