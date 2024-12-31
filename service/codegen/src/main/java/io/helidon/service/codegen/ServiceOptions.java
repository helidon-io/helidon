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

package io.helidon.service.codegen;

import java.io.Serializable;
import java.util.Set;

import io.helidon.codegen.Option;
import io.helidon.common.GenericType;
import io.helidon.common.types.TypeName;

/**
 * Supported options specific to Helidon Service Registry.
 */
public final class ServiceOptions {
    /**
     * Treat all super types as a contract for a given service type being added.
     */
    public static final Option<Boolean> AUTO_ADD_NON_CONTRACT_INTERFACES =
            Option.create("helidon.registry.autoAddNonContractInterfaces",
                          "Treat all super types and implemented types as a contract for a given service type "
                                  + "being added. Defaults to true.",
                          true);
    /**
     * A set of interface/class types that should not be considered contracts, even if implemented by a service.
     */
    public static final Option<Set<TypeName>> NON_CONTRACT_TYPES =
            Option.createSet("helidon.registry.nonContractTypes",
                             "Types that should not be considered contracts. "
                                     + "By default we exclude Serializable.",
                             Set.of(TypeName.create(Serializable.class)),
                             TypeName::create,
                             new GenericType<Set<TypeName>>() { });
    /**
     * Which {@code InterceptionStrategy} to use.
     */
    public static final Option<InterceptionStrategy> INTERCEPTION_STRATEGY =
            Option.create("helidon.registry.interceptionStrategy",
                          "Which interception strategy to use (NONE, EXPLICIT, ALL_RUNTIME, ALL_RETAINED)",
                          InterceptionStrategy.EXPLICIT,
                          InterceptionStrategy::valueOf,
                          GenericType.create(InterceptionStrategy.class));

    /**
     * Additional meta annotations that mark scope annotations. This can be used to include
     * jakarta.enterprise.context.NormalScope annotated types as scopes.
     */
    public static final Option<Set<TypeName>> SCOPE_META_ANNOTATIONS =
            Option.createSet("helidon.registry.scopeMetaAnnotations",
                             "Additional meta annotations that mark scope annotations. This can be used to include"
                                     + "jakarta.enterprise.context.NormalScope annotated types as scopes.",
                             Set.of(),
                             TypeName::create,
                             new GenericType<Set<TypeName>>() { });

    private ServiceOptions() {
    }
}
