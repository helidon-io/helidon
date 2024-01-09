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

package io.helidon.inject;

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.ServiceDescriptor;

/**
 * Service descriptor to enable injection of {@link io.helidon.inject.Services}.
 * Injection of services enables a possibility to have multiple Services instances within a single JVM,
 * as it allows a service to inject the instance it is managed by (for example to do programmatic lookups),
 * rather than depending on the singleton static instance.
 * @deprecated this is an internal type of the service registry, equivalent to code generated descriptors; these
 * types must be public, so we can generate {@link io.helidon.inject.Application}, yet should not be used directly
 * from user code
 */
@Deprecated
@SuppressWarnings({"checkstyle:TypeName", "DeprecatedIsStillUsed"}) // matches pattern of generated descriptors
public class Services__ServiceDescriptor implements ServiceDescriptor<Services> {
    /**
     * Singleton instance to be referenced when building applications.
     */
    public static final Services__ServiceDescriptor INSTANCE = new Services__ServiceDescriptor();

    private static final TypeName SERVICES = TypeName.create(Services.class);
    private static final TypeName INFO_TYPE = TypeName.create(Services__ServiceDescriptor.class);
    private static final Set<TypeName> CONTRACTS = Set.of(Services.TYPE_NAME);

    private Services__ServiceDescriptor() {
    }

    @Override
    public TypeName serviceType() {
        return SERVICES;
    }

    @Override
    public TypeName infoType() {
        return INFO_TYPE;
    }

    @Override
    public Set<TypeName> contracts() {
        return CONTRACTS;
    }

    @Override
    public TypeName scope() {
        return Injection.Singleton.TYPE_NAME;
    }
}
