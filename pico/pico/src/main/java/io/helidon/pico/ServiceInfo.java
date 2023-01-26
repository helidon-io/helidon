/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.builder.Builder;
import io.helidon.builder.Singular;
import io.helidon.pico.types.AnnotationAndValue;

/**
 * Describes a managed service or injection point.
 *
 * @see Services
 * @see ServiceInfoCriteria
 */
@Builder(interceptor = ServiceInfoBuildInterceptor.class)
public interface ServiceInfo extends ServiceInfoBasics {

    /**
     * The managed services external contracts / interfaces. These should also be contained within
     * {@link #contractsImplemented()}. External contracts are from other modules other than the module containing
     * the implementation typically.
     *
     * @see io.helidon.pico.ExternalContracts
     * @return the service external contracts implemented
     */
    @Singular
    Set<String> externalContractsImplemented();

    /**
     * The management agent (i.e., the activator) that is responsible for creating and activating - typically build-time created.
     *
     * @return the activator type name
     */
    Optional<String> activatorTypeName();

    /**
     * The name of the ascribed module, if known.
     *
     * @return the module name
     */
    Optional<String> moduleName();

    /**
     * Determines whether this service info matches the criteria for injection.
     * Matches is a looser form of equality check than {@code equals()}. If a service matches criteria
     * it is generally assumed to be viable for assignability.
     *
     * @param criteria the criteria to compare against
     * @return true if the criteria provided matches this instance
     */
    // internal note: it is unfortunate that we have a matches() here as well as in ServiceInfo. This is what happened
    // when we split ServiceInfo into ServiceInfoCriteria.  Sometimes we need ServiceInfo.matches(criteria), and other times
    // ServiceInfoCriteria.matches(criteria).
    default boolean matches(
            ServiceInfoCriteria criteria) {
        if (criteria == PicoServices.EMPTY_CRITERIA) {
            return true;
        }

        boolean matches = matches(serviceTypeName(), criteria.serviceTypeName());
        if (matches && criteria.serviceTypeName().isEmpty()) {
            matches = contractsImplemented().containsAll(criteria.contractsImplemented())
                    || criteria.contractsImplemented().contains(serviceTypeName());
        }
        return matches
                && scopeTypeNames().containsAll(criteria.scopeTypeNames())
                && matchesQualifiers(qualifiers(), criteria.qualifiers())
                && matches(activatorTypeName(), criteria.activatorTypeName())
                && matchesWeight(this, criteria)
                && matches(realizedRunLevel(), criteria.runLevel())
                && matches(moduleName(), criteria.moduleName());
    }

    /**
     * Matches qualifier collections.
     *
     * @param src      the target service info to evaluate
     * @param criteria the criteria to compare against
     * @return true if the criteria provided matches this instance
     */
    static boolean matchesQualifiers(
            Collection<QualifierAndValue> src,
            Collection<QualifierAndValue> criteria) {
        if (criteria.isEmpty()) {
            return true;
        }

        if (src.isEmpty()) {
            return false;
        }

        if (src.contains(DefaultQualifierAndValue.WILDCARD_NAMED)) {
            return true;
        }

        for (QualifierAndValue criteriaQualifier : criteria) {
            if (src.contains(criteriaQualifier)) {
                // NOP;
                continue;
            } else if (criteriaQualifier.typeName().equals(DefaultQualifierAndValue.NAMED)) {
                if (criteriaQualifier.equals(DefaultQualifierAndValue.WILDCARD_NAMED)
                        || criteriaQualifier.value().isEmpty()) {
                    // any Named qualifier will match ...
                    boolean hasSameTypeAsCriteria = src.stream()
                            .anyMatch(q -> q.typeName().equals(criteriaQualifier.typeName()));
                    if (hasSameTypeAsCriteria) {
                        continue;
                    }
                } else if (src.contains(DefaultQualifierAndValue.WILDCARD_NAMED)) {
                    continue;
                }
                return false;
            } else if (criteriaQualifier.value().isEmpty()) {
                Set<AnnotationAndValue> sameTypeAsCriteriaSet = src.stream()
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

    private static boolean matches(
            Object src,
            Optional<?> criteria) {
        if (criteria.isEmpty()) {
            return true;
        }

        return Objects.equals(src, criteria.get());
    }

    /**
     * Weight matching is always less or equal to criteria specified.
     *
     * @param src      the item being considered
     * @param criteria the criteria
     * @return true if there is a match
     */
    private static boolean matchesWeight(
            ServiceInfoBasics src,
            ServiceInfoCriteria criteria) {
        if (criteria.weight().isEmpty()) {
            return true;
        }

        Double srcWeight = src.realizedWeight();
        return (srcWeight.compareTo(criteria.weight().get()) <= 0);
    }

    /**
     * Creates a builder from a {@link io.helidon.pico.ServiceInfoBasics} instance.
     *
     * @param val the instance to copy
     * @return the fluent builder
     */
    // note to self: the builders framework should probably code-generate this automatically
    static DefaultServiceInfo.Builder toBuilder(
            ServiceInfoBasics val) {
        if (val instanceof ServiceInfo) {
            return DefaultServiceInfo.toBuilder((ServiceInfo) val);
        }

        DefaultServiceInfo.Builder result = DefaultServiceInfo.builder();
        result.serviceTypeName(val.serviceTypeName());
        result.scopeTypeNames(val.scopeTypeNames());
        result.qualifiers(val.qualifiers());
        result.contractsImplemented(val.contractsImplemented());
        result.declaredRunLevel(val.declaredRunLevel());
        result.declaredWeight(val.declaredWeight());

        return result;
    }

}
