/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.security.providers.google.login;

import io.helidon.common.config.Config;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.spi.SecurityProviderService;

/**
 * Java service ({@link SecurityProviderService}) for google token provider.
 */
public class GoogleTokenService implements SecurityProviderService {

    static final String CONFIG_PROVIDER_KEY = "google-login";

    @Override
    public String providerConfigKey() {
        return CONFIG_PROVIDER_KEY;
    }

    @Override
    public Class<? extends SecurityProvider> providerClass() {
        return GoogleTokenProvider.class;
    }

    @Override
    public SecurityProvider providerInstance(Config config) {
        return GoogleTokenProvider.create(config);
    }
}
