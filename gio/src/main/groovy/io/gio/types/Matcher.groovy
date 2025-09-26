package io.gio.types

/**
 * Matcher rule like switch, but a expression
 */
class Matcher {

    private Map<Object, Closure> options = [:]
    private Closure _orelse
    Object target

    private Matcher(Object value) {
        this.target = value
    }

    /**
     * Execute match on target.
     *
     * <pre>
     * def result = match(value) {
     *      when('value') { "my result $it" }
     *      when(Integer) { "$it is a Integer" }
     *      when({ it == 'value' }) { "my result $it" }
     *      when([1, 2, 3]) { "$it it is on range!" }
     *      otherwise { "$it is invalid" }
     * }
     * </pre>
     * @param target A raw value or a Closure
     * @param body The match rule
     * @return The match result
     */
    static <T> T match(Object target, @DelegatesTo(Matcher) Closure body) {
        def matcher = new Matcher(target)
        body.delegate = matcher
        body()
        matcher.doMatch() as T
    }

    private def doMatch() {

        def value = target instanceof Closure ? target.call() : target

        def option = this.options.findResult { k, f ->
            def test = k instanceof Closure ? k.call(value) : k
            if (test instanceof Class && target.getClass() == test) f
            else if (test instanceof Collection && test.contains(value)) f
            else if (test == target) f
            else null
        }

        if (option) option.call(value)
        else _orelse?.call(value)
    }

    /**
     * Test a target
     * @param value A raw value or a Closure
     * @param f The match rules
     * @return The match result
     */
    def when(Object value, Closure f) {
        options[value] = f
    }

    /**
     * If not match is found
     * @param f The rule
     * @return f Then result
     */
    def otherwise(Closure f) {
        _orelse = f
    }

    /**
     * If not match is found
     * @param f The rule
     * @return f Then result
     */
    def orelse(Closure f) {
        _orelse = f
    }
}
