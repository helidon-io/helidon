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

package io.helidon.integrations.vault.auths.common;

import java.util.Optional;

import javax.annotation.Priority;

import io.helidon.config.Config;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.spi.VaultAuth;

/**
 * Java Service Loader implementation for creating an unauthenticated Vault instance.
 */
@Priority(10000)
public class NoVaultAuth implements VaultAuth {
    /**
     * Required for service loader.
     */
    public NoVaultAuth() {
    }

    /**
     * Create a new instance.
     * @return a new unauthenticated Vault authentication
     */
    public static NoVaultAuth create() {
        return new NoVaultAuth();
    }

    @Override
    public Optional<RestApi> authenticate(Config config, Vault.Builder vaultBuilder) {
        boolean enabled = config.get("noauth.enabled").asBoolean().orElse(true);

        if (!enabled) {
            return Optional.empty();
        }

        String address = vaultBuilder.address().orElseThrow(() -> new VaultApiException("Address must be defined"));

        return Optional.of(VaultRestApi.builder()
                                   .webClientBuilder(webclient -> {
                                       webclient.baseUri(address + "/v1");
                                       vaultBuilder.baseNamespace()
                                               .ifPresent(ns -> webclient.addHeader("X-Vault-Namespace", ns));
                                       vaultBuilder.webClientUpdater().accept(webclient);
                                   })
                                   .faultTolerance(vaultBuilder.ftHandler())
                                   .build());
    }
}
