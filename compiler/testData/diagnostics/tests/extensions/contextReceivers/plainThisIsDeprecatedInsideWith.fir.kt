// !DIAGNOSTICS: -UNUSED_PARAMETER

fun println(a: Any?): Unit = TODO()

fun f() {
    with("abc") {
        fun Any.inner() = this
        this.length
        with(123) {
            println(this)
            this
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