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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.codegen.RoundContext;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.MODEL_CONFIGS_TYPE;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.MODEL_CONFIG_TYPE;
import static java.util.function.Predicate.not;

/**
 * The {@code ModelConfigCodegen} class is responsible for generating
 * configuration blueprints for lc4j models by introspecting their builders.
 */
class ModelConfigCodegen implements CodegenExtension {
    static final TypeName GENERATOR = TypeName.create(ModelConfigCodegen.class);
    static final Set<TypeName> GENERATED_CLASSES = new HashSet<>();

    @Override
    public void process(RoundContext roundContext) {
        var types = roundContext.types();
        for (TypeInfo type : types) {
            type.findAnnotation(MODEL_CONFIGS_TYPE)
                    .flatMap(a -> a.annotationValues())
                    .or(() -> type.findAnnotation(MODEL_CONFIG_TYPE).map(List::of))
                    .stream()
                    .flatMap(Collection::stream)
                    .forEach(modelAnnotation -> process(roundContext, type, modelAnnotation));
        }
    }

    private void process(RoundContext ctx,
                         TypeInfo type,
                         Annotation modelAnnotation) {

        var modelType = modelAnnotation.typeValue().flatMap(ctx::typeInfo).orElseThrow();
        var modelBuilderType = resolveModelBuilderType(ctx, modelAnnotation);
        List<String> skips = modelAnnotation.stringValues("skip").orElseThrow();
        Set<String> nestedTypes = modelAnnotation.stringValues("nestedTypes").stream().flatMap(Collection::stream)
                .collect(Collectors.toSet());

        var providerKey = modelAnnotation.stringValue("providerKey")
                .filter(s -> !s.isEmpty())
                .or(() -> Optional.of(ModelCodegenHelper.providerConfigKeyFromClassName(type)))
                .filter(s -> !s.isEmpty())
                .orElseThrow();

        var blueprintBuilder = new LlmModelBlueprintBuilder(ctx, type, modelType, modelBuilderType, providerKey, modelAnnotation);
        blueprintBuilder.introspectBuilder(skips, nestedTypes);
        blueprintBuilder.buildAndAdd();
    }

    TypeInfo resolveModelBuilderType(RoundContext roundContext, Annotation modelConfigAnnotation) {
        // Explicitly set builder has priority
        return modelConfigAnnotation.typeValue("builder")
                .filter(not(TypeNames.BOXED_VOID::equals))
                // Or find the return type of builder() method
                .or(() -> modelConfigAnnotation.typeValue()
                        .flatMap(roundContext::typeInfo)
                        .stream()
                        .flatMap(ti -> ti.elementInfo().stream())
                        .filter(m -> m.elementName().equals("builder"))
                        .filter(m -> m.parameterArguments().isEmpty())
                        .map(m -> m.typeName())
                        .findFirst()
                )
                .flatMap(roundContext::typeInfo)
                .orElseThrow();
    }
}
