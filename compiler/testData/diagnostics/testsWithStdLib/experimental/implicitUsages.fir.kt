// !USE_EXPERIMENTAL: kotlin.RequiresOptIn

@RequiresOptIn
@Retention(AnnotationRetention.BINARY)
annotation class Marker

@Marker
interface Some

abstract class User {
    abstract fun createSome(): Some
    fun Some?.onSome() {}
    fun withSome(some: Some? = null) {}

    fun use() {
        val something = createSome()
        val somethingOther: Some = createSome()
        null.onSome()
        withSome()
    }
}

data class DataClass(@property:Marker val x: Int)

fun useDataClass(d: DataClass) {
    // Should have error in both
    d.x
    val (x) = d
}

typealias My = Some

fun my(my: My) {}

fun your(my: Some) {}

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
    arg.bar()
}

@Marker
interface I

@OptIn(Marker::class)
class A : I

@OptIn(Marker::class)
class B : I

fun main() {
    val x = listOf(A(), B())
}