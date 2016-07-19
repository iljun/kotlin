package org.kotlinnative.translator.llvm

import org.jetbrains.kotlin.builtins.isFunctionTypeOrSubtype
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.kotlinnative.translator.llvm.types.*


fun LLVMFunctionDescriptor(name: String, argTypes: List<LLVMVariable>?, returnType: LLVMType, declare: Boolean = false, arm: Boolean = false) =
        "${if (declare) "declare" else "define"} $returnType @$name(${
        argTypes?.mapIndexed { i: Int, s: LLVMVariable ->
            "${s.getType()} ${if (s.type is LLVMReferenceType && !(s.type as LLVMReferenceType).byRef) "byval" else ""} %${s.label}"
        }?.joinToString()}) ${if (arm) "#0" else ""}"

fun LLVMMapStandardType(name: String, type: KotlinType, scope: LLVMScope = LLVMRegisterScope()): LLVMVariable = when {
    type.isFunctionTypeOrSubtype -> LLVMVariable(name, LLVMFunctionType(type), type.toString(), scope, pointer = 1)
    type.toString() == "Int" -> LLVMVariable(name, LLVMIntType(), type.toString(), scope)
    type.toString() == "Double" -> LLVMVariable(name, LLVMDoubleType(), type.toString(), scope)
    type.isUnit() -> LLVMVariable("", LLVMVoidType(), "", scope)
    type.isMarkedNullable -> LLVMVariable(name, LLVMReferenceType("${type.toString().dropLast(1)}"), name, scope, pointer = 1)
    else -> LLVMVariable(name, LLVMReferenceType("$type"), name, scope, pointer = 1)
}
