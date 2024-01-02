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

package io.helidon.inject.configdriven;

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.ServiceInfo;

/**
 * Service descriptor for {@link ConfigBeanRegistry}.
 */
public final class ConfigBeanRegistryDescriptor implements ServiceInfo {
    /**
     * Singleton instance of this descriptor.
     */
    public static final ConfigBeanRegistryDescriptor INSTANCE = new ConfigBeanRegistryDescriptor();

    private static final TypeName TYPE = TypeName.create(ConfigBeanRegistryDescriptor.class);
    private static final TypeName CBR_IMPL_TYPE = TypeName.create(ConfigBeanRegistryImpl.class);
    private static final TypeName CBR_TYPE = TypeName.create(ConfigBeanRegistry.class);
    private static final Set<TypeName> CONTRACTS = Set.of(CBR_TYPE);

    private ConfigBeanRegistryDescriptor() {
    }

    @Override
    public TypeName serviceType() {
        return CBR_IMPL_TYPE;
    }

    @Override
    public TypeName infoType() {
        return TYPE;
    }

    @Override
    public Set<TypeName> contracts() {
        return CONTRACTS;
    }

    @Override
    public Set<TypeName> scopes() {
        return Set.of(Injection.Singleton.TYPE_NAME);
    }
}
