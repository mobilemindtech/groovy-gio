package io.gio

abstract class Validation {

    abstract boolean isSuccess()

    abstract boolean isFailure()


    static Failure failure(String message) { new Failure(message) }

    static Success success() { new Success() }

    static ValidationException exception(List<Failure> failures) {
        new ValidationException(failures)
    }

    final static class Success extends Validation {
        @Override
        boolean isSuccess() { true }

        @Override
        boolean isFailure() { false }
    }

    final static class Failure extends Validation {

        String message

        Failure(String message) {
            this.message = message
        }

        @Override
        boolean isSuccess() { false }

        @Override
        boolean isFailure() { true }
    }

    final static class ValidationException extends Exception {
        List<Failure> failures = []

        ValidationException(String message) {
            super(message)
        }

        ValidationException(List<Failure> failures) {
            this("Validation error", failures)
        }

        ValidationException(String message, List<Failure> failures) {
            super(message)
            this.failures = failures
        }
    }

}
