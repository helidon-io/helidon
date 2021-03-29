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
 * Use this class only with Helidon Security on the classpath.
 */
public class Kv2SecurityProvider implements SecretsProvider<Kv2SecurityProvider.Kv2SecretConfig> {
    private final Kv2Secrets secrets;

    Kv2SecurityProvider(Vault vault) {
        this.secrets = vault.secrets(Kv2Secrets.ENGINE);
    }

    @Override
    public Supplier<Single<Optional<String>>> secret(Config config) {
        return secret(Kv2SecretConfig.create(config));
    }

    @Override
    public Supplier<Single<Optional<String>>> secret(Kv2SecretConfig providerConfig) {
        String key = providerConfig.key;

        return () -> secrets.get(providerConfig.request())
                .map(VaultOptionalResponse::entity)
                .map(it -> it.flatMap(response -> response.value(key)));
    }

    public static class Kv2SecretConfig implements ProviderConfig {
        private final String path;
        private final String key;
        private final Optional<Integer> version;

        private Kv2SecretConfig(Builder builder) {
            this.path = builder.path;
            this.key = builder.key;
            this.version = Optional.ofNullable(builder.version);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static Kv2SecretConfig create(Config config) {
            return builder()
                    .config(config)
                    .build();
        }

        private GetKv2.Request request() {
            GetKv2.Request request = GetKv2.Request.builder()
                    .path(this.path);

            version.ifPresent(request::version);

            return request;
        }

        public static class Builder implements io.helidon.common.Builder<Kv2SecretConfig> {
            private String path;
            private String key;
            private Integer version;

            private Builder() {
            }

            @Override
            public Kv2SecretConfig build() {
                Objects.requireNonNull(path, "Secret path must be defined. Config property \"path\"");
                Objects.requireNonNull(key, "Secret value key must be defined. Config property \"key\"");

                return new Kv2SecretConfig(this);
            }

            public Builder config(Config config) {
                config.get("path").asString().ifPresent(this::path);
                config.get("key").asString().ifPresent(this::key);
                config.get("version").asInt().ifPresent(this::version);
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

            public Builder version(Integer version) {
                this.version = version;
                return this;
            }
        }
    }
}
