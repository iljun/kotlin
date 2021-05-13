/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package codegen.kclass.kclass0

import kotlin.test.*
import kotlin.reflect.KClass

@Test fun runTest() {
    main(emptyArray<String>())
}

fun main(args: Array<String>) {
    checkClass(
            clazz = Any::class,
            expectedQualifiedName = "kotlin.Any",
            expectedSimpleName = "Any",
            expectedToStringName = "class kotlin.Any",
            expectedInstance = Any(),
            expectedNotInstance = null
    )
    checkClass(
            clazz = Int::class,
            expectedQualifiedName = "kotlin.Int",
            expectedSimpleName = "Int",
            expectedToStringName = "class kotlin.Int",
            expectedInstance = 42,
            expectedNotInstance = "17"
    )
    checkClass(
            clazz = String::class,
            expectedQualifiedName = "kotlin.String",
            expectedSimpleName = "String",
            expectedToStringName = "class kotlin.String",
            expectedInstance = "17",
            expectedNotInstance = 42
    )
    checkClass(
            clazz = RootClass::class,
            expectedQualifiedName = "codegen.kclass.kclass0.RootClass",
            expectedSimpleName = "RootClass",
            expectedToStringName = "class codegen.kclass.kclass0.RootClass",
            expectedInstance = RootClass(),
            expectedNotInstance = Any()
    )
    checkClass(
            clazz = RootClass.Nested::class,
            expectedQualifiedName = "codegen.kclass.kclass0.RootClass.Nested",
            expectedSimpleName = "Nested",
            expectedToStringName = "class codegen.kclass.kclass0.RootClass.Nested",
            expectedInstance = RootClass.Nested(),
            expectedNotInstance = Any()
    )

    // Local classes.
    class Local {
        val captured = args

        inner class Inner
    }
    checkClass(
            clazz = Local::class,
            expectedQualifiedName = null,
            expectedSimpleName = "Local",
            expectedToStringName = "class codegen.kclass.kclass0.main\$Local",
            expectedInstance = Local(),
            expectedNotInstance = Any()
    )
    checkClass(
            clazz = Local.Inner::class,
            expectedQualifiedName = null,
            expectedSimpleName = "Inner",
            expectedToStringName = "class codegen.kclass.kclass0.main\$Local\$Inner",
            expectedInstance = Local().Inner(),
            expectedNotInstance = Any()
    )

    // Anonymous object.
    with(object : Any() {
        val captured = args

        inner class Inner
        val innerKClass = Inner::class
    }) {
        checkClass(
                clazz = this::class,
                expectedQualifiedName = null,
                expectedSimpleName = null,
                expectedToStringName = "class codegen.kclass.kclass0.main\$1",
                expectedInstance = this,
                expectedNotInstance = Any()
        )
        checkClass(
                clazz = this.innerKClass,
                expectedQualifiedName = null,
                expectedSimpleName = "Inner",
                expectedToStringName = "class codegen.kclass.kclass0.main\$1\$Inner",
                expectedInstance = this.Inner(),
                expectedNotInstance = Any()
        )
    }

    // Anonymous object assigned to property.
    val obj = object : Any() {
        val captured = args

        inner class Inner
        val innerKClass = Inner::class
    }
    checkClass(
            clazz = obj::class,
            expectedQualifiedName = null,
            expectedSimpleName = null,
            expectedToStringName = "class codegen.kclass.kclass0.main\$obj\$1",
            expectedInstance = obj,
            expectedNotInstance = Any()
    )
    checkClass(
            clazz = obj.innerKClass,
            expectedQualifiedName = null,
            expectedSimpleName = "Inner",
            expectedToStringName = "class codegen.kclass.kclass0.main\$obj\$1\$Inner",
            expectedInstance = obj.Inner(),
            expectedNotInstance = Any()
    )

    // Interfaces:
    checkClass(
            clazz = Comparable::class,
            expectedQualifiedName = "kotlin.Comparable",
            expectedSimpleName = "Comparable",
            expectedToStringName = "class kotlin.Comparable",
            expectedInstance = 42,
            expectedNotInstance = Any()
    )
    checkClass(
            clazz = Interface::class,
            expectedQualifiedName = "codegen.kclass.kclass0.Interface",
            expectedSimpleName = "Interface",
            expectedToStringName = "class codegen.kclass.kclass0.Interface",
            expectedInstance = object : Interface {},
            expectedNotInstance = Any()
    )

    checkInstanceClass(Any(), Any::class)
    checkInstanceClass(42, Int::class)
    assert(42::class == Int::class)

    checkReifiedClass<Int>(Int::class)
    checkReifiedClass<Int?>(Int::class)
    checkReifiedClass2<Int>(Int::class)
    checkReifiedClass2<Int?>(Int::class)
    checkReifiedClass<Any>(Any::class)
    checkReifiedClass2<Any>(Any::class)
    checkReifiedClass2<Any?>(Any::class)
    checkReifiedClass<Local>(Local::class)
    checkReifiedClass2<Local>(Local::class)
    checkReifiedClass<RootClass>(RootClass::class)
    checkReifiedClass2<RootClass>(RootClass::class)
}

class RootClass {
    class Nested
}
interface Interface

fun checkClass(
        clazz: KClass<*>,
        expectedQualifiedName: String?, expectedSimpleName: String?, expectedToStringName: String,
        expectedInstance: Any, expectedNotInstance: Any?
) {
    assert(clazz.qualifiedName == expectedQualifiedName)
    assert(clazz.simpleName == expectedSimpleName)
    assert(clazz.toString() == expectedToStringName)

    assert(clazz.isInstance(expectedInstance))
    if (expectedNotInstance != null) assert(!clazz.isInstance(expectedNotInstance))
}

fun checkInstanceClass(instance: Any, clazz: KClass<*>) {
    assert(instance::class == clazz)
}

inline fun <reified T> checkReifiedClass(expectedClass: KClass<*>) {
    assert(T::class == expectedClass)
}

inline fun <reified T> checkReifiedClass2(expectedClass: KClass<*>) {
    checkReifiedClass<T>(expectedClass)
    checkReifiedClass<T?>(expectedClass)
}
