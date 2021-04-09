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

package io.helidon.integrations.vault.spi;

import java.util.List;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.integrations.vault.Vault;

/**
 * A Java Service Loader service for locating injectable instances.
 */
public interface InjectionProvider {
    /**
     * List of injectable types supported by this provider.
     *
     * @return list of types
     */
    List<InjectionType<?>> injectables();

    @FunctionalInterface
    interface CreateInstanceFunction<T> {
        /**
         * Create a new instance in singleton scope (or Application for CDI).
         *
         * @param vault to use
         * @param vaultConfig configuration located on the vault node
         * @return a new instance to be injected
         */
        T apply(Vault vault, Config vaultConfig, InstanceConfig instanceConfig);
    }

    class InjectionType<T> {
        private final Class<T> type;
        private final CreateInstanceFunction<T> creator;

        private InjectionType(Class<T> type, CreateInstanceFunction<T> creator) {
            this.type = type;
            this.creator = creator;
        }

        public static <T> InjectionType<T> create(Class<T> type, CreateInstanceFunction<T> creator) {
            return new InjectionType<>(type, creator);
        }

        public Class<T> injectedType() {
            return type;
        }

        public T createInstance(Vault vault, Config vaultConfig, InstanceConfig instanceConfig) {
            return creator.apply(vault, vaultConfig, instanceConfig);
        }
    }

    class InstanceConfig {
        private final String vaultName;
        private final String vaultPath;

        private InstanceConfig(Builder builder) {
            this.vaultName = builder.vaultName;
            this.vaultPath = builder.vaultPath;
        }

        public static Builder builder() {
            return new Builder();
        }

        public Optional<String> vaultName() {
            return Optional.ofNullable(vaultName);
        }

        public Optional<String> vaultPath() {
            return Optional.ofNullable(vaultPath);
        }

        public static class Builder implements io.helidon.common.Builder<InstanceConfig> {
            private String vaultPath;
            private String vaultName;

            private Builder() {
            }

            @Override
            public InstanceConfig build() {
                return new InstanceConfig(this);
            }

            public Builder vaultPath(String vaultPath) {
                this.vaultPath = vaultPath;
                return this;
            }

            public Builder vaultName(String vaultName) {
                this.vaultName = vaultName;
                return this;
            }
        }
    }
}
