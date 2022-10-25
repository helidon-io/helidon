/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.spi;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.Weighted;
import io.helidon.pico.api.RunLevel;

import jakarta.inject.Singleton;

/**
 * Basic service info that describes a service provider type.
 */
public interface ServiceInfoBasics extends Weighted {

    /**
     * The managed service implementation {@link Class}.
     *
     * @return the service type name
     */
    String serviceTypeName();

    /**
     * The managed service assigned Scope's.
     *
     * @return the service scope type name
     */
    default Set<String> scopeTypeNames() {
        return Collections.singleton(Singleton.class.getName());
    }

    /**
     * The managed service assigned Qualifier's.
     *
     * @return the service qualifiers
     */
    default Set<QualifierAndValue> qualifiers() {
        return Collections.emptySet();
    }

    /**
     * The managed services "announced" interfaces - aka {@link io.helidon.pico.api.Contract}'s.
     *
     * @return the service contracts implemented
     */
    default Set<String> contractsImplemented() {
        return Collections.emptySet();
    }

    /**
     * The optional {@link io.helidon.pico.api.RunLevel} ascribed to the service.
     *
     * @return the service's run level
     */
    default Integer runLevel() {
        return RunLevel.NORMAL;
    }

    /**
     * Weight that was declared on the type itself.
     *
     * @return the declared weight
     * @see #realizedWeight()
     */
    default Optional<Double> declaredWeight() {
        return Optional.of(weight());
    }

    /**
     * The realized weight will use the default weight if no weight was specified directly.
     *
     * @return the realized weight
     * @see #weight()
     */
    default double realizedWeight() {
        return declaredWeight().orElse(weight());
    }

    /**
     * Determines whether this matches the given contract.
     *
     * @param contract the contract
     * @return true if the service type name or the set of contracts implemented equals the provided contract
     */
    default boolean matchesContract(String contract) {
        return (Objects.equals(serviceTypeName(), contract) || contractsImplemented().contains(contract));
    }

    /**
     * Determines whether this matches the given contract.
     *
     * @param contract the contract
     * @return true if the service type name or the set of contracts implemented equals the provided contract
     */
    default boolean matchesContract(Class<?> contract) {
        if (matchesContract(contract.getName())) {
            return true;
        }

        Class<?>[] supers = contract.getInterfaces();
        for (Class<?> iface : supers) {
            if (matchesContract(iface)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if the criteria provided is non-null or there are referenced service types or contracts.
     *
     * @param serviceInfo the service info to inspect
     * @return true if contracts specified
     */
    static boolean hasContracts(ServiceInfoBasics serviceInfo) {
        if (Objects.isNull(serviceInfo)) {
            return false;
        }

        return (!serviceInfo.contractsImplemented().isEmpty() || Objects.nonNull(serviceInfo.serviceTypeName()));
    }

    /**
     * Returns true if the criteria provided is blank/empty.
     *
     * @param serviceInfo the service info to inspect
     * @return true if no contracts specified
     */
    static boolean isBlank(ServiceInfoBasics serviceInfo) {
        if (Objects.isNull(serviceInfo)) {
            return true;
        }

        return (!hasContracts(serviceInfo) && serviceInfo.qualifiers().isEmpty());
    }

}
