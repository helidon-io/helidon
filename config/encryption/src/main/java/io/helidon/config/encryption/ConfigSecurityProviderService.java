/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.config.encryption;

import javax.annotation.Priority;

import io.helidon.config.Config;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.spi.SecurityProviderService;

/**
 * Java Service Loader implementation of a {@link io.helidon.security.Security} provider service.
 * Do not instantiate directly.
 */
@Priority(5000)
public class ConfigSecurityProviderService implements SecurityProviderService {
    /**
     * @deprecated do not use, this should only be invoked by Java Service Loader
     * @see io.helidon.config.encryption.ConfigSecurityProvider
     */
    @Deprecated
    public ConfigSecurityProviderService() {
    }

    @Override
    public String providerConfigKey() {
        return "config-vault";
    }

    @Override
    public Class<? extends SecurityProvider> providerClass() {
        return ConfigSecurityProvider.class;
    }

    @Override
    public SecurityProvider providerInstance(Config config) {
        return ConfigSecurityProvider.create(config);
    }
}
