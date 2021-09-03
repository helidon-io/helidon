/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
package io.helidon.security.providers.idcs.mapper;

import io.helidon.config.Config;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.spi.SecurityProviderService;

/**
 * Service for {@link IdcsRoleMapperRxProvider}.
 */
public class IdcsRoleMapperProviderService implements SecurityProviderService {
    @Override
    public String providerConfigKey() {
        return "idcs-role-mapper";
    }

    // This is for backward compatibility only. This will be changed in 3.x
    @Override
    public Class<? extends SecurityProvider> providerClass() {
        return IdcsRoleMapperProvider.class;
    }

    @Override
    public SecurityProvider providerInstance(Config config) {
        if (config.get("multitenant").asBoolean().orElse(true)) {
            return IdcsMtRoleMapperRxProvider.create(config);
        }
        // we now use the new reactive implementation by default
        // the behavior is backward compatible (and configuration as well)
        return IdcsRoleMapperRxProvider.create(config);
    }
}
