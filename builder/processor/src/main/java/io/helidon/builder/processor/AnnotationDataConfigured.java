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

package io.helidon.builder.processor;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;

import static io.helidon.builder.processor.Types.CONFIGURED_TYPE;
import static io.helidon.builder.processor.Types.PROTOTYPE_CONFIGURED_TYPE;

record AnnotationDataConfigured(boolean configured, String rootPrefix) {
    static AnnotationDataConfigured create(TypeInfo typeInfo) {
        Boolean configured = null;
        Boolean isRoot = null;
        String prefix = null;

        if (typeInfo.hasAnnotation(PROTOTYPE_CONFIGURED_TYPE)) {
            configured = true;
            // if the annotation is present, the value has to be defined (may be empty string)
            prefix = typeInfo.annotation(PROTOTYPE_CONFIGURED_TYPE).stringValue().orElse(null);
            if (prefix != null) {
                isRoot = true;
            }
        }

        if (configured == null) {
            if (typeInfo.hasAnnotation(CONFIGURED_TYPE)) {
                configured = true;
                Annotation annotation = typeInfo.annotation(CONFIGURED_TYPE);
                prefix = annotation.stringValue("prefix").orElse(null);
                isRoot = annotation.booleanValue("root").orElse(false);
            }
        }
        if (isRoot == null) {
            isRoot = false;
        }
        if (prefix == null && isRoot) {
            prefix = "";
        }
        if (!isRoot) {
            prefix = null;
        }
        if (configured == null) {
            configured = false;
        }
        return new AnnotationDataConfigured(configured, prefix);
    }

    boolean isRoot() {
        return rootPrefix != null;
    }
}
