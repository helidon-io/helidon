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

package io.helidon.integrations.vault.secrets.transit;

import io.helidon.config.Config;
import io.helidon.integrations.vault.Vault;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.spi.SecurityProviderService;

/**
 * Service provider for {@link io.helidon.security.spi.SecurityProviderService} for transit secrets.
 */
public class TransitSecurityService implements SecurityProviderService {
    @Override
    public String providerConfigKey() {
        return "vault-transit";
    }

    @Override
    public Class<? extends SecurityProvider> providerClass() {
        return TransitSecurityProvider.class;
    }

    @Override
    public SecurityProvider providerInstance(Config config) {
        return new TransitSecurityProvider(Vault.create(config));
    }
}
