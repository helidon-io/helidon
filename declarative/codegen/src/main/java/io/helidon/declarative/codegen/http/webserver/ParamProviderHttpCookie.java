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

import java.util.Optional;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.declarative.codegen.http.HttpCodegenValidation;
import io.helidon.declarative.codegen.http.webserver.spi.HttpParameterCodegenProvider;
import io.helidon.service.codegen.DefaultsCodegen;
import io.helidon.service.codegen.DefaultsParams;

import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_COOKIE_PARAM_ANNOTATION;

class ParamProviderHttpCookie extends AbstractParametersProvider implements HttpParameterCodegenProvider {
    @Override
    public boolean codegen(ParameterCodegenContext ctx) {
        Optional<Annotation> first = ctx.annotations().stream()
                .filter(it -> HTTP_COOKIE_PARAM_ANNOTATION.equals(it.typeName()))
                .findFirst();

        if (first.isEmpty()) {
            return false;
        }

        Annotation cookieParam = first.get();
        String cookieParamName = cookieParam.value()
                .orElseThrow(() -> new CodegenException("@CookieParam annotation must have a value."));
        HttpCodegenValidation.validateCookieName(cookieParamName,
                                                 "@Http.CookieParam",
                                                 cookieParam.originatingElement().orElse(null));

        TypeName parameterType = ctx.parameterType();
        TypeName realType = parameterType.isOptional() ? parameterType.typeArguments().getFirst() : parameterType;
        Optional<DefaultsCodegen.DefaultCode> defaultCode = DefaultsCodegen.findDefault(ctx.annotations(), realType);

        ContentBuilder<?> contentBuilder = ctx.contentBuilder();

        codegenFromParameters(ctx,
                              parameterType,
                              cookieParamName,
                              parameterType.isOptional() || defaultCode.isPresent(),
                              new ParametersSource(ctx.serverRequestParamName() + ".headers().cookies()",
                                                   false,
                                                   "http/cookies"));

        if (defaultCode.isPresent()) {
            var defaultInfo = defaultCode.get();
            if (defaultInfo.requiresMapper()) {
                ensureMapperField(ctx.fieldHandler());
            }

            var params = DefaultsParams.builder()
                    .contextField(ctx.serverRequestParamName() + ".headers().cookies()")
                    .mapperQualifier("http/cookies")
                    .mappersField("mappers")
                    .build();
            DefaultsCodegen.codegenOptional(contentBuilder,
                                            defaultInfo,
                                            ctx.fieldHandler(),
                                            params);
        }

        contentBuilder.addContentLine(";");

        return true;
    }

    @Override
    protected String providerType() {
        return "Cookie";
    }
}
