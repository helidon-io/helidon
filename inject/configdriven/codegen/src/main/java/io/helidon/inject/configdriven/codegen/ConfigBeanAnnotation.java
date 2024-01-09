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

package io.helidon.inject.configdriven.codegen;

import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

import static io.helidon.inject.codegen.InjectCodegenTypes.CONFIG_META_CONFIGURED;
import static io.helidon.inject.codegen.InjectCodegenTypes.PROTOTYPE_CONFIGURED;

record ConfigBeanAnnotation(String configKey,
                            boolean repeatable,
                            boolean atLeastOne,
                            boolean wantDefault) {
    static final TypeName CONFIG_BEAN_TYPE = TypeName.create("io.helidon.inject.configdriven.service.ConfigDriven.ConfigBean");
    static final TypeName AT_LEAST_ONE_TYPE = TypeName.create("io.helidon.inject.configdriven.service.ConfigDriven.AtLeastOne");
    static final TypeName REPEATABLE_TYPE = TypeName.create("io.helidon.inject.configdriven.service.ConfigDriven.Repeatable");
    static final TypeName WANT_DEFAULT_TYPE = TypeName.create("io.helidon.inject.configdriven.service.ConfigDriven.WantDefault");

    static ConfigBeanAnnotation create(TypeInfo typeInfo) {
        /*
         config key is obtained in this order:
         1. ConfigDriven.ConfigBean annotation
         2. Prototype.Configured annotation
         3. Configured annotation
         */
        String configKey;
        configKey = typeInfo.annotation(CONFIG_BEAN_TYPE)
                .stringValue()
                .orElse("");
        if (configKey.isBlank() && typeInfo.hasAnnotation(PROTOTYPE_CONFIGURED)) {
            configKey = typeInfo.annotation(PROTOTYPE_CONFIGURED)
                    .stringValue()
                    .orElse("");
        }
        if (configKey.isBlank() &&  typeInfo.hasAnnotation(CONFIG_META_CONFIGURED)) {
            configKey = typeInfo.annotation(CONFIG_META_CONFIGURED)
                    .stringValue("prefix")
                    .orElse("");
        }

        return new ConfigBeanAnnotation(
                configKey,
                typeInfo.hasAnnotation(REPEATABLE_TYPE),
                typeInfo.hasAnnotation(AT_LEAST_ONE_TYPE),
                typeInfo.hasAnnotation(WANT_DEFAULT_TYPE)
        );
    }
}
