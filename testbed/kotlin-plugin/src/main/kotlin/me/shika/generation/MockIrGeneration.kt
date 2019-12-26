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
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
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
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.impl.IrAnonymousInitializerImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetClassImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
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
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.KotlinType

val MOCK_ORIGIN = object : IrDeclarationOriginImpl("mock", isSynthetic = true) { }
val INTERCEPTOR_FQ = ClassId.fromString("Interceptor")
val PRINTLN_INTERCEPTOR_FQ = ClassId.fromString("PrintlnInterceptor")

class MockIrGeneration : IrGenerationExtension {

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
        val resultClass = when (targetType.classDescriptor.kind) {
            INTERFACE -> generateInterfaceImpl(irClass, irProperty, targetType, backendContext)
            else -> generateClassImpl(irClass, irProperty, targetType, backendContext)
        }

        val symbolTable = backendContext.ir.symbols.externalSymbolTable
        val printlnInterceptorDescriptor = irClass.module.findClassAcrossModuleDependencies(PRINTLN_INTERCEPTOR_FQ)!!
        val printlnInterceptorSymbol = symbolTable.referenceClass(printlnInterceptorDescriptor)

        irClass.addChild(
            IrAnonymousInitializerImpl(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                origin = MOCK_ORIGIN,
                symbol = IrAnonymousInitializerSymbolImpl(irClass.symbol),
                isStatic = false
            ).also { initializer ->
                initializer.body = backendContext.createIrBuilder(initializer.symbol).irBlockBody {
                    val interceptorInstance = irCall(printlnInterceptorSymbol.constructors.single())

                    val initClass = irCall(resultClass.constructors.first {
                        it.valueParameters.firstOrNull { it.name == Name.identifier("interceptor") } != null
                    }).also {
                        it.putValueArgument(0, interceptorInstance)
                    }
                    irProperty.setter?.let {
                        +irSet(it.returnType, irGet(irClass.thisReceiver!!), it.symbol, initClass)
                    }
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

val KotlinType.classDescriptor
    get() = (constructor.declarationDescriptor as ClassDescriptor)
