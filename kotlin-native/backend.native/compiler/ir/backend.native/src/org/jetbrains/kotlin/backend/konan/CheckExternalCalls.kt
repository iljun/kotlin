/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan

import kotlinx.cinterop.toCValues
import llvm.*
import org.jetbrains.kotlin.backend.konan.llvm.*

private fun getBasicBlocks(function: LLVMValueRef) =
        generateSequence(LLVMGetFirstBasicBlock(function)) { LLVMGetNextBasicBlock(it) }

private fun getInstructions(function: LLVMBasicBlockRef) =
        generateSequence(LLVMGetFirstInstruction(function)) { LLVMGetNextInstruction(it) }

private fun LLVMValueRef.isFunctionCall() = LLVMIsACallInst(this) != null || LLVMIsAInvokeInst(this) != null

private fun LLVMValueRef.isExternalFunction() = LLVMGetFirstBasicBlock(this) == null


private val fastFunctions = setOf(
        "_ZNSt3__112__next_primeEm",    // std::__1::__next_prime(unsigned long)
        "_ZNSt3__16chrono12steady_clock3nowEv",    // std::__1::chrono::steady_clock::now()
        "_ZNSt3__19to_stringEi",    // std::__1::to_string(int)
        "_ZSt13set_terminatePFvvE",    // std::set_terminate(void (*)())

        // C++-exceptions related staff, it probably never happens
        "__cxa_allocate_exception", "__cxa_throw", "__cxa_begin_catch", "__cxa_end_catch",
        "_ZNSt9exceptionD2Ev", // std::exception::~exception()
        "_ZdlPv", // operator delete(void*)
        "_ZNKSt3__121__basic_string_commonILb1EE20__throw_length_errorEv", // std::__1::__basic_string_common<true>::__throw_length_error() const
        "_ZNKSt3__120__vector_base_commonILb1EE20__throw_length_errorEv", // std::__1::__vector_base_common<true>::__throw_length_error() const
        "_ZSt9terminatev", // std::terminate()
        "_ZSt17current_exceptionv", // std::current_exception()
        "_ZNSt13exception_ptrC1ERKS_", // std::exception_ptr::exception_ptr(std::exception_ptr const&)
        "_ZSt17rethrow_exceptionSt13exception_ptr", // std::rethrow_exception(std::exception_ptr)
        "_ZNSt13exception_ptrD1Ev", // std::exception_ptr::~exception_ptr()

        // fast libc functions
        "__exp10", "acos", "acosf", "acosh", "acoshf", "asin", "asinf", "asinh", "asinhf", "atan", "atan2", "atan2f", "atanf", "atanh",
        "atanhf", "cosh", "coshf", "expm1", "expm1f", "hypot", "hypotf", "log1p", "log1pf", "nextafter", "nextafterf", "remainder",
        "remainderf", "sinh", "sinhf", "tan", "tanf", "tanh", "tanhf",
        "memcmp", "memmem", "strcmp", "strlen", "strnlen", "snprintf", "vsnprintf", "gettimeofday",
        "calloc", "free", "exit",


        // non waiting pthread functions
        "\u0001_pthread_cond_init", "pthread_cond_destroy", "pthread_create", "pthread_detach",
        "pthread_equal", "pthread_getspecific", "pthread_key_create", "pthread_main_np",
        "pthread_mutex_destroy", "pthread_mutex_init", "pthread_once",
        "pthread_self", "pthread_setspecific", "pthread_mutex_unlock",


        // TODO: objc function i don't know
        "CFStringCreateCopy",
        "CFStringGetCharacters",
        "CFStringGetLength",
        "NSStringFromClass",
        "class_addIvar",
        "class_addMethod",
        "class_addProtocol",
        "class_copyMethodList",
        "class_copyProtocolList",
        "class_getClassMethod",
        "class_getInstanceMethod",
        "class_getInstanceVariable",
        "class_getName",
        "class_getSuperclass",
        "class_isMetaClass",
        "dispatch_once",
        "ivar_getOffset",
        "method_getName",
        "method_getTypeEncoding",
        "objc_alloc",
        "objc_allocWithZone",
        "objc_allocateClassPair",
        "objc_autorelease",
        "objc_autoreleasePoolPop",
        "objc_autoreleasePoolPush",
        "objc_autoreleaseReturnValue",
        "objc_destroyWeak",
        "objc_enumerationMutation",
        "objc_getAssociatedObject",
        "objc_getClass",
        "objc_getProtocol",
        "objc_loadWeakRetained",
        "objc_lookUpClass",
        "objc_msgSend",
        "objc_msgSendSuper2",
        "objc_registerClassPair",
        "objc_release",
        "objc_retain",
        "objc_retainAutoreleaseReturnValue",
        "objc_retainBlock",
        "objc_setAssociatedObject",
        "objc_storeWeak",
        "objc_terminate",
        "object_getClass",
        "object_getClassName",
        "protocol_copyProtocolList",
        "protocol_getMethodDescription",
        "protocol_getName",
        "sel_getName",
        "sel_registerName",
)

