/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.declarative.codegen.http.HttpFields;
import io.helidon.declarative.codegen.http.webserver.spi.HttpParameterCodegenProvider;
import io.helidon.service.codegen.DefaultsCodegen;
import io.helidon.service.codegen.DefaultsParams;

import static io.helidon.declarative.codegen.http.HttpTypes.BAD_REQUEST_EXCEPTION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_HEADER_PARAM_ANNOTATION;

class ParamProviderHttpHeader extends AbstractParametersProvider implements HttpParameterCodegenProvider {
    @Override
    public boolean codegen(ParameterCodegenContext ctx) {
        Optional<Annotation> headerParam = headerAnnotation(ctx);
        if (headerParam.isEmpty()) {
            return false;
        }

        codegenHeader(ctx, headerParam.get());
        return true;
    }

    private Optional<Annotation> headerAnnotation(ParameterCodegenContext ctx) {
        return ctx.annotations().stream()
                .filter(it -> HTTP_HEADER_PARAM_ANNOTATION.equals(it.typeName()))
                .findFirst();
    }

    private void codegenHeader(ParameterCodegenContext ctx, Annotation headerParam) {
        String headerParamName = headerParam.value()
                .orElseThrow(() -> new CodegenException("@HeaderParam annotation must have a value."));
        String headerConstantName = HttpFields.ensureHeaderNameConstant(ctx.fieldHandler(), headerParamName);

        TypeName parameterType = ctx.parameterType();
        TypeName realType = parameterType.isOptional() ? parameterType.typeArguments().getFirst() : parameterType;
        var defaultCode = DefaultsCodegen.findDefault(ctx.annotations(), realType);

        if (realType.isList()) {
            codegenListHeader(ctx,
                              headerParamName,
                              headerConstantName,
                              parameterType.isOptional(),
                              realType.typeArguments().getFirst(),
                              defaultCode);
            return;
        }

        codegenSingleHeader(ctx,
                            headerParamName,
                            headerConstantName,
                            parameterType.isOptional(),
                            realType,
                            defaultCode);
    }

    private void codegenListHeader(ParameterCodegenContext ctx,
                                   String headerParamName,
                                   String headerConstantName,
                                   boolean optional,
                                   TypeName itemType,
                                   Optional<DefaultsCodegen.DefaultCode> defaultCode) {
        ContentBuilder<?> contentBuilder = ctx.contentBuilder();
        contentBuilder.addContent(ctx.serverRequestParamName())
                .addContent(".headers()")
                .addContent(".find(")
                .addContent(headerConstantName)
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(".map(header -> ");
        if (TypeNames.STRING.equals(itemType)) {
            contentBuilder.addContent("header.allValues())");
        } else {
            contentBuilder.addContent("header.allValues()")
                    .addContentLine(".stream()")
                    .addContent(".map(value -> ");
            mapStringValue(ctx,
                           contentBuilder,
                           itemType,
                           "value",
                           headerParamName,
                           "headers");
            contentBuilder.addContentLine(")")
                    .addContent(".collect(")
                    .addContent(Collectors.class)
                    .addContent(".toList()))");
        }

        finishHeaderValue(ctx, headerConstantName, optional, defaultCode);
    }

    private void codegenSingleHeader(ParameterCodegenContext ctx,
                                     String headerParamName,
                                     String headerConstantName,
                                     boolean optional,
                                     TypeName realType,
                                     Optional<DefaultsCodegen.DefaultCode> defaultCode) {
        ContentBuilder<?> contentBuilder = ctx.contentBuilder();
        contentBuilder.addContent(ctx.serverRequestParamName())
                .addContent(".headers()")
                .addContent(".find(")
                .addContent(headerConstantName)
                .addContentLine(")")
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(".map(header -> ");
        if (TypeNames.STRING.equals(realType)) {
            contentBuilder.addContent("header.get())");
        } else {
            mapStringValue(ctx,
                           contentBuilder,
                           realType,
                           "header.get()",
                           headerParamName,
                           "headers");
            contentBuilder.addContent(")");
        }

        finishHeaderValue(ctx, headerConstantName, optional, defaultCode);
    }

    private void finishHeaderValue(ParameterCodegenContext ctx,
                                   String headerConstantName,
                                   boolean optional,
                                   Optional<DefaultsCodegen.DefaultCode> defaultCode) {
        ContentBuilder<?> contentBuilder = ctx.contentBuilder();
        if (optional || defaultCode.isPresent()) {
            contentBuilder.addContentLine(";")
                    .decreaseContentPadding()
                    .decreaseContentPadding();
            defaultCode.ifPresent(defaultInfo -> codegenHeaderDefault(ctx, contentBuilder, defaultInfo));
            return;
        }

        contentBuilder.addContentLine()
                .addContent(".orElseThrow(() -> new ")
                .addContent(BAD_REQUEST_EXCEPTION)
                .addContent("(\"Header \" + ")
                .addContent(headerConstantName)
                .addContent(".defaultCase() + ")
                .addContentLiteral(" is not present in the request.")
                .addContentLine("));")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void codegenHeaderDefault(ParameterCodegenContext ctx,
                                      ContentBuilder<?> contentBuilder,
                                      DefaultsCodegen.DefaultCode defaultInfo) {
        if (defaultInfo.requiresMapper()) {
            ensureMapperField(ctx.fieldHandler());
        }

        DefaultsCodegen.codegenOptional(contentBuilder,
                                        defaultInfo,
                                        ctx.fieldHandler(),
                                        DefaultsParams.builder()
                                                .contextField(ctx.serverRequestParamName() + ".headers()")
                                                .mapperQualifier("headers")
                                                .mappersField("mappers")
                                                .build());
        contentBuilder.addContentLine(";");
    }

    @Override
    protected String providerType() {
        return "Header";
    }
}
