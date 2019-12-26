package me.shika.generation

import org.jetbrains.kotlin.backend.common.BackendContext
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irThrowIse
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.impl.IrAnonymousInitializerImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetClassImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.expressions.putValueArgument
import org.jetbrains.kotlin.ir.symbols.impl.IrAnonymousInitializerSymbolImpl
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.ConstantValueGenerator
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.ir.util.TypeTranslator
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.module
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.KotlinType

fun generateInterfaceImpl(testClass: IrClass, irProperty: IrProperty, targetType: KotlinType, backendContext: BackendContext): IrClass {
    val symbolTable = backendContext.ir.symbols.externalSymbolTable
    val typeTranslator = TypeTranslator(
        symbolTable,
        backendContext.irBuiltIns.languageVersionSettings,
        backendContext.builtIns
    ).apply {
        this.constantValueGenerator = ConstantValueGenerator(testClass.module, symbolTable)
    }
    val targetTypeDescriptor = targetType.classDescriptor
    val targetIrType = typeTranslator.translateType(targetType)

    val interceptorDescriptor = testClass.module.findClassAcrossModuleDependencies(INTERCEPTOR_FQ)
    val interceptorIrType = typeTranslator.translateType(interceptorDescriptor!!.defaultType)

    val implClass =
        buildClass {
            origin = MOCK_ORIGIN
            name = Name.identifier("${irProperty.name}_Mock")
        }.also { implClass ->
            val implClassType = IrSimpleTypeImpl(implClass.symbol, hasQuestionMark = false, arguments = emptyList(), annotations = emptyList())

            implClass.superTypes += targetIrType
            implClass.superTypes += backendContext.irBuiltIns.anyType
            implClass.thisReceiver = buildValueParameter {
                type = implClassType
                name = Name.identifier("$this")
            }.also {
                it.parent = implClass
            }
            val interceptorField = implClass.addField(fieldName = "\$interceptor", fieldType = interceptorIrType)
            implClass.addConstructor {
                origin = MOCK_ORIGIN
                isPrimary = true
                returnType = implClassType
            }.also { ctor ->
                val interceptorParam = ctor.addValueParameter(
                    name = "interceptor",
                    type = interceptorIrType,
                    origin = MOCK_ORIGIN
                )
                ctor.body = backendContext.createIrBuilder(ctor.symbol).irBlockBody {
                    +irDelegatingConstructorCall(backendContext.irBuiltIns.anyClass.owner.constructors.single())
                    +irSetField(irGet(implClass.thisReceiver!!), interceptorField, irGet(interceptorParam))
                }
            }
            val targetFunctions = targetIrType.getClass()?.functions ?: emptySequence()
            targetFunctions.forEach { originalFunction ->
                implClass.addFunction {
                    name = originalFunction.name
                    returnType = originalFunction.returnType
                    origin = MOCK_ORIGIN
                }.also { overridenFunction ->
                    overridenFunction.overriddenSymbols += originalFunction.symbol
                    overridenFunction.valueParameters += originalFunction.valueParameters
                    overridenFunction.typeParameters += originalFunction.typeParameters
                    overridenFunction.dispatchReceiverParameter = implClass.thisReceiver!!.copyTo(overridenFunction)
                    overridenFunction.body = backendContext.createIrBuilder(overridenFunction.symbol).irBlockBody {
                        val nameParam = irString(originalFunction.name.asString())
                        val arrayOfParams = irCall(
                            backendContext.ir.symbols.arrayOf
                        ).also { call ->
                            val arrayElementType = backendContext.irBuiltIns.anyNType
                            val arrayType = backendContext.ir.symbols.array.typeWith(arrayElementType)
                            val arrayElements = overridenFunction.valueParameters.map { irGet(it) }
                            val arg0 = IrVarargImpl(startOffset, endOffset, arrayType, arrayElementType, arrayElements)
                            call.putValueArgument(0, arg0)
                            call.putTypeArgument(0, backendContext.irBuiltIns.anyNType)
                        }
                        val interceptor = irGetField(irGet(overridenFunction.dispatchReceiverParameter!!), interceptorField)
                        val interceptFunction = interceptorIrType.getClass()!!.getSimpleFunction("interceptCall")!!
                        val currentCls = IrGetClassImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, implClass.defaultType, irGet(overridenFunction.dispatchReceiverParameter!!))

                        val interceptedValue = irTemporary(irCall(interceptFunction).also { call ->
                            call.dispatchReceiver = interceptor
                            call.putValueArgument(0, currentCls)
                            call.putValueArgument(1, nameParam)
                            call.putValueArgument(2, arrayOfParams)
                        })

                        +irReturn(
                            irIfNull(
                                type = backendContext.irBuiltIns.anyNType,
                                subject = irGet(interceptedValue),
                                thenPart = irThrow(
                                    irCall(backendContext.irBuiltIns.illegalArgumentExceptionSymbol).also {
                                        it.putValueArgument(0, irString("Mock interceptor returned null"))
                                    }
                                ),
                                elsePart = irGet(interceptedValue)
                            )
                        )
                    }
                }
            }
        }
    testClass.addChild(implClass)

    return implClass
}