private fun LLVMValueRef.isAssumedFast(): Boolean {
    val name = this.name ?: return false
    return name.startsWith("llvm.") || name in fastFunctions
}

// returns null for inderect calls
private fun LLVMValueRef.getCalledFunction(): LLVMValueRef? {
    fun cleanCalledFunction(value: LLVMValueRef): LLVMValueRef? {
        return when {
            LLVMIsAFunction(value) != null -> value
            LLVMIsACastInst(value) != null -> cleanCalledFunction(LLVMGetOperand(value, 0)!!)
            LLVMIsALoadInst(value) != null -> null // this is a virtual call
            LLVMIsAArgument(value) != null -> null // this is a callback call
            LLVMIsAInlineAsm(value) != null -> null // this is inline assembly call
            LLVMIsAPHINode(value) != null -> null // this is some kind of indirect calls
            LLVMIsASelectInst(value) != null -> null // this is some kind of indirect calls
            LLVMIsAConstantExpr(value) != null -> {
                when (LLVMGetConstOpcode(value)) {
                    LLVMOpcode.LLVMBitCast -> cleanCalledFunction(LLVMGetOperand(value, 0)!!)
                    else -> TODO("not implemented constant type in call")
                }
            }
            LLVMIsAGlobalAlias(value) != null -> return cleanCalledFunction(LLVMAliasGetAliasee(value)!!)
            else -> {
                LLVMDumpValue(this)
                println()
                LLVMDumpValue(value)
                println()
                TODO("not implemented call argument")
            }
        }
    }

    return cleanCalledFunction(LLVMGetCalledValue(this)!!)
}

private fun checkBasicBlock(functionName: String, block: LLVMBasicBlockRef, checkRuntimeFunction: LLVMValueRef, context: Context) {
    val calls = getInstructions(block)
            .filter { it.isFunctionCall() }
            .toList()
    val builder = LLVMCreateBuilderInContext(llvmContext)
    for (call in calls) {
        val called = call.getCalledFunction()
        if (called == null || !called.isExternalFunction() || called.isAssumedFast()) {
            continue
        }
        LLVMPositionBuilderBefore(builder, call)
        val functionNameLlvm = context.llvm.staticData.cStringLiteral(functionName).llvm
        val calledNameLlvm = context.llvm.staticData.cStringLiteral(called.name!!).llvm
        LLVMBuildCall(builder, checkRuntimeFunction, listOf(functionNameLlvm, calledNameLlvm).toCValues(), 2, "")
    }
    LLVMDisposeBuilder(builder)
}

private fun checkFunction(function: LLVMValueRef, checkRuntimeFunction: LLVMValueRef, context: Context) {
    getBasicBlocks(function).forEach {
        checkBasicBlock(function.name!!, it, checkRuntimeFunction, context)
    }
}

internal fun checkLlvmModuleExternalCalls(context: Context) {
    val checkRuntimeFunctionName = "Kotlin_mm_checkStateAtExternalFunctionCall"
    val checkRuntimeFunction = LLVMGetNamedFunction(context.llvmModule, checkRuntimeFunctionName)
            ?: throw IllegalStateException("${checkRuntimeFunctionName} function is not available")
    getFunctions(context.llvmModule!!).forEach {
        checkFunction(it, checkRuntimeFunction, context)
    }
}