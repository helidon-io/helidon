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

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

record ConfigDrivenAnnotation(TypeName configBeanType,
                              boolean activateByDefault) {
    static final String TYPE_NAME = "io.helidon.inject.configdriven.service.ConfigDriven";
    static final TypeName TYPE = TypeName.create(TYPE_NAME);

    static ConfigDrivenAnnotation create(TypeInfo typeInfo) {
        // this must be available, as it drives our annotation processor
        Annotation annotation = typeInfo.annotation(TYPE);

        // value is mandatory on the annotation
        return new ConfigDrivenAnnotation(annotation.typeValue().orElseThrow(),
                                          annotation.booleanValue("activateByDefault")
                                                  .orElse(false));
    }
}
