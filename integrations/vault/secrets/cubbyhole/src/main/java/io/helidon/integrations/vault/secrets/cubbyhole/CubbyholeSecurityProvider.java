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

package io.helidon.integrations.vault.secrets.cubbyhole;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.VaultOptionalResponse;
import io.helidon.security.spi.ProviderConfig;
import io.helidon.security.spi.SecretsProvider;

/**
 * Integration with Helidon Security.
 */
public class CubbyholeSecurityProvider implements SecretsProvider<CubbyholeSecurityProvider.CubbyholeSecretConfig> {
    private final CubbyholeSecretsRx secrets;

    CubbyholeSecurityProvider(Vault vault) {
        this.secrets = vault.secrets(CubbyholeSecretsRx.ENGINE);
    }

    @Override
    public Supplier<Single<Optional<String>>> secret(Config config) {
        return secret(CubbyholeSecretConfig.create(config));
    }

    @Override
    public Supplier<Single<Optional<String>>> secret(CubbyholeSecretConfig providerConfig) {
        String key = providerConfig.key;

        return () -> secrets.get(providerConfig.request())
                .map(VaultOptionalResponse::entity)
                .map(it -> it.flatMap(response -> response.value(key)));
    }

    /**
     * Configuration of a secret when using programmatic setup of security secrets.
     */
    public static class CubbyholeSecretConfig implements ProviderConfig {
        private final String path;
        private final String key;

        private CubbyholeSecretConfig(Builder builder) {
            this.path = builder.path;
            this.key = builder.key;
        }

        /**
         * A new builder for
         * {@link io.helidon.integrations.vault.secrets.cubbyhole.CubbyholeSecurityProvider.CubbyholeSecretConfig}.
         *
         * @return a new builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Create a new secrets configuration from config.
         *
         * @param config config to use
         * @return a new secret configuration
         */
        public static CubbyholeSecretConfig create(Config config) {
            return builder()
                    .config(config)
                    .build();
        }

        private GetCubbyhole.Request request() {
            return GetCubbyhole.Request.builder()
                    .path(this.path);
        }

        /**
         * Fluent API builder for
         * {@link io.helidon.integrations.vault.secrets.cubbyhole.CubbyholeSecurityProvider.CubbyholeSecretConfig}.
         */
        public static class Builder implements io.helidon.common.Builder<CubbyholeSecretConfig> {
            private String path;
            private String key;

            private Builder() {
            }

            @Override
            public CubbyholeSecretConfig build() {
                Objects.requireNonNull(path, "Secret path must be defined. Config property \"path\"");
                Objects.requireNonNull(key, "Secret value key must be defined. Config property \"key\"");

                return new CubbyholeSecretConfig(this);
            }

            /**
             * Update this builder from configuration.
             * Configuration options:
             * <table class="config">
             * <caption>Secret configuration</caption>
             * <tr>
             *     <th>key</th>
             *     <th>description</th>
             *     <th>builder method</th>
             * </tr>
             * <tr>
             *     <td>path</td>
             *     <td>Path of the secret on Vault's KV2 secret provider</td>
             *     <td>{@link #path(String)}</td>
             * </tr>
             * <tr>
             *     <td>key</td>
             *     <td>Key within the secret used to obtain the value</td>
             *     <td>{@link #key(String)}</td>
             * </tr>
             * </table>
             *
             * @param config config to use
             * @return updated builder
             */
            public Builder config(Config config) {
                config.get("path").asString().ifPresent(this::path);
                config.get("key").asString().ifPresent(this::key);
                return this;
            }

            /**
             * Path of the secret on Vault's Cubbyhole secret provider.
             *
             * @param path secret path
             * @return updated builder
             */
            public Builder path(String path) {
                this.path = path;
                return this;
            }

            /**
             * Key within the secret used to obtain the value.
             *
             * @param key key to use
             * @return updated builder
             */
            public Builder key(String key) {
                this.key = key;
                return this;
            }
        }
    }
}
