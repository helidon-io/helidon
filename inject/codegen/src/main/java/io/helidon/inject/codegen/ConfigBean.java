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

package io.helidon.inject.codegen;

import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

import static io.helidon.inject.codegen.InjectCodegenTypes.COMMON_CONFIG;
import static io.helidon.inject.codegen.InjectCodegenTypes.PROTOTYPE_BLUEPRINT;

record ConfigBean(TypeName configBeanType,
                  ConfigBeanAnnotation annotation) {

    static ConfigBean create(TypeInfo configBeanTypeInfo) {
        /*
        The type info is either:
        - @Prototype.Blueprint that is also annotated with @Prototype.Configured with a root configuration node
        - a type annotated with @ConfigDriven.ConfigBean with a defined root key (not empty string) and

        The type info MUST:
        - have a "static T create(Config config)" to get configured instances
        - if has @AtLeastOne or @WantDefault, then a "static T create()" to get default instances
         */
        ConfigBeanAnnotation configBean = ConfigBeanAnnotation.create(configBeanTypeInfo);

        TypeName typeName = configBeanType(configBeanTypeInfo, configBean);

        return new ConfigBean(typeName, configBean);
    }

    TypeName generatedType() {;
        return TypeName.builder()
                .packageName(configBeanType.packageName())
                .className(configBeanType.className() + "__ConfigBean")
                .build();
    }

    private static TypeName configBeanType(TypeInfo configBeanTypeInfo, ConfigBeanAnnotation configBean) {
        // config bean may be the actual prototype, or its blueprint
        if (configBeanTypeInfo.hasAnnotation(PROTOTYPE_BLUEPRINT)) {
            String className = configBeanTypeInfo.typeName().className();
            if (className.endsWith("Blueprint")) {
                className = className.substring(0, className.length() - 9);
                return TypeName.builder(configBeanTypeInfo.typeName().genericTypeName())
                        .className(className)
                        .build();
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
                    .noneMatch(ElementInfoPredicates.hasParams(COMMON_CONFIG))) {
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

            return configBeanTypeInfo.typeName();
        }
    }
}
