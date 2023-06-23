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

package io.helidon.pico.api;

import java.util.Optional;
import java.util.Set;

import io.helidon.builder.Builder;
import io.helidon.builder.Singular;
import io.helidon.common.Weighted;

/**
 * Basic service info that describes a service provider type.
 *
 * @see ServiceInfo
 */
@Builder(allowPublicOptionals = true)
public interface ServiceInfoBasics {

    /**
     * Default weight for any <i>internal</i> Pico service component. It is defined to be
     * {@link Weighted#DEFAULT_WEIGHT} {@code - 1} in order to allow any other service implementation to
     * naturally have a higher weight (since it will use the {@code DEFAULT_WEIGHT} unless explicitly overridden.
     */
    double DEFAULT_PICO_WEIGHT = Weighted.DEFAULT_WEIGHT - 1;

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
    @Singular
    Set<String> scopeTypeNames();

    /**
     * The managed service assigned Qualifier's.
     *
     * @return the service qualifiers
     */
    @Singular
    Set<QualifierAndValue> qualifiers();

    /**
     * The managed services advertised types (i.e., typically its interfaces).
     *
     * @see io.helidon.pico.api.ExternalContracts
     * @return the service contracts implemented
     */
    @Singular
    Set<String> contractsImplemented();

    /**
     * The optional {@link RunLevel} ascribed to the service.
     *
     * @return the service's run level
     * @see #realizedRunLevel()
     */
    Optional<Integer> declaredRunLevel();

    /**
     * The realized run level will use the default run level if no run level was specified directly.
     *
     * @return the realized run level
     * @see #declaredRunLevel()
     */
    default int realizedRunLevel() {
        return declaredRunLevel().orElse(RunLevel.NORMAL);
    }

    /**
     * Weight that was declared on the type itself.
     *
     * @return the declared weight
     * @see #realizedWeight()
     */
    Optional<Double> declaredWeight();

    /**
     * The realized weight will use {@link Weighted#DEFAULT_WEIGHT} if no weight was specified directly.
     *
     * @return the realized weight
     * @see #declaredWeight()
     */
    default double realizedWeight() {
        return declaredWeight().orElse(Weighted.DEFAULT_WEIGHT);
    }

}
