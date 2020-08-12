package me.shika.generation

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.copyBodyTo
import org.jetbrains.kotlin.backend.common.ir.copyTypeParametersFrom
import org.jetbrains.kotlin.backend.common.ir.copyValueParametersFrom
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.util.copyTypeAndValueArgumentsFrom
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name

class ParamIrGeneration : IrGenerationExtension {
    private val functionMapping = mutableMapOf<IrFunction, IrFunction>()

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(AddIntParameter(pluginContext, functionMapping))
        moduleFragment.transformChildrenVoid(ReplaceCalls(pluginContext, functionMapping))
        moduleFragment.patchDeclarationParents()
    }
}

/**
 * Adds one Int param to function containing "addParam"
 */
class AddIntParameter(
    private val pluginContext: IrPluginContext,
    private val functionMapping: MutableMap<IrFunction, IrFunction>
) : IrElementTransformerVoid() {
    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement =
        super.visitSimpleFunction(declaration).let {
            if (it is IrSimpleFunction && it.shouldAddParam) {
                replaceFunction(declaration).also {
                    functionMapping[declaration] = it
                }
            } else {
                it
            }
        }

    private fun replaceFunction(declaration: IrSimpleFunction): IrFunction =
        buildFun {
            updateFrom(declaration)
            returnType = declaration.returnType
            name = declaration.name
        }.also { newFunction ->
            newFunction.apply {
                copyValueParametersFrom(declaration)
                copyTypeParametersFrom(declaration)
            }
            declaration.copyBodyTo(newFunction)

            val intValueParameter = buildValueParameter {
                name = Name.identifier("\$generated")
                index = declaration.valueParameters.size
                type = pluginContext.irBuiltIns.intType
            }
            newFunction.valueParameters += intValueParameter
        }
}

class ReplaceCalls(
    private val pluginContext: IrPluginContext,
    private val functionMapping: MutableMap<IrFunction, IrFunction>
): IrElementTransformerVoid() {
    override fun visitCall(expression: IrCall): IrExpression =
        super.visitCall(expression).let {
            if (it is IrCall && it.symbol.owner.shouldAddParam) {
                transformCall(it)
            } else {
                it
            }
        }

    private fun transformCall(call: IrCall): IrCall =
        IrCallImpl(
            startOffset = call.startOffset,
            endOffset = call.endOffset,
            type = call.type,
            symbol = functionMapping[call.symbol.owner]?.symbol ?: call.symbol,
        ).also {
            it.copyTypeAndValueArgumentsFrom(call)
            it.putValueArgument(
                it.valueArgumentsCount,
                IrConstImpl(
                    startOffset = call.startOffset,
                    endOffset = call.endOffset,
                    type = pluginContext.irBuiltIns.intType,
                    kind = IrConstKind.Int,
                    value = 0
                )
            )
        }
}

private val IrFunction.shouldAddParam: Boolean get() =
    name.identifier.contains("addParam")
