/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.declarative.codegen.http.webserver;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.codegen.FieldHandler;

import static io.helidon.declarative.codegen.DeclarativeTypes.COMMON_MAPPERS;
import static io.helidon.declarative.codegen.http.HttpTypes.BAD_REQUEST_EXCEPTION;

/**
 * A provider of parameters when code generating call of methods with annotated parameter, such
 * as HTTP headers, path parameters etc.
 */
public abstract class AbstractParametersProvider {
    /**
     * Constructor with no side effects.
     */
    protected AbstractParametersProvider() {
    }

    /**
     * Code generate getting a value from parameters.
     *
     * @param contentBuilder content builder to update
     * @param parameterType type of the parameter we need to get
     * @param paramName name of the parameter we need to get
     * @param optional whether the parameter is optional
     */
    protected void codegenFromParameters(ParameterCodegenContext ctx,
                                         ContentBuilder<?> contentBuilder,
                                         TypeName parameterType,
                                         String paramName,
                                         boolean optional,
                                         String parametersAccessor,
                                         boolean filterEmptyStringValues,
                                         String mapperQualifier) {
        if (optional) {
            TypeName realType = parameterType.isOptional() ? parameterType.typeArguments().getFirst() : parameterType;
            if (realType.isList()) {
                contentBuilder
                        .addContent(parametersAccessor)
                        .addContent(".contains(\"")
                        .addContent(paramName)
                        .addContentLine("\")")
                        .increaseContentPadding()
                        .increaseContentPadding()
                        .addContent("? ")
                        .addContent(Optional.class)
                        .addContent(".of(");
                addListValueExpression(ctx,
                                       contentBuilder,
                                       realType.typeArguments().getFirst(),
                                       paramName,
                                       parametersAccessor,
                                       filterEmptyStringValues,
                                       mapperQualifier);
                contentBuilder.addContent(") : ")
                        .addContent(Optional.class)
                        .addContent(".<")
                        .addContent(realType)
                        .addContent(">empty()");
                contentBuilder
                        .decreaseContentPadding()
                        .decreaseContentPadding();
                return;
            }
            // optional
            contentBuilder
                    .addContent(parametersAccessor)
                    .addContent(".first(\"")
                    .addContent(paramName)
                    .addContentLine("\")");
            if (requiresMapper(realType)) {
                contentBuilder.addContent(".map(it -> ");
                mapStringValue(ctx,
                               contentBuilder,
                               realType,
                               "it",
                               paramName,
                               mapperQualifier);
                contentBuilder.addContent(")");
            } else {
                contentBuilder.addContent(".asOptional()");
            }
        } else if (parameterType.isList()) {
            TypeName realType = parameterType.typeArguments().getFirst();
            // list
            addListValueExpression(ctx,
                                   contentBuilder,
                                   realType,
                                   paramName,
                                   parametersAccessor,
                                   filterEmptyStringValues,
                                   mapperQualifier);
        } else {
            // direct type
            contentBuilder
                    .addContent(parametersAccessor)
                    .addContent(".first(\"")
                    .addContent(paramName)
                    .addContentLine("\")")
                    .increaseContentPadding()
                    .increaseContentPadding();
            if (requiresMapper(parameterType)) {
                contentBuilder.addContent(".map(it -> ");
                mapStringValue(ctx,
                               contentBuilder,
                               parameterType,
                               "it",
                               paramName,
                               mapperQualifier);
                contentBuilder.addContent(")");
            }
            // add .orElseThrow() in case the parameter is missing
            contentBuilder.addContentLine()
                    .addContent(".orElseThrow(() -> new ")
                    .addContent(BAD_REQUEST_EXCEPTION)
                    .addContent("(\"")
                    .addContent(providerType())
                    .addContent(" ")
                    .addContent(paramName)
                    .addContentLine(" is not present in the request.\"));")
                    .decreaseContentPadding()
                    .decreaseContentPadding();
        }
    }

    protected void codegenFromParameters(FieldHandler fieldHandler,
                                         ContentBuilder<?> contentBuilder,
                                         TypeName parameterType,
                                         String paramName,
                                         boolean optional) {
        codegenFromParameters(new ParamCodegenContextImpl(fieldHandler,
                                                         Set.of(),
                                                         parameterType,
                                                         null,
                                                         contentBuilder,
                                                         "",
                                                         "",
                                                         TypeNames.OBJECT,
                                                         "",
                                                         paramName,
                                                         0,
                                                         0),
                              contentBuilder,
                              parameterType,
                              paramName,
                              optional,
                              "",
                              false,
                              "http/path");
    }

    /**
     * Type of this provider, such as "header", used when code generating error messages.
     *
     * @return provider type
     */
    protected abstract String providerType();

