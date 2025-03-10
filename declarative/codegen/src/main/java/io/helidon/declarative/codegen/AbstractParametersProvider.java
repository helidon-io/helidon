/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen;

import java.util.stream.Collectors;

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.declarative.codegen.DeclarativeCodegenTypes.COMMON_MAPPERS;

abstract class AbstractParametersProvider {
    void codegenFromParameters(ContentBuilder<?> contentBuilder, TypeName parameterType, String paramName, boolean optional) {
        if (optional) {
            TypeName realType = parameterType.isOptional() ? parameterType.typeArguments().getFirst() : parameterType;
            // optional
            contentBuilder
                    .addContent(".first(\"")
                    .addContent(paramName)
                    .addContent("\").");
            asMethod(contentBuilder, realType);
            contentBuilder.addContent(".asOptional()");
        } else if (parameterType.isList()) {
            TypeName realType = parameterType.typeArguments().getFirst();
            // list
            contentBuilder
                    .addContent(".allValues(\"")
                    .addContent(paramName)
                    .addContentLine("\")")
                    .addContentLine(".stream()")
                    .addContent(".map(helidonDeclarative__it -> helidonDeclarative__it.");
            getMethod(contentBuilder, realType);
            contentBuilder.addContentLine(")")
                    .addContent(".collect(")
                    .addContent(Collectors.class)
                    .addContent(".toList())");
        } else {
            // direct type
            contentBuilder
                    .addContent(".first(\"")
                    .addContent(paramName)
                    .addContent("\").");
            getMethod(contentBuilder, parameterType);
        }

    }

    void asMethod(ContentBuilder<?> content, TypeName type) {
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
                .addContent(boxed)
                .addContent(".class)");
    }

    void getMethod(ContentBuilder<?> content, TypeName type) {
        TypeName boxed = type.boxed();

        if (TypeNames.BOXED_BOOLEAN.equals(boxed)) {
            content.addContent("getBoolean()");
            return;
        }

        if (TypeNames.BOXED_DOUBLE.equals(boxed)) {
            content.addContent("getDouble()");
            return;
        }

        if (TypeNames.BOXED_INT.equals(boxed)) {
            content.addContent("getInt()");
            return;
        }

        if (TypeNames.BOXED_LONG.equals(boxed)) {
            content.addContent("getLong()");
            return;
        }

        if (TypeNames.STRING.equals(type)) {
            content.addContent("getString()");
            return;
        }

        content.addContent("get(")
                .addContent(boxed)
                .addContent(".class)");
    }

    void ensureMapperField(ParameterCodegenContext ctx) {
        ctx.fieldHandler()
                .field(COMMON_MAPPERS,
                       "mappers",
                       AccessModifier.PRIVATE,
                       field -> {
                       },
                       constructor -> constructor
                               .addParameter(param -> param.name("mappers")
                                       .type(COMMON_MAPPERS))
                               .addContentLine("this.mappers = mappers;")
                );
    }
}
