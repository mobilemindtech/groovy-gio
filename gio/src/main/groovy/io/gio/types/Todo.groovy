package io.gio.types

class Todo {

    static class NotImplementedException extends RuntimeException {
        NotImplementedException() {
            super("not implemented")
        }

        NotImplementedException(String text) {
            super(text)
        }
    }

    static <T> T TODO(String text = "TODO") {
        throw new NotImplementedException(text)
    }
}
