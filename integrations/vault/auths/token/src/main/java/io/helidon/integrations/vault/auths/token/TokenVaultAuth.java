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

package io.helidon.integrations.vault.auths.token;

import java.util.Optional;
import java.util.logging.Logger;

import javax.annotation.Priority;

import io.helidon.config.Config;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.auths.common.VaultRestApi;
import io.helidon.integrations.vault.spi.VaultAuth;

/**
 * Java Service Loader implementation for authenticating using a token.
 * You can create a new instance using {@link #builder()}.
 * To use a custom built instance, use {@link Vault.Builder#addVaultAuth(io.helidon.integrations.vault.spi.VaultAuth)}.
 */
@Priority(5000)
public class TokenVaultAuth implements VaultAuth {
    private static final Logger LOGGER = Logger.getLogger(TokenVaultAuth.class.getName());
    private final String token;
    private final String baseNamespace;

    /**
     * Required for service loader.
     */
    public TokenVaultAuth() {
        this.token = null;
        this.baseNamespace = null;
    }

    private TokenVaultAuth(Builder builder) {
        this.token = builder.token;
        this.baseNamespace = builder.baseNamespace;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Optional<RestApi> authenticate(Config config, Vault.Builder vaultBuilder) {
        boolean enabled = config.get("auth.token.enabled").asBoolean().orElse(true);

        if (!enabled) {
            return Optional.empty();
        }

        return Optional.ofNullable(token)
                .or(vaultBuilder::token)
                .or(() -> config.get("token").asString().asOptional())
                .map(token -> restApi(vaultBuilder, token));
    }

    private RestApi restApi(Vault.Builder vaultBuilder, String token) {
        String address = vaultBuilder.address()
                .orElseThrow(() -> new VaultApiException("Address is required when using token authentication"));

        LOGGER.info("Authenticated Vault " + address + " using a token");

        return VaultRestApi.builder()
                .webClientBuilder(builder -> {
                    builder.config(vaultBuilder.config().get("webclient"))
                            .baseUri(address + "/v1")
                            .addHeader("X-Vault-Token", token);
                    Optional.ofNullable(baseNamespace)
                            .or(vaultBuilder::baseNamespace)
                            .ifPresent(ns -> builder.addHeader("X-Vault-Namespace", ns));
                    vaultBuilder.webClientUpdater().accept(builder);
                })
                .faultTolerance(vaultBuilder.ftHandler())
                .build();
    }

    public static class Builder implements io.helidon.common.Builder<TokenVaultAuth> {
        private String baseNamespace;
        private String token;

        private Builder() {
        }

        @Override
        public TokenVaultAuth build() {
            return new TokenVaultAuth(this);
        }

        public Builder baseNamespace(String baseNamespace) {
            this.baseNamespace = baseNamespace;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }
    }
}
