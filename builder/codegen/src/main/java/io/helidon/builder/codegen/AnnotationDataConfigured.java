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

package io.helidon.builder.codegen;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;

record AnnotationDataConfigured(boolean configured, String rootPrefix, boolean isRoot) {
    static AnnotationDataConfigured create(TypeInfo typeInfo) {
        boolean configured = false;
        boolean isRoot = false;
        String prefix = null;

        if (typeInfo.hasAnnotation(Types.PROTOTYPE_CONFIGURED)) {
            configured = true;

            Annotation annotation = typeInfo.annotation(Types.PROTOTYPE_CONFIGURED);
            // if the annotation is present, the value has to be defined (may be empty string)
            prefix = annotation.stringValue().orElse(null);
            if (prefix != null) {
                isRoot = annotation.booleanValue("root").orElse(true);
            }
        }

        return new AnnotationDataConfigured(configured, prefix, isRoot);
    }
}
