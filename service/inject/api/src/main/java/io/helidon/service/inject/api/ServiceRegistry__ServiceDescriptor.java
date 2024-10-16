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

package io.helidon.service.inject.api;

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.service.registry.ServiceRegistry;

/**
 * Service descriptor to enable dependency on {@link InjectRegistry}.
 */
@SuppressWarnings("checkstyle:TypeName") // matches pattern of generated descriptors
public class ServiceRegistry__ServiceDescriptor implements GeneratedInjectService.Descriptor<InjectRegistry> {
    /**
     * Singleton instance to be referenced when building bindings.
     */
    public static final ServiceRegistry__ServiceDescriptor INSTANCE = new ServiceRegistry__ServiceDescriptor();

    private static final TypeName DESCRIPTOR_TYPE = TypeName.create(ServiceRegistry__ServiceDescriptor.class);
    private static final Set<TypeName> CONTRACTS = Set.of(InjectRegistry.TYPE,
                                                          ServiceRegistry.TYPE,
                                                          InjectRegistrySpi.TYPE);

    private ServiceRegistry__ServiceDescriptor() {
    }

    @Override
    public TypeName serviceType() {
        return InjectRegistry.TYPE;
    }

    @Override
    public TypeName descriptorType() {
        return DESCRIPTOR_TYPE;
    }

    @Override
    public Set<TypeName> contracts() {
        return CONTRACTS;
    }

    @Override
    public TypeName scope() {
        return Injection.Singleton.TYPE;
    }
}
