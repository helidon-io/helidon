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

package io.helidon.inject.configdriven.processor;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeValues;

record ConfigBean(TypeName typeName,
                  String configPrefix,
                  ConfigBeanAnnotation annotation) {
    private static final TypeName CONFIGURED = TypeName.create("io.helidon.config.metadata.Configured");
    private static final TypeName CONFIGURED_PROTOTYPE = TypeName.create("io.helidon.builder.api.Prototype.Configured");
    private static final TypeName CONFIG = TypeName.create("io.helidon.common.config.Config");

    public static ConfigBean create(TypeInfo configBeanTypeInfo) {
        TypeName typeName;

        // config bean may be the actual prototype, or its blueprint
        if (configBeanTypeInfo.hasAnnotation(TypeName.create("io.helidon.builder.api.Prototype.Blueprint"))) {
            String className = configBeanTypeInfo.typeName().className();
            if (className.endsWith("Blueprint")) {
                className = className.substring(0, className.length() - 9);
                typeName = TypeName.builder(configBeanTypeInfo.typeName().genericTypeName())
                        .className(className)
                        .build();
            } else {
                throw new IllegalArgumentException("Type annotation with @Prototype.Blueprint does not"
                                                           + " end with Blueprint: " + configBeanTypeInfo.typeName());
            }
        } else {
            typeName = configBeanTypeInfo.typeName();

            // this type must have a "static Type create(Config config)"
            boolean hasConfigFactoryMethod = configBeanTypeInfo.elementInfo()
                    .stream()
                    .filter(it -> it.modifiers().contains(TypeValues.MODIFIER_STATIC))
                    .filter(it -> it.elementName().equals("create"))
                    .filter(it -> it.typeName().equals(configBeanTypeInfo.typeName()))
                    .filter(it -> it.parameterArguments().size() == 1)
                    .anyMatch(it -> it.parameterArguments().get(0).typeName().equals(CONFIG));
            if (!hasConfigFactoryMethod) {
                throw new IllegalArgumentException("ConfigBean type must have a \"static "
                                                           + configBeanTypeInfo.typeName().resolvedName()
                                                           + " create(io.helidon.common.config.Config config)\" method");
            }
        }

        // the type must be annotation with @Configured(root = true, prefix = "something")
        boolean isRoot;
        String configPrefix;
        if (configBeanTypeInfo.hasAnnotation(CONFIGURED)) {
            Annotation configured = configBeanTypeInfo.annotation(CONFIGURED);
            isRoot = configured.booleanValue("root").orElse(false);
            configPrefix = configured.getValue("prefix").orElse("");
        } else if (configBeanTypeInfo.hasAnnotation(CONFIGURED_PROTOTYPE)) {
            Annotation configured = configBeanTypeInfo.annotation(CONFIGURED_PROTOTYPE);
            isRoot = configured.booleanValue("root").orElse(true);
            configPrefix = configured.value().orElse("");
        } else {
            throw new IllegalArgumentException("Blueprint must be annotated with @Configured(root = true)"
                                                       + " to be eligible to be a ConfigBean: " + configBeanTypeInfo.typeName());
        }



        if (!isRoot) {
            throw new IllegalArgumentException("Blueprint must be annotated with @Configured(root = true)"
                                                       + " to be eligible to be a ConfigBean: " + configBeanTypeInfo.typeName());
        }

        return new ConfigBean(typeName, configPrefix, ConfigBeanAnnotation.create(configBeanTypeInfo));
    }
}
