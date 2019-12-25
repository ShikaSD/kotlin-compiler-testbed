package me.shika.generation

import org.jetbrains.kotlin.backend.common.BackendContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind.*
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.impl.IrAnonymousInitializerImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrAnonymousInitializerSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrClassSymbolImpl
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.ConstantValueGenerator
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.ir.util.TypeTranslator
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.module
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter.Companion.FUNCTIONS
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.KotlinType

class MockIrGeneration : IrGenerationExtension {
    val MOCK_ORIGIN = object : IrDeclarationOriginImpl("mock", isSynthetic = true) { }

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
        val typeTranslator = TypeTranslator(
            backendContext.ir.symbols.externalSymbolTable,
            backendContext.irBuiltIns.languageVersionSettings,
            backendContext.builtIns
        ).apply {
            this.constantValueGenerator = ConstantValueGenerator(testClass.module, SymbolTable())
        }
        val targetTypeDescriptor = targetType.classDescriptor
        val targetIrType = typeTranslator.translateType(targetType)

        testClass.addChild(
            buildClass {
                origin = MOCK_ORIGIN
                name = Name.identifier("${irProperty.name}_Mock")
            }.also { implClass ->
                implClass.superTypes += targetIrType
                implClass.thisReceiver = buildValueParameter {
                    type = IrSimpleTypeImpl(implClass.symbol, hasQuestionMark = false, arguments = emptyList(), annotations = emptyList())
                    name = Name.identifier("$implClass")
                }.also {
                    it.parent = implClass
                }
                val targetFunctions = targetIrType.getClass()?.functions ?: emptySequence()
                targetFunctions.forEach { originalFunction ->
                    implClass.addFunction {
                        name = originalFunction.name
                        returnType = originalFunction.returnType.makeNullable()
                        origin = MOCK_ORIGIN
                    }.also { overridenFunction ->
                        overridenFunction.overriddenSymbols += originalFunction.symbol
                        overridenFunction.valueParameters += originalFunction.valueParameters
                        overridenFunction.typeParameters += originalFunction.typeParameters
                        overridenFunction.body = backendContext.createIrBuilder(overridenFunction.symbol).irBlockBody {
                            +irReturn(irNull())
                        }
                    }
                }
            }
        )

        testClass.addChild(
            IrAnonymousInitializerImpl(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                origin = MOCK_ORIGIN,
                symbol = IrAnonymousInitializerSymbolImpl(testClass.symbol),
                isStatic = false
            ).also { initializer ->
                initializer.body = backendContext.createIrBuilder(initializer.symbol).irBlockBody {
//                    +irSet()
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
