// !USE_EXPERIMENTAL: kotlin.RequiresOptIn

@RequiresOptIn
@Retention(AnnotationRetention.BINARY)
annotation class Marker

@Marker
interface Some

abstract class User {
    abstract fun createSome(): <!EXPERIMENTAL_API_USAGE_ERROR!>Some<!>
    fun <!EXPERIMENTAL_API_USAGE_ERROR!>Some<!>?.onSome() {}
    fun withSome(some: <!EXPERIMENTAL_API_USAGE_ERROR!>Some<!>? = null) {}

    fun use() {
        val something = <!EXPERIMENTAL_API_USAGE_ERROR!>createSome<!>()
        val somethingOther: <!EXPERIMENTAL_API_USAGE_ERROR!>Some<!> = <!EXPERIMENTAL_API_USAGE_ERROR!>createSome<!>()
        null.<!EXPERIMENTAL_API_USAGE_ERROR!>onSome<!>()
        <!EXPERIMENTAL_API_USAGE_ERROR!>withSome<!>()
    }
}

data class DataClass(@property:Marker val x: Int)

fun useDataClass(d: DataClass) {
    // Should have error in both
    d.<!EXPERIMENTAL_API_USAGE_ERROR!>x<!>
    val (<!EXPERIMENTAL_API_USAGE_ERROR!>x<!>) = d
}

typealias My = <!EXPERIMENTAL_API_USAGE_ERROR!>Some<!>

fun my(my: <!EXPERIMENTAL_API_USAGE_ERROR!>My<!>) {}

fun your(my: <!EXPERIMENTAL_API_USAGE_ERROR!>Some<!>) {}

@Marker
interface ExperimentalType {
    fun foo() {}
    fun bar() {}
}

@OptIn(Marker::class)
interface NotExperimentalExtension : ExperimentalType {
    override fun foo() {}
}

fun use(arg: NotExperimentalExtension) {
    arg.foo()
    arg.<!EXPERIMENTAL_API_USAGE_ERROR!>bar<!>()
}

@Marker
interface I

@OptIn(Marker::class)
class A : I

@OptIn(Marker::class)
class B : I

fun main() {
    val x = <!EXPERIMENTAL_API_USAGE_ERROR!>listOf<!>(A(), B())
}

