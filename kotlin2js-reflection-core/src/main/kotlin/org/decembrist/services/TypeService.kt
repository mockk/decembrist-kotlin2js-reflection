package org.decembrist.services

import org.decembrist.domain.Import
import org.decembrist.parser.KotlinParser.*
import org.decembrist.services.ImportService.retrievePackageName
import org.decembrist.services.typecontexts.ModifiedProjection
import org.decembrist.services.typecontexts.VarargsType
import org.decembrist.services.typesuggestions.*
import org.decembrist.services.typesuggestions.TypeSuggestion.Type
import org.decembrist.services.typesuggestions.TypeSuggestion.Unknown
import java.util.Collections.singletonList
import org.decembrist.services.typecontexts.StarType as ContextStarType

object TypeService {

    /**
     * @return connected [Type] or [Unknown] on [className] without package
     */
    fun getTypeSuggestion(className: String, nullable: Boolean = false): TypeSuggestion {
        val packageName = className.substringBeforeLast(".")
        val clazz = className.substringAfterLast(".")
        return if (packageName == className) {
            Unknown(clazz, nullable = nullable)
        } else {
            Type(clazz, packageName, nullable = nullable)
        }
    }

    fun getTypeSuggestion(className: String,
                          packageName: String,
                          nullable: Boolean = false): TypeSuggestion {
        val clazz = className.substringAfterLast(".")
        return if (packageName == className) {
            Unknown(clazz, nullable = nullable)
        } else {
            Type(clazz, packageName, nullable = nullable)
        }
    }

    fun getTypeSuggestion(ctx: TypeContext, imports: Collection<Import>): TypeSuggestion {
        val typeReferenceContext =
            ctx.typeReference() ?: ctx.nullableType()?.typeReference()
        //TODO nullable types
        val simpleTypes = typeReferenceContext
                ?.userType()
                ?.simpleUserType()
                .orEmpty()
        val typeContext = simpleTypes.lastOrNull()
        return if (typeContext != null) {
            val projections = typeContext.typeArguments()
                    ?.typeProjection()
                    .orEmpty()
            val typeName = makeTypeName(simpleTypes)
            var result = typeSuggestionFromImports(typeName, imports, ctx)
            if (projections.isNotEmpty()) {
                result = result.toProjectionContainer()
                    val typeProjections = projections
                            .map { retrieveType(it, typeContext) }
                            .map { getTypeSuggestion(it, imports) }
                    result.projections = typeProjections
            }
            result
        } else {
            val typeName = ctx.text
            typeSuggestionFromImports(typeName, imports, ctx)
        }
    }

    fun getTypeSuggestion(ctx: VarargsType, imports: Collection<Import>): TypeSuggestion {
        val arrayType = TypeConstants.ARRAY.type
        val projections = singletonList(getTypeSuggestion(ctx.ctx, imports))
        return VarargsContainer(
                arrayType,
                projections
        )
    }

    fun splitFullClassName(fullClassName: String) = if (fullClassName.contains(".")) {
        val className = fullClassName.substringAfterLast(".")
        val packageName = fullClassName.substringBeforeLast(".")
        Pair(className, packageName)
    } else Pair(fullClassName, "")

    private fun makeTypeName(simpleTypes: List<SimpleUserTypeContext>): String {
        return simpleTypes
                .map(SimpleUserTypeContext::simpleIdentifier)
                .joinToString(".") { it.text }
    }

    private fun typeSuggestionFromImports(typeName: String,
                                          imports: Collection<Import>,
                                          paramCxt: TypeContext): TypeSuggestion {
        return when (typeName) {
            "*" -> StarType()
            else -> {
                val nullable = paramCxt
                        .nullableType()
                        ?.quest()
                        ?.isNotEmpty() == true

                when (paramCxt) {
                    is ModifiedProjection -> {
                        val packageName = retrievePackageName(imports, typeName)
                        if (packageName != null) {
                            Projection(
                                    typeName,
                                    nullable,
                                    packageName,
                                    paramCxt.isIN,
                                    paramCxt.isOUT)
                        } else {
                            UnknownProjection(
                                    typeName,
                                    nullable,
                                    paramCxt.isIN,
                                    paramCxt.isOUT)
                        }
                    }
                    else -> {
                        val fullClassName = ImportService.retrieveFullClass(imports, typeName)
                        getTypeSuggestion(fullClassName, nullable)
                    }
                }
            }
        }
    }

    private fun retrieveType(projection: TypeProjectionContext,
                             typeContext: SimpleUserTypeContext) = when {
        projection.MULT() != null -> ContextStarType(typeContext)
        projection.typeProjectionModifiers()?.isEmpty?.not() == true -> ModifiedProjection(
                projection,
                typeContext
        )
        else -> projection.type()
    }

}