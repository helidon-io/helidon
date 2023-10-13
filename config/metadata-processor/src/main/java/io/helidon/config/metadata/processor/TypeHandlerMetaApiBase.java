/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.config.metadata.processor;

import java.util.List;
import java.util.function.BiFunction;

import javax.annotation.processing.ProcessingEnvironment;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.config.metadata.processor.UsedTypes.META_OPTION;
import static io.helidon.config.metadata.processor.UsedTypes.META_OPTIONS;

// base for types using config metadata annotations
abstract class TypeHandlerMetaApiBase extends TypeHandlerBase {
    TypeHandlerMetaApiBase(ProcessingEnvironment aptEnv) {
        super(aptEnv);
    }

    List<ConfiguredOptionData> findConfiguredOptionAnnotations(TypedElementInfo elementInfo) {
        if (elementInfo.hasAnnotation(META_OPTIONS)) {
            Annotation metaOptions = elementInfo.annotation(META_OPTIONS);
            return metaOptions.annotationValues()
                    .stream()
                    .flatMap(List::stream)
                    .map(it -> ConfiguredOptionData.createMeta(aptEnv(), it))
                    .toList();
        }

        if (elementInfo.hasAnnotation(META_OPTION)) {
            Annotation metaOption = elementInfo.annotation(META_OPTION);
            return List.of(ConfiguredOptionData.createMeta(aptEnv(), metaOption));
        }

        return List.of();
    }

    void processBuilderMethod(TypeName typeName,
                              ConfiguredType configuredType,
                              TypedElementInfo elementInfo,
                              BiFunction<TypedElementInfo, ConfiguredOptionData, OptionType> optionTypeMethod,
                              BiFunction<TypedElementInfo, OptionType, List<TypeName>> builderParamsMethod) {
        List<ConfiguredOptionData> options = findConfiguredOptionAnnotations(elementInfo);
        if (options.isEmpty()) {
            return;
        }

        for (ConfiguredOptionData data : options) {
            if (!data.configured()) {
                continue;
            }
            String name = key(elementInfo, data);
            String description = description(elementInfo, data);
            String defaultValue = defaultValue(data.defaultValue());
            boolean experimental = data.experimental();
            OptionType type = optionTypeMethod.apply(elementInfo, data);
            boolean optional = defaultValue != null || data.optional();
            boolean deprecated = data.deprecated();
            List<ConfiguredOptionData.AllowedValue> allowedValues = allowedValues(data, type.elementType());

            List<TypeName> paramTypes = builderParamsMethod.apply(elementInfo, type);

            ConfiguredType.ProducerMethod builderMethod = new ConfiguredType.ProducerMethod(false,
                                                                                            typeName,
                                                                                            elementInfo.elementName(),
                                                                                            paramTypes);

            ConfiguredType.ConfiguredProperty property = new ConfiguredType.ConfiguredProperty(builderMethod.toString(),
                                                                                               name,
                                                                                               description,
                                                                                               defaultValue,
                                                                                               type.elementType(),
                                                                                               experimental,
                                                                                               optional,
                                                                                               type.kind(),
                                                                                               data.provider(),
                                                                                               data.providerType(),
                                                                                               deprecated,
                                                                                               data.merge(),
                                                                                               allowedValues);
            configuredType.addProperty(property);
        }
    }

    static final class OptionType {
        private final TypeName elementType;
        private final String kind;

        OptionType(TypeName elementType, String kind) {
            this.elementType = elementType;
            this.kind = kind;
        }

        TypeName elementType() {
            return elementType;
        }

        String kind() {
            return kind;
        }
    }
}
