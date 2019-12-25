package me.shika.generation

import org.jetbrains.kotlin.backend.common.BackendContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind.*
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irThrowIse
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.impl.IrAnonymousInitializerImpl
import org.jetbrains.kotlin.ir.expressions.IrStatementOriginImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrAnonymousInitializerSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrClassSymbolImpl
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.ConstantValueGenerator
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.ir.util.TypeTranslator
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.irConstructorCall
import org.jetbrains.kotlin.ir.util.module
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter.Companion.FUNCTIONS
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.KotlinType

class MockIrGeneration : IrGenerationExtension {
    val MOCK_ORIGIN = object : IrDeclarationOriginImpl("mock", isSynthetic = true) { }
    val INTERCEPTOR_FQ = ClassId.fromString("Interceptor")

    override fun generate(file: IrFile, backendContext: BackendContext, bindingContext: BindingContext) {
        val executeLater = mutableListOf<() -> Unit>()
        file.traverseClasses { cls ->
            cls.traverseProperties {
                executeLater += { generateMockForProperty(cls, it, backendContext) }
            }
        }
        executeLater.forEach { it() }
    }

    private fun generateMockForProperty(irClass: IrClass, irProperty: IrProperty, backendContext: BackendContext) {
        if (irProperty.annotations.none { it.descriptor.containingDeclaration.name == Name.identifier("Mock") }) {
            return // Not a mock
        }

        val targetType = irProperty.descriptor.type
        when (targetType.classDescriptor.kind) {
            INTERFACE -> generateInterfaceImpl(irClass, irProperty, targetType, backendContext)
            else -> TODO()
        }

    }

    private fun generateInterfaceImpl(testClass: IrClass, irProperty: IrProperty, targetType: KotlinType, backendContext: BackendContext) {
        val symbolTable = SymbolTable()
        val typeTranslator = TypeTranslator(
            backendContext.ir.symbols.externalSymbolTable,
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
                val symbol = IrSimpleTypeImpl(implClass.symbol, hasQuestionMark = false, arguments = emptyList(), annotations = emptyList())

                implClass.superTypes += targetIrType
                implClass.thisReceiver = buildValueParameter {
                    type = symbol
                    name = Name.identifier("$implClass")
                }.also {
                    it.parent = implClass
                }
                val interceptorField = implClass.addField(fieldName = "interceptor", fieldType = interceptorIrType)
                implClass.addConstructor {
                    origin = MOCK_ORIGIN
                    isPrimary = true
                    returnType = symbol
                }.also { ctor ->
                    val interceptorParam = ctor.addValueParameter(
                        name = "interceptor",
                        type = interceptorIrType,
                        origin = MOCK_ORIGIN
                    )
                    ctor.body = backendContext.createIrBuilder(ctor.symbol).irBlockBody {
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
                                overridenFunction.valueParameters.forEachIndexed { index, irValueParameter ->
                                    call.putValueArgument(index, irGet(irValueParameter))
                                }
                                call.putTypeArgument(0, backendContext.irBuiltIns.anyNType)
                            }
                            val interceptor = irGetField(irGet(overridenFunction.dispatchReceiverParameter!!), interceptorField)
                            val interceptFunction = interceptorIrType.getClass()!!.getSimpleFunction("interceptCall")!!

                            val interceptedValue = irTemporary(irCall(interceptFunction).also { call ->
                                call.dispatchReceiver = interceptor
                                call.putValueArgument(0, nameParam)
                                call.putValueArgument(1, arrayOfParams)
                            })

                            +irReturn(
                                irIfNull(
                                    type = backendContext.irBuiltIns.anyNType,
                                    subject = irGet(interceptedValue),
                                    thenPart = irThrowIse(),
                                    elsePart = irGet(interceptedValue)
                                )
                            )
                        }
                    }
                }
            }
        testClass.addChild(implClass)

        testClass.addChild(
            IrAnonymousInitializerImpl(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                origin = MOCK_ORIGIN,
                symbol = IrAnonymousInitializerSymbolImpl(testClass.symbol),
                isStatic = false
            ).also { initializer ->
                initializer.body = backendContext.createIrBuilder(initializer.symbol).irBlockBody {
//                    val initClass = irCall(implClass.primaryConstructor!!.symbol)
//                    irProperty.setter?.let {
//                        +irSet(it.returnType, irGet(testClass.thisReceiver!!), it.symbol, initClass)
//                    }
                }
            }
        )
    }
}

private fun IrFile.traverseClasses(block: (IrClass) -> Unit) =
    acceptChildrenVoid(
        object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitClass(declaration: IrClass) {
                declaration.acceptChildrenVoid(this)
                block(declaration)
            }
        }
    )

private fun IrClass.traverseProperties(block: (IrProperty) -> Unit) =
    acceptChildrenVoid(
        object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
            }

            override fun visitProperty(declaration: IrProperty) {
                super.visitProperty(declaration)
                block(declaration)
            }
        }
    )

private val KotlinType.classDescriptor
    get() = (constructor.declarationDescriptor as ClassDescriptor)
