/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Weighted;
import io.helidon.common.types.TypeName;

/**
 * Basic service info that describes a service provider type.
 *
 * @see ServiceInfo
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Prototype.Blueprint
@Prototype.CustomMethods(ServiceInfoBasicsBlueprint.CustomMethods.class)
interface ServiceInfoBasicsBlueprint {

    /**
     * Default weight for any <i>internal</i> Injection service component. It is defined to be
     * {@link io.helidon.common.Weighted#DEFAULT_WEIGHT} {@code - 1} in order to allow any other service implementation to
     * naturally have a higher weight (since it will use the {@code DEFAULT_WEIGHT} unless explicitly overridden.
     */
    double DEFAULT_INJECT_WEIGHT = Weighted.DEFAULT_WEIGHT - 1;

    /**
     * The managed service implementation {@link Class}.
     *
     * @return the service type name
     */
    TypeName serviceTypeName();

    /**
     * The managed service assigned Scope's.
     *
     * @return the service scope type name
     */
    @Option.Singular
    Set<TypeName> scopeTypeNames();

    /**
     * The managed service assigned Qualifier's.
     *
     * @return the service qualifiers
     */
    @Option.Singular
    Set<Qualifier> qualifiers();

    /**
     * The managed services advertised types (i.e., typically its interfaces).
     *
     * @see ExternalContracts
     * @return the service contracts implemented
     */
    @Option.Singular("contractImplemented")
    Set<TypeName> contractsImplemented();

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
     * The realized weight will use {@link io.helidon.common.Weighted#DEFAULT_WEIGHT} if no weight was specified directly.
     *
     * @return the realized weight
     * @see #declaredWeight()
     */
    default double realizedWeight() {
        return declaredWeight().orElse(Weighted.DEFAULT_WEIGHT);
    }

    final class CustomMethods {
        private CustomMethods() {
        }

        /**
         * The managed service implementation type name.
         * @param builder the builder instance
         * @param type type of the service
         */
        @Prototype.BuilderMethod
        static void serviceTypeName(ServiceInfoBasics.BuilderBase<?, ?> builder, Class<?> type) {
            builder.serviceTypeName(TypeName.create(type));
        }

        /**
         * Add contract implemented.
         *
         * @param builder the builder instance
         * @param type type of the service
         */
        @Prototype.BuilderMethod
        static void addContractImplemented(ServiceInfoBasics.BuilderBase<?, ?> builder, Class<?> type) {
            builder.addContractImplemented(TypeName.create(type));
        }
    }
}
