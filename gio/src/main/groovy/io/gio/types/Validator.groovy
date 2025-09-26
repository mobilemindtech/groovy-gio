package io.gio.types

class Validator {

    private Closure _errors
    private Closure _success
    private Closure rule
    private Map<Closure, String> validations = [:]
    private List<String> messages = []

    protected Validator(Closure f) {
        f.delegate = this
        this.rule = f
    }

    protected boolean hasErrorsCallback() { _errors != null }

    protected List<String> getMessages() { messages }

    protected Closure getErrorsCallback() { _errors }

    def required(String message = "", Closure f) {
        validations.put({ !f.call() }, message)
    }

    def required(Object value, String message = "") {
        validations.put({ !value }, message)
    }

    def isTrue(Boolean value, String message = "") {
        validations.put({ !value }, message)
    }

    def isTrue(String message = "", Closure f) {
        validations.put({ !f.call() }, message)
    }

    def isFalse(Boolean value, String message = "") {
        validations.put({ value }, message)
    }

    def isFalse(String message = "", Closure f) {
        validations.put({ f.call() }, message)
    }

    def required(String value, String message = "") {
        validations.put({ !value || value.trim().isEmpty() }, message)
    }

    def gt(Number value, Number expected, String message = "") {
        validations.put({ !value || value <= expected }, message)
    }

    def gte(Number value, Number expected, String message = "") {
        validations.put({ !value || value <= expected }, message)
    }

    def lt(Number value, Number expected, String message = "") {
        validations.put({ !value || value >= expected }, message)
    }

    def lte(Number value, Number expected, String message = "") {
        validations.put({ !value || value > expected }, message)
    }

    def eq(Object value, Object expected, String message = "") {
        validations.put({ value != expected }, message)
    }

    def every(List l, String message = "") {
        validations.put({ !l.every { x -> x } }, message)
    }

    def any(List l, String message = "") {
        validations.put({ !l.any { x -> x } }, message)
    }

    /**
     *
     * @param f Closure return boolean
     * @param message
     * @return
     */
    def validate(String message, Closure<Boolean> f) {
        validations.put({ !f() }, message)
    }

    def errors(Closure f) {
        _errors = f
    }

    def valid(Closure f) {
        _success = f
    }

    /**
     * Execute validator
     * @return true to valid
     */
    protected def validate() {
        this.rule.call()

        this.messages = []
        this.validations.each { c, message ->
            if (c.call()) this.messages << message
        }

        def valid = messages.empty
        if (valid) this._success?.call()
        else this._errors?.call(this.messages)
    }

    boolean isValid() { this.messages.empty }

    boolean isInvalid() { !isValid() }

    static <T> T withValidation(@DelegatesTo(Validator) Closure f) {
        new Validator(f).validate() as T
    }

    static <T> T withValidation(Class<T> ignore, @DelegatesTo(Validator) Closure f) {
        new Validator(f).validate() as T
    }
}
