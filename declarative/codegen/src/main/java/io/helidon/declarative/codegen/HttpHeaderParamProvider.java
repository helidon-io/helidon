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

import java.util.Optional;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.declarative.codegen.spi.HttpParameterCodegenProvider;

import static io.helidon.codegen.CodegenUtil.toConstantName;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.HTTP_HEADER_NAME;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.HTTP_HEADER_NAMES;
import static io.helidon.declarative.codegen.WebServerCodegenTypes.HTTP_HEADER_PARAM_ANNOTATION;

class HttpHeaderParamProvider extends AbstractParametersProvider implements HttpParameterCodegenProvider {
    @Override
    public boolean codegen(ParameterCodegenContext ctx) {

        Optional<Annotation> first = ctx.qualifiers().stream()
                .filter(it -> HTTP_HEADER_PARAM_ANNOTATION.equals(it.typeName()))
                .findFirst();

        if (first.isEmpty()) {
            return false;
        }
        Annotation headerParam = first.get();
        String headerParamName = headerParam.value()
                .orElseThrow(() -> new CodegenException("@HeaderParam annotation must have a value."));

        String headerConstantName =
                toConstantName("header_" + headerParamName
                                       + "_" + ctx.methodIndex()
                                       + "_" + ctx.paramIndex());

        // add the header name constant
        ctx.classBuilder().addField(header -> header
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .isFinal(true)
                .type(HTTP_HEADER_NAME)
                .name(headerConstantName)
                .addContent(HTTP_HEADER_NAMES)
                .addContent(".create(\"")
                .addContent(headerParamName)
                .addContent("\")"));

        ContentBuilder<?> contentBuilder = ctx.contentBuilder();
        String serverRequestParamName = ctx.serverRequestParamName();
        TypeName parameterType = ctx.parameterType();

        contentBuilder.addContent(serverRequestParamName)
                .addContent(".headers()");

        // add generated code to obtain the header from request
        if (parameterType.isOptional()) {
            TypeName realType = parameterType.typeArguments().getFirst();
            contentBuilder.addContent(".contains(")
                    .addContent(headerConstantName)
                    .addContentLine(")")
                    .addContent("? ")
                    .addContent(Optional.class)
                    .addContent(".of(")
                    .addContent(serverRequestParamName)
                    .addContent(".headers().get(")
                    .addContent(headerConstantName)
                    .addContent(").");
            getMethod(contentBuilder, realType);
            contentBuilder.addContentLine(")")
                    .addContent(":")
                    .addContent(Optional.class)
                    .addContent(".empty();");
        } else {
            contentBuilder.addContent(".get(")
                    .addContent(headerConstantName)
                    .addContent(").");
            getMethod(contentBuilder, parameterType);
            contentBuilder.addContent(";");
        }

        return true;
    }

}
