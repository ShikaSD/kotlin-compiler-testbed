package me.shika.generation

import org.jetbrains.kotlin.backend.common.BackendContext
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irEqualsNull
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irNotEquals
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.impl.IrExpressionBodyImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetClassImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.ConstantValueGenerator
import org.jetbrains.kotlin.ir.util.TypeTranslator
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.module
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.KotlinType

fun generateClassImpl(testClass: IrClass, irProperty: IrProperty, targetType: KotlinType, backendContext: BackendContext): IrClass {
    val symbolTable = backendContext.ir.symbols.externalSymbolTable
    val typeTranslator = TypeTranslator(
        symbolTable,
        backendContext.irBuiltIns.languageVersionSettings,
        backendContext.builtIns
    ).apply {
        this.constantValueGenerator = ConstantValueGenerator(testClass.module, symbolTable)
    }
    val targetIrType = typeTranslator.translateType(targetType)
    val targetIrClass = targetIrType.getClass()!!

    val interceptorDescriptor = backendContext.ir.irModule.descriptor.findClassAcrossModuleDependencies(INTERCEPTOR_FQ)!!
    val interceptorType = typeTranslator.translateType(interceptorDescriptor.defaultType)

    val interceptorField = buildField {
        name = Name.identifier("\$interceptor")
        type = interceptorType
    }
    targetIrClass.addChild(interceptorField)

    targetIrClass.addConstructor {
        origin = MOCK_ORIGIN
        isPrimary = true
        returnType = targetIrType
    }.also { ctor ->
        val interceptorParam = ctor.addValueParameter(
            name = "interceptor",
            type = interceptorType,
            origin = MOCK_ORIGIN
        )
        ctor.body = backendContext.createIrBuilder(ctor.symbol).irBlockBody {
            +irDelegatingConstructorCall(backendContext.irBuiltIns.anyClass.owner.constructors.single())
            +irSetField(irGet(targetIrClass.thisReceiver!!), interceptorField, irGet(interceptorParam))
        }
    }

    targetIrClass.functions.forEach {
        it.body = backendContext
            .createIrBuilder(it.symbol)
            .irBlockBody(startOffset = it.startOffset, endOffset = it.endOffset) {
                val interceptFunction = interceptorType.getClass()!!.getSimpleFunction("interceptCall")!!
                val currentCls = IrGetClassImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, targetIrType, irGet(it.dispatchReceiverParameter!!))
                val nameParam = irString("${it.name}")
                val arrayOfParams = irCall(
                    backendContext.ir.symbols.arrayOf
                ).also { call ->
                    val arrayElementType = backendContext.irBuiltIns.anyNType
                    val arrayType = backendContext.ir.symbols.array.typeWith(arrayElementType)
                    val arrayElements = it.valueParameters.map { irGet(it) }
                    val arg0 = IrVarargImpl(startOffset, endOffset, arrayType, arrayElementType, arrayElements)
                    call.putValueArgument(0, arg0)
                    call.putTypeArgument(0, backendContext.irBuiltIns.anyNType)
                }

                val interceptedValue = irTemporary(irCall(interceptFunction).also { call ->
                    call.dispatchReceiver = irGetField(irGet(it.dispatchReceiverParameter!!), interceptorField)
                    call.putValueArgument(0, currentCls)
                    call.putValueArgument(1, nameParam)
                    call.putValueArgument(2, arrayOfParams)
                })


                +irIfThen(
                    type = backendContext.irBuiltIns.nothingType,
                    condition = irNotEquals(irGet(interceptedValue), irNull()),
                    thenPart = irReturn(irGet(interceptedValue))
                )

                val currentStatements = it.body?.statements.orEmpty()
                currentStatements.forEach {
                    +it
                }
            }
    }

    return targetIrClass
}
