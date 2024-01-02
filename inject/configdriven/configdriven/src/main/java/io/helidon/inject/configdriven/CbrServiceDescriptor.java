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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.config.Config;
import io.helidon.common.types.TypeName;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.ServiceDescriptor;

/**
 * A descriptor for config driven registry. Must be public, as other services that inject to it may need to
 * use this type.
 * For most services, such a type is code generated.
 */
public class CbrServiceDescriptor implements ServiceDescriptor<ConfigBeanRegistryImpl> {
    /**
     * Singleton instance bound to binder.
     */
    public static final CbrServiceDescriptor INSTANCE = new CbrServiceDescriptor();
    static final String CBR_RUNTIME_ID = "CBR_CONFIG_DRIVEN";
    private static final TypeName TYPE = TypeName.create(ConfigBeanRegistryImpl.class);
    private static final TypeName CBR_TYPE = TypeName.create(ConfigBeanRegistry.class);
    private static final TypeName INFO_TYPE = TypeName.create(CbrServiceDescriptor.class);
    private static final Set<TypeName> CONTRACTS = Set.of(CBR_TYPE);
    private static final TypeName CONFIG_TYPE = TypeName.create(Config.class);

    /**
     * Injection point describing required {@link io.helidon.common.config.Config} instance.
     */
    public static final Ip CONFIG_IP = Ip.builder()
            .typeName(TypeName.create(Optional.class.getName() + "<" + CONFIG_TYPE.fqName() + ">"))
            .name("config")
            .service(TYPE)
            .descriptor(INFO_TYPE)
            .field("CONFIG_IP")
            .contract(CONFIG_TYPE)
            .build();
    private static final List<Ip> DEPENDENCIES = List.of(CONFIG_IP);

    @Override
    public String runtimeId() {
        return CBR_RUNTIME_ID;
    }

    @Override
    public TypeName serviceType() {
        return TYPE;
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
    public Set<TypeName> scopes() {
        return Set.of(Injection.Singleton.TYPE_NAME);
    }

    @Override
    public List<Ip> dependencies() {
        return DEPENDENCIES;
    }
}
