// !USE_EXPERIMENTAL: kotlin.RequiresOptIn
// FILE: api.kt

package api

import kotlin.annotation.AnnotationTarget.*

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Target(CLASS, ANNOTATION_CLASS, PROPERTY, FIELD, LOCAL_VARIABLE, VALUE_PARAMETER, CONSTRUCTOR, FUNCTION,
        PROPERTY_SETTER, TYPEALIAS)
@Retention(AnnotationRetention.BINARY)
annotation class E1

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Target(FILE)
annotation class E2

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Target(EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
annotation class E3

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Target(TYPE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class E3A

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Target(TYPE)
@Retention(AnnotationRetention.BINARY)
annotation class E3B

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Target(PROPERTY_GETTER)
@Retention(AnnotationRetention.BINARY)
annotation class E4

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Target(PROPERTY_SETTER)
@Retention(AnnotationRetention.BINARY)
annotation class E5

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Target(PROPERTY, FUNCTION, PROPERTY_SETTER, VALUE_PARAMETER, FIELD)
@Retention(AnnotationRetention.BINARY)
annotation class E6

var some: Int
    @E4
    get() = 42
    @E5
    set(value) {}

class My {
    @E6
    override fun hashCode() = 0
}

interface Base {
    val bar: Int

    val baz: Int

    @E6
    fun foo()

    fun String.withReceiver()
}

class Derived : Base {
    @E6
    override val bar: Int = 42

    @set:E6 @setparam:E6
    override var baz: Int = 13

    @E6
    override fun foo() {}

    override fun @receiver:E6 String.withReceiver() {}
}

abstract class Another : Base {
    @delegate:E6
    override val bar: Int by lazy { 42 }
}