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

package io.helidon.integrations.langchain4j.codegen;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

class LlmModelBlueprintBuilder extends IntrospectionBlueprintBuilder {
    private final String configRoot;
    private final String namePrefix;

    LlmModelBlueprintBuilder(RoundContext ctx,
                             TypeInfo lc4jProviderTypeInfo,
                             TypeInfo modelType,
                             TypeInfo modelBuilderType,
                             String providerKey,
                             Annotation modelAnnotation) {
        super(ctx, modelType, lc4jProviderTypeInfo, modelBuilderType);
        this.namePrefix = modelAnnotation.typeValue().map(TypeName::className)
                .orElseThrow(() -> new CodegenException("Missing model class"));

        String modelTypeKey = ModelType.forTypeInfo(modelType).configKey();
        this.configRoot = "langchain4j." + providerKey + "." + modelTypeKey;
        TypeName blueprintTypeName = TypeName.builder()
                .packageName(lc4jProviderTypeInfo.typeName().packageName())
                .className(namePrefix + "ConfigBlueprint")
                .build();

        initClassModel(blueprintTypeName, lc4jProviderTypeInfo.typeName(), Optional.of(lc4jProviderTypeInfo.typeName()));
        addEnableProperty();
    }

    @Override
    protected String configRoot() {
        return configRoot;
    }

    @Override
    protected Map<String, TypedElementInfo> resolveOverriddenProperties() {
        return parentTypeInfo()
                .elementInfo()
                .stream()
                .filter(e -> e.kind().equals(ElementKind.METHOD))
                .filter(e -> e.hasAnnotation(LangchainTypes.OPT_CONFIGURED))
                .collect(Collectors.toMap(info -> info.signature().name(), Function.identity()));
    }

    void addEnableProperty() {
        var methodBuilder = Method.builder()
                .name("enabled")
                .addAnnotation(Annotation.create(LangchainTypes.OPT_CONFIGURED))
                .returnType(TypeNames.PRIMITIVE_BOOLEAN)
                .javadoc(Javadoc.builder()
                                 .add("If set to {@code false} (default), " + this.namePrefix + " will not be "
                                              + "available even if configured.")
                                 .returnDescription("whether " + this.namePrefix + " is enabled, defaults to {@code"
                                                            + " false}")
                                 .build());

        classModelBuilder().addMethod(methodBuilder.build());
    }
}
