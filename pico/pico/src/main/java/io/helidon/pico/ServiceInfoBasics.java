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

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.Weighted;

import jakarta.inject.Singleton;

/**
 * Basic service info that describes a service provider type.
 *
 * @see ServiceInfo
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
        return Set.of();
    }

    /**
     * The managed services advertised types (i.e., typically its interfaces).
     *
     * @see io.helidon.pico.ExternalContracts
     * @return the service contracts implemented
     */
    default Set<String> contractsImplemented() {
        return Set.of();
    }

    /**
     * The optional {@link RunLevel} ascribed to the service.
     *
     * @return the service's run level
     */
    default int runLevel() {
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

}
