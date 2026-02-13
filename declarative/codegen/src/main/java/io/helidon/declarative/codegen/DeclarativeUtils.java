/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen;

import java.util.Collection;
import java.util.Optional;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;

/**
 * Utilities of Declarative code generators.
 */
public class DeclarativeUtils {
    private DeclarativeUtils() {
    }

    /**
     * Find an annotation that is of the provided type, even it is a meta annotation of one of the annotations present.
     *
     * @param annotations    set of annotations on the element
     * @param metaAnnotation type of the meta annotation we are looking for
     * @return the meta annotation instance, or empty optional if not found
     */
    public static Optional<Annotation> findMetaAnnotated(Collection<Annotation> annotations,
                                                         TypeName metaAnnotation) {
        for (Annotation annotation : annotations) {
            if (annotation.typeName().equals(metaAnnotation)) {
                return Optional.of(annotation);
            }
            if (annotation.hasMetaAnnotation(metaAnnotation)) {
                return Annotations.findFirst(metaAnnotation, annotation.metaAnnotations());
            }
        }
        return Optional.empty();
    }

    /**
     * Combine paths ensuring a single "/" between the two paths.
     *
     * @param first  fist path, with possible trailing slash
     * @param second second path with possible leading slash
     * @return combined result with slash
     */
    public static String combinePaths(String first, String second) {
        if (first.endsWith("/")) {
            if (second.startsWith("/")) {
                return first + second.substring(1);
            }
            return first + second;
        }
        if (second.startsWith("/")) {
            return first + second;
        }
        return first + "/" + second;
    }
}
