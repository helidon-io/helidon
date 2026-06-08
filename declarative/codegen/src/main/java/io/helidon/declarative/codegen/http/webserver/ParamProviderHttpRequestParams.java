/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.http.HttpCodegenValidation;
import io.helidon.declarative.codegen.http.webserver.spi.HttpParameterCodegenProvider;
import io.helidon.service.codegen.RegistryCodegenContext;

import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_FORM_PARAM_ANNOTATION;
import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_REQUEST_PARAMS_ANNOTATION;

class ParamProviderHttpRequestParams implements HttpParameterCodegenProvider {
    private static final TypeName PARAMETERS = TypeName.create("io.helidon.common.parameters.Parameters");

    private final RegistryCodegenContext codegenContext;
    private final List<HttpParameterCodegenProvider> componentProviders;

    ParamProviderHttpRequestParams(RegistryCodegenContext codegenContext,
                                   List<HttpParameterCodegenProvider> componentProviders) {
        this.codegenContext = codegenContext;
        this.componentProviders = List.copyOf(componentProviders);
    }

    @Override
    public boolean codegen(ParameterCodegenContext ctx) {
        Optional<Annotation> first = ctx.annotations().stream()
                .filter(it -> HTTP_REQUEST_PARAMS_ANNOTATION.equals(it.typeName()))
                .findFirst();

        if (first.isEmpty()) {
            return false;
        }

        TypeInfo requestParamsType = HttpCodegenValidation.requestParamsRecordType(codegenContext::typeInfo,
                                                                                  ctx.parameterType(),
                                                                                  null);

        HttpCodegenValidation.validateRequestParamsBodyComponents(requestParamsType);

        boolean hasFormParams = hasComponentAnnotation(requestParamsType, HTTP_FORM_PARAM_ANNOTATION);
        String methodName = "requestParams_" + ctx.methodIndex() + "_" + ctx.paramIndex();
        addRequestParamsMethod(ctx, requestParamsType, methodName, hasFormParams);

        ctx.contentBuilder()
                .addContent(methodName)
                .addContent("(")
                .addContent(ctx.serverRequestParamName())
                .addContent(", ")
                .addContent(ctx.serverResponseParamName());
        if (hasFormParams) {
            ctx.contentBuilder()
                    .addContent(", ")
                    .addContent(ParamProviderHttpForm.FORM_PARAMS);
        }
        ctx.contentBuilder().addContent(");");

        return true;
    }

    private void addRequestParamsMethod(ParameterCodegenContext ctx,
                                        TypeInfo requestParamsType,
                                        String methodName,
                                        boolean hasFormParams) {
        ctx.classBuilder()
                .addMethod(method -> method
                        .accessModifier(AccessModifier.PRIVATE)
                        .returnType(ctx.parameterType())
                        .name(methodName)
                        .addParameter(req -> req
                                .type(WebServerCodegenTypes.SERVER_REQUEST)
                                .name(ctx.serverRequestParamName()))
                        .addParameter(res -> res
                                .type(WebServerCodegenTypes.SERVER_RESPONSE)
                                .name(ctx.serverResponseParamName()))
                        .update(it -> {
                            if (hasFormParams) {
                                it.addParameter(formParams -> formParams
                                        .type(ParamProviderHttpForm.LAZY_FORM_PARAMS)
                                        .name(ParamProviderHttpForm.FORM_PARAMS));
                            }
                        })
                        .update(it -> requestParamsMethodBody(ctx, it, requestParamsType)));
    }

    private void requestParamsMethodBody(ParameterCodegenContext ctx,
                                         Method.Builder method,
                                         TypeInfo requestParamsType) {
        List<TypedElementInfo> components = HttpCodegenValidation.requestParamsComponents(requestParamsType);

        for (TypedElementInfo component : components) {
            method.addContent("var ")
                    .addContent(componentName(component))
                    .addContent(" = ");
            codegenComponentParameter(ctx, method, component);
            method.addContentLine();
        }

        method.addContent("return new ")
                .addContent(ctx.parameterType())
                .addContentLine("(")
                .increaseContentPadding()
                .increaseContentPadding();

        for (int i = 0; i < components.size(); i++) {
            method.addContent(componentName(components.get(i)));
            if (i + 1 < components.size()) {
                method.addContentLine(",");
            }
        }

        method.addContentLine(");")
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private void codegenComponentParameter(ParameterCodegenContext ctx,
                                           Method.Builder method,
                                           TypedElementInfo component) {
        Set<Annotation> annotations = new HashSet<>(component.annotations());
        var componentContext = new ParamCodegenContextImpl(ctx.fieldHandler(),
                                                           annotations,
                                                           component.typeName(),
                                                           ctx.classBuilder(),
                                                           method,
                                                           ctx.serverRequestParamName(),
                                                           ctx.serverResponseParamName(),
                                                           ctx.endpointType(),
                                                           ctx.methodName(),
                                                           component.elementName(),
                                                           ctx.methodIndex(),
                                                           ctx.paramIndex());
        for (HttpParameterCodegenProvider provider : componentProviders) {
            if (provider.codegen(componentContext)) {
                return;
            }
        }

        throw new CodegenException("Record component '" + component.elementName()
                                           + "' of @Http.RequestParams type " + ctx.parameterType().fqName()
                                           + " is not annotated with a supported request parameter annotation"
                                           + " and is not a supported typed parameter.",
                                   component.originatingElementValue());
    }

    private boolean hasComponentAnnotation(TypeInfo requestParamsType, TypeName annotation) {
        return HttpCodegenValidation.requestParamsComponents(requestParamsType)
                .stream()
                .anyMatch(it -> HttpCodegenValidation.hasAnnotation(it.annotations(), annotation));
    }

    private String componentName(TypedElementInfo component) {
        return "requestParam_" + component.elementName();
    }
}