    boolean requiresMapper(TypeName type) {
        return !TypeNames.STRING.equals(type);
    }

    void asMethod(ParameterCodegenContext ctx, ContentBuilder<?> content, TypeName type) {
        TypeName boxed = type.boxed();

        if (TypeNames.BOXED_BOOLEAN.equals(boxed)) {
            content.addContent("asBoolean()");
            return;
        }

        if (TypeNames.BOXED_DOUBLE.equals(boxed)) {
            content.addContent("asDouble()");
            return;
        }

        if (TypeNames.BOXED_INT.equals(boxed)) {
            content.addContent("asInt()");
            return;
        }

        if (TypeNames.BOXED_LONG.equals(boxed)) {
            content.addContent("asLong()");
            return;
        }

        if (TypeNames.STRING.equals(type)) {
            content.addContent("asString()");
            return;
        }

        content.addContent("as(")
                .addContent(genericTypeConstant(ctx, boxed))
                .addContent(")");
    }

    void mapStringValue(ParameterCodegenContext ctx,
                        ContentBuilder<?> content,
                        TypeName type,
                        String valueExpression,
                        String paramName,
                        String qualifier) {
        if (!requiresMapper(type)) {
            content.addContent(valueExpression);
            return;
        }

        ensureMapperField(ctx);
        content.addContent("mappers.map(")
                .addContent(valueExpression)
                .addContent(", ")
                .addContent(TypeNames.GENERIC_TYPE)
                .addContent(".STRING");
        content.addContent(", ")
                .addContent(genericTypeConstant(ctx, type.boxed()))
                .addContent(", ")
                .addContent("me -> new ")
                .addContent(BAD_REQUEST_EXCEPTION)
                .addContent("(\"")
                .addContent(providerType())
                .addContent(" ")
                .addContent(paramName)
                .addContent(" has invalid value.\", me), ")
                .addContentLiteral(qualifier);
        content.addContent(")");
    }

    void addListValueExpression(ParameterCodegenContext ctx,
                                ContentBuilder<?> contentBuilder,
                                TypeName itemType,
                                String paramName,
                                String parametersAccessor,
                                boolean filterEmptyStringValues,
                                String mapperQualifier) {
        contentBuilder.addContent(parametersAccessor)
                .addContent(".all(\"")
                .addContent(paramName)
                .addContent("\")");

        if (filterEmptyStringValues || requiresMapper(itemType)) {
            contentBuilder.addContent(".stream()");
            if (filterEmptyStringValues) {
                contentBuilder.addContent(".filter(it -> !it.isEmpty())");
            }
            if (requiresMapper(itemType)) {
                contentBuilder.addContent(".map(it -> ");
                mapStringValue(ctx,
                               contentBuilder,
                               itemType,
                               "it",
                               paramName,
                               mapperQualifier);
                contentBuilder.addContent(")");
            }
            contentBuilder.addContent(".collect(")
                    .addContent(Collectors.class)
                    .addContent(".toList())");
        }
    }

    void addTypeArgument(ParameterCodegenContext ctx, ContentBuilder<?> content, TypeName type) {
        if (type.typeArguments().isEmpty()) {
            content.addContent(type)
                    .addContent(".class");
            return;
        }
        content.addContent(genericTypeConstant(ctx, type));
    }

    void ensureMapperField(ParameterCodegenContext ctx) {
        ctx.fieldHandler()
                .field(COMMON_MAPPERS,
                       "mappers",
                       AccessModifier.PRIVATE,
                       "mappers",
                       field -> {
                       },
                       (constructor, fieldName) -> constructor
                               .addParameter(param -> param.name("mappers")
                                       .type(COMMON_MAPPERS))
                               .addContentLine("this.mappers = mappers;")
                );
    }

    private String genericTypeConstant(ParameterCodegenContext ctx, TypeName type) {
        TypeName boxed = type.boxed();
        TypeName genericType = TypeName.builder(TypeNames.GENERIC_TYPE)
                .addTypeArgument(boxed)
                .build();

        return ctx.fieldHandler().constant("GTYPE",
                                           genericType,
                                           ResolvedType.create(boxed),
                                           content -> {
                                               if (boxed.typeArguments().isEmpty()) {
                                                   content.addContent(TypeNames.GENERIC_TYPE)
                                                           .addContent(".create(")
                                                           .addContent(boxed)
                                                           .addContent(".class)");
                                               } else {
                                                   content.addContent("new ")
                                                           .addContent(TypeNames.GENERIC_TYPE)
                                                           .addContent("<")
                                                           .addContent(boxed)
                                                           .addContent(">() {}");
                                               }
                                           });
    }
}
