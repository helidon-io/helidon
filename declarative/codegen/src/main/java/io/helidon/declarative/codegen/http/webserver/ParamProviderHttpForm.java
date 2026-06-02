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
import io.helidon.declarative.codegen.http.webserver.spi.HttpParameterCodegenProvider;
import io.helidon.service.codegen.DefaultsCodegen;
import io.helidon.service.codegen.DefaultsParams;

import static io.helidon.declarative.codegen.http.HttpTypes.HTTP_FORM_PARAM_ANNOTATION;

class ParamProviderHttpForm extends AbstractParametersProvider implements HttpParameterCodegenProvider {
    private static final TypeName LAZY_VALUE = TypeName.create("io.helidon.common.LazyValue");
    private static final TypeName PARAMETERS = TypeName.create("io.helidon.common.parameters.Parameters");

    static final String FORM_PARAMS = "declarative__formParams";
    static final String FORM_PARAMS_ACCESSOR = FORM_PARAMS + ".get()";
    static final String FORM_PARAMS_COMPONENT = "form-params";
    static final TypeName LAZY_FORM_PARAMS = TypeName.builder(LAZY_VALUE)
            .addTypeArgument(PARAMETERS)
            .build();

    @Override
    public boolean codegen(ParameterCodegenContext ctx) {
        Optional<Annotation> first = ctx.annotations().stream()
                .filter(it -> HTTP_FORM_PARAM_ANNOTATION.equals(it.typeName()))
                .findFirst();

        if (first.isEmpty()) {
            return false;
        }

        Annotation formParam = first.get();
        String formParamName = formParam.value()
                .orElseThrow(() -> new CodegenException("@FormParam annotation must have a value."));

        TypeName parameterType = ctx.parameterType();
        TypeName realType = parameterType.isOptional() ? parameterType.typeArguments().getFirst() : parameterType;
        Optional<DefaultsCodegen.DefaultCode> defaultCode = DefaultsCodegen.findDefault(ctx.annotations(), realType);
        ContentBuilder<?> contentBuilder = ctx.contentBuilder();

        codegenFromParameters(ctx,
                              parameterType,
                              formParamName,
                              parameterType.isOptional() || defaultCode.isPresent(),
                              new ParametersSource(FORM_PARAMS_ACCESSOR,
                                                   true,
                                                   FORM_PARAMS_COMPONENT));

        if (defaultCode.isPresent()) {
            var defaultInfo = defaultCode.get();
            if (defaultInfo.requiresMapper()) {
                ensureMapperField(ctx.fieldHandler());
            }

            var params = DefaultsParams.builder()
                    .contextField(FORM_PARAMS_ACCESSOR)
                    .mapperQualifier(FORM_PARAMS_COMPONENT)
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
        return "Form parameter";
    }
}
