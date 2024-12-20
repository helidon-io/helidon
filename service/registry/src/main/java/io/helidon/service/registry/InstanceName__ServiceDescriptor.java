/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.registry;

import java.util.Set;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

/**
 * Service descriptor to enable injection of String name of a {@link Service.PerInstance}
 * service (using qualifier {@link Service.InstanceName}).
 * <p>
 * Not intended for direct use by users, implementation detail of the service registry, must be public,
 * as it may be used in generated binding.
 */
@SuppressWarnings({"checkstyle:TypeName"}) // matches pattern of generated descriptors
public class InstanceName__ServiceDescriptor implements ServiceDescriptor<String> {
    /**
     * Singleton instance to be referenced when building bindings.
     */
    public static final InstanceName__ServiceDescriptor INSTANCE = new InstanceName__ServiceDescriptor();

    private static final TypeName INFO_TYPE = TypeName.create(InstanceName__ServiceDescriptor.class);
    private static final Set<ResolvedType> CONTRACTS = Set.of(ResolvedType.create(TypeNames.STRING));

    private InstanceName__ServiceDescriptor() {
    }

    @Override
    public TypeName serviceType() {
        return INFO_TYPE;
    }

    @Override
    public TypeName descriptorType() {
        return INFO_TYPE;
    }

    @Override
    public Set<ResolvedType> contracts() {
        return CONTRACTS;
    }

    @Override
    public FactoryType factoryType() {
        return FactoryType.NONE;
    }
}
