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

package io.helidon.inject.api;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.types.Annotation;

/**
 * Utility methods for qualifiers.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public final class Qualifiers {
    private Qualifiers() {
    }

    /**
     * Matches qualifier collections.
     *
     * @param src      the target service info to evaluate
     * @param criteria the criteria to compare against
     * @return true if the criteria provided matches this instance
     */
    public static boolean matchesQualifiers(Collection<Qualifier> src,
                                            Collection<Qualifier> criteria) {
        if (criteria.isEmpty()) {
            return true;
        }

        if (src.isEmpty()) {
            return false;
        }

        if (src.contains(CommonQualifiers.WILDCARD_NAMED)) {
            return true;
        }

        for (Qualifier criteriaQualifier : criteria) {
            if (src.contains(criteriaQualifier)) {
                // NOP;
                continue;
            } else if (criteriaQualifier.typeName().equals(CommonQualifiers.NAMED)) {
                if (criteriaQualifier.equals(CommonQualifiers.WILDCARD_NAMED)
                        || criteriaQualifier.value().isEmpty()) {
                    // any Named qualifier will match ...
                    boolean hasSameTypeAsCriteria = src.stream()
                            .anyMatch(q -> q.typeName().equals(criteriaQualifier.typeName()));
                    if (hasSameTypeAsCriteria) {
                        continue;
                    }
                } else if (src.contains(CommonQualifiers.WILDCARD_NAMED)) {
                    continue;
                }
                return false;
            } else if (criteriaQualifier.value().isEmpty()) {
                Set<Annotation> sameTypeAsCriteriaSet = src.stream()
                        .filter(q -> q.typeName().equals(criteriaQualifier.typeName()))
                        .collect(Collectors.toSet());
                if (sameTypeAsCriteriaSet.isEmpty()) {
                    return false;
                }
            } else {
                return false;
            }
        }

        return true;
    }
}
