// !DIAGNOSTICS: -UNUSED_PARAMETER

fun println(a: Any?): Unit = TODO()

fun f() {
    with("abc") {
        fun Any.inner() = this
        <!PLAIN_THIS_IS_DEPRECATED_INSIDE_WITH!>this<!>.length
        with(123) {
            println(<!PLAIN_THIS_IS_DEPRECATED_INSIDE_WITH!>this<!>)
            <!PLAIN_THIS_IS_DEPRECATED_INSIDE_WITH!>this<!>
        }
    }
}

fun g() {
    fun <T, R> with(receiver: T, block: T.() -> R): R = TODO()
    with("abc") {
        fun Any.inner() = this
        this.length
        with(123) {
            println(this)
            this
        }
    }
}