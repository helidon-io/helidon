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

    /**
     * Interface to enable lambdas as instance constructors.
     *
     * @param <T> type of the constructed instance
     */
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

    /**
     * A single injection type. The provider creates one for each injectable it provides.
     *
     * @param <T> type of the injectable
     */
    class InjectionType<T> {
        private final Class<T> type;
        private final CreateInstanceFunction<T> creator;

        private InjectionType(Class<T> type, CreateInstanceFunction<T> creator) {
            this.type = type;
            this.creator = creator;
        }

        /**
         * Create an injection type for the class and creator function.
         *
         * @param type class of the injectable
         * @param creator function to create a new instance
         * @param <T> type of the injectable
         * @return a new injection type
         */
        public static <T> InjectionType<T> create(Class<T> type, CreateInstanceFunction<T> creator) {
            return new InjectionType<>(type, creator);
        }

        /**
         * Class of the injectable.
         *
         * @return class
         */
        public Class<T> injectedType() {
            return type;
        }

        /**
         * Create a new instance of the injectable.
         *
         * @param vault vault to use
         * @param vaultConfig vault configuration node
         * @param instanceConfig configuration of the instance
         * @return a new injectable instance
         */
        public T createInstance(Vault vault, Config vaultConfig, InstanceConfig instanceConfig) {
            return creator.apply(vault, vaultConfig, instanceConfig);
        }
    }

    /**
     * Configuration of an instance, that can have a named Vault (defined in configuration),
     * and a customized path (such as build-secrets instead of secrets for kv2).
     */
    class InstanceConfig {
        private final String vaultName;
        private final String vaultPath;

        private InstanceConfig(Builder builder) {
            this.vaultName = builder.vaultName;
            this.vaultPath = builder.vaultPath;
        }

        /**
         * A new builder.
         *
         * @return builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Name of the vault to use, empty for default configuration.
         *
         * @return name of vault
         */
        public Optional<String> vaultName() {
            return Optional.ofNullable(vaultName);
        }

        /**
         * Custom path of the component.
         *
         * @return path if defined
         */
        public Optional<String> vaultPath() {
            return Optional.ofNullable(vaultPath);
        }

        /**
         * Fluent API builder for {@link io.helidon.integrations.vault.spi.InjectionProvider.InstanceConfig}.
         */
        public static class Builder implements io.helidon.common.Builder<InstanceConfig> {
            private String vaultPath;
            private String vaultName;

            private Builder() {
            }

            @Override
            public InstanceConfig build() {
                return new InstanceConfig(this);
            }

            /**
             * Configure the custom vault path.
             *
             * @param vaultPath path
             * @return updated builder
             */
            public Builder vaultPath(String vaultPath) {
                this.vaultPath = vaultPath;
                return this;
            }

            /**
             * Configure the vault name.
             *
             * @param vaultName vault name
             * @return updated builder
             */
            public Builder vaultName(String vaultName) {
                this.vaultName = vaultName;
                return this;
            }
        }
    }
}
