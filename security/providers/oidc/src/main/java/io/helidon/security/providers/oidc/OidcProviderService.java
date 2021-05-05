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

package io.helidon.security.providers.oidc;

import io.helidon.config.Config;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.spi.SecurityProviderService;

/**
 * Java service to create OIDC provider instance.
 */
public class OidcProviderService implements SecurityProviderService {
    /**
     * The default configuration key expected under security.providers that would contain
     * configuration of a provider instance.
     */
    public static final String PROVIDER_CONFIG_KEY = "oidc";

    @Override
    public String providerConfigKey() {
        return PROVIDER_CONFIG_KEY;
    }

    @Override
    public Class<? extends SecurityProvider> providerClass() {
        return OidcProvider.class;
    }

    @Override
    public SecurityProvider providerInstance(Config config) {
        return OidcProvider.create(config);
    }
}
