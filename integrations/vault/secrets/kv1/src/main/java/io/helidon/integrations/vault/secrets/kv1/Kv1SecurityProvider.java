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

package io.helidon.integrations.vault.secrets.kv1;

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
public class Kv1SecurityProvider implements SecretsProvider<Kv1SecurityProvider.Kv1SecretConfig> {
    private final Kv1SecretsRx secrets;

    Kv1SecurityProvider(Vault vault) {
        this.secrets = vault.secrets(Kv1SecretsRx.ENGINE);
    }

    @Override
    public Supplier<Single<Optional<String>>> secret(Config config) {
        return secret(Kv1SecretConfig.create(config));
    }

    @Override
    public Supplier<Single<Optional<String>>> secret(Kv1SecretConfig providerConfig) {
        String key = providerConfig.key;

        return () -> secrets.get(providerConfig.request())
                .map(VaultOptionalResponse::entity)
                .map(it -> it.flatMap(response -> response.value(key)));
    }

    public static class Kv1SecretConfig implements ProviderConfig {
        private final String path;
        private final String key;

        private Kv1SecretConfig(Builder builder) {
            this.path = builder.path;
            this.key = builder.key;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static Kv1SecretConfig create(Config config) {
            return builder()
                    .config(config)
                    .build();
        }

        private GetKv1.Request request() {
            return GetKv1.Request.builder()
                    .path(this.path);
        }

        public static class Builder implements io.helidon.common.Builder<Kv1SecretConfig> {
            private String path;
            private String key;

            private Builder() {
            }

            @Override
            public Kv1SecretConfig build() {
                Objects.requireNonNull(path, "Secret path must be defined. Config property \"path\"");
                Objects.requireNonNull(key, "Secret value key must be defined. Config property \"key\"");

                return new Kv1SecretConfig(this);
            }

            public Builder config(Config config) {
                config.get("path").asString().ifPresent(this::path);
                config.get("key").asString().ifPresent(this::key);
                return this;
            }

            public Builder path(String path) {
                this.path = path;
                return this;
            }

            public Builder key(String key) {
                this.key = key;
                return this;
            }
        }
    }
}
