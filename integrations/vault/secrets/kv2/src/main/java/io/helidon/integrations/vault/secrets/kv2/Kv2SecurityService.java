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

package io.helidon.integrations.vault.secrets.kv2;

import io.helidon.config.Config;
import io.helidon.integrations.vault.Vault;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.spi.SecurityProviderService;

/**
 * Service loader service implementation for {@link io.helidon.security.spi.SecurityProviderService}.
 */
public class Kv2SecurityService implements SecurityProviderService {
    /**
     * @deprecated Do not use this constructor, this is a service loader service!
     */
    @Deprecated
    public Kv2SecurityService() {
    }

    @Override
    public String providerConfigKey() {
        return "vault-kv2";
    }

    @Override
    public Class<? extends SecurityProvider> providerClass() {
        return Kv2SecurityProvider.class;
    }

    @Override
    public SecurityProvider providerInstance(Config config) {
        return new Kv2SecurityProvider(Vault.create(config));
    }
}
