package gio.core

import java.util.concurrent.TimeoutException

class GIOTimeoutException extends TimeoutException {
    GIOTimeoutException(String msg){
        super(msg)
    }
}
