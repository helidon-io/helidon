/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.service.codegen;

import java.util.Optional;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

import static io.helidon.service.codegen.ServiceCodegenTypes.BUILDER_BLUEPRINT;
import static io.helidon.service.codegen.ServiceCodegenTypes.CONFIG_COMMON_CONFIG;

record ConfigBean(TypeName configBeanType,
                  Optional<TypeName> configBeanBuilderType,
                  ConfigBeanAnnotation annotation) {

    static ConfigBean create(TypeInfo configBeanTypeInfo) {
        /*
        The type info is either:
        - @Prototype.Blueprint that is also annotated with @Prototype.Configured with a root configuration node
        - a type annotated with @ConfigDriven.ConfigBean with a defined root key (not empty string) and

        The type info MUST:
        - have a "static T create(Config config)" to get configured instances
        - if has @AtLeastOne or @AddDefault, then a "static T create()" to get default instances
         */
        ConfigBeanAnnotation configBean = ConfigBeanAnnotation.create(configBeanTypeInfo);

        validate(configBeanTypeInfo, configBean);

        return configBeanType(configBeanTypeInfo, configBean);
    }

    TypeName generatedType() {
        return TypeName.builder()
                .packageName(configBeanType.packageName())
                .className(configBeanType.className() + "__ConfigBean")
                .build();
    }

    TypeName generatedTypeBuilder() {
        return TypeName.builder()
                .packageName(configBeanType.packageName())
                .className(configBeanType.className() + "__ConfigBeanBuilder")
                .build();
    }

    private static void validate(TypeInfo typeInfo, ConfigBeanAnnotation configBean) {
        if (configBean.orDefault()) {
            if (configBean.wantDefault()) {
                throw new CodegenException("Annotation @ConfigDriven.OrDefault cannot be combined with any other ConfigDriven"
                                                   + " annotation, except for @ConfigDriven.ConfigBean,"
                                                   + " but @ConfigDriven.AddDefault is used.",
                                           typeInfo.originatingElementValue());
            }
            if (configBean.atLeastOne()) {
                throw new CodegenException("Annotation @ConfigDriven.OrDefault cannot be combined with any other ConfigDriven"
                                                   + " annotation, except for @ConfigDriven.ConfigBean,"
                                                   + " but @ConfigDriven.AtLeastOne is used.",
                                           typeInfo.originatingElementValue());
            }
            if (configBean.repeatable()) {
                throw new CodegenException("Annotation @ConfigDriven.OrDefault cannot be combined with any other ConfigDriven"
                                                   + " annotation, except for @ConfigDriven.ConfigBean,"
                                                   + " but @ConfigDriven.Repeatable is used.",
                                           typeInfo.originatingElementValue());
            }
        }
    }

    private static ConfigBean configBeanType(TypeInfo configBeanTypeInfo, ConfigBeanAnnotation configBean) {
        // config bean may be the actual prototype, or its blueprint
        if (configBeanTypeInfo.hasAnnotation(BUILDER_BLUEPRINT)) {
            String className = configBeanTypeInfo.typeName().className();
            if (className.endsWith("Blueprint")) {
                className = className.substring(0, className.length() - 9);
                TypeName prototype = TypeName.builder(configBeanTypeInfo.typeName().genericTypeName())
                        .className(className)
                        .build();
                return new ConfigBean(prototype,
                                      Optional.of(TypeName.builder()
                                                          .from(prototype)
                                                          .addEnclosingName(prototype.className())
                                                          .className("Builder")
                                                          .build()),
                                      configBean);
            } else {
                throw new IllegalArgumentException("Type annotation with @Prototype.Blueprint does not"
                                                           + " end with Blueprint: " + configBeanTypeInfo.typeName());
            }
        } else {
            // this type must have a "static Type create(Config config)"
            if (configBeanTypeInfo.elementInfo()
                    .stream()
                    .filter(ElementInfoPredicates::isStatic)
                    .filter(ElementInfoPredicates.elementName("create"))
                    .filter(it -> it.typeName().equals(configBeanTypeInfo.typeName()))
                    .noneMatch(ElementInfoPredicates.hasParams(CONFIG_COMMON_CONFIG))) {
                throw new IllegalArgumentException("ConfigBean type must have a \"static "
                                                           + configBeanTypeInfo.typeName().resolvedName()
                                                           + " create(io.helidon.common.config.Config config)\" method");
            }
            if (configBean.atLeastOne() || configBean.wantDefault()) {
                if (configBeanTypeInfo.elementInfo()
                        .stream()
                        .filter(ElementInfoPredicates::isStatic)
                        .filter(ElementInfoPredicates.elementName("create"))
                        .filter(it -> it.typeName().equals(configBeanTypeInfo.typeName()))
                        .noneMatch(ElementInfoPredicates::hasNoArgs)) {
                    throw new IllegalArgumentException("ConfigBean type must have a \"static "
                                                               + configBeanTypeInfo.typeName().resolvedName()
                                                               + " create()\" method, as it may require a default "
                                                               + "(unconfigured) value.");
                }
            }

            return new ConfigBean(configBeanTypeInfo.typeName(), Optional.empty(), configBean);
        }
    }
}
