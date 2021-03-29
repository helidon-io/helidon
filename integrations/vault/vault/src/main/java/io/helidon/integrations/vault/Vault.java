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
package io.helidon.integrations.vault;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.faulttolerance.FaultTolerance;
import io.helidon.faulttolerance.FtHandler;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.spi.VaultAuth;
import io.helidon.webclient.WebClient;

/**
 * Main entry point to Vault operations.
 * <p>
 * To access secrets in the vault, start with {@link #builder()} to create a new Vault instance.
 * Once you have a Vault instance, you can access secrets through engines.
 * To get access to secrets, use {@link #secrets(Engine)}.
 */
public interface Vault {
    /**
     * HTTP {@code LIST} method used by several Vault engines.
     */
    Http.RequestMethod LIST = Http.RequestMethod.create("LIST");

    /**
     * Fluent API builder to construct new instances.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create a Vault from configuration.
     *
     * @param config configuration
     * @return a new Vault
     * @see io.helidon.integrations.vault.Vault.Builder#config(io.helidon.config.Config)
     */
    static Vault create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    /**
     * Get access to secrets using the provided engine, using the default mount point of that engine.
     *
     * @param engine engine to use, such as {@code Kv2Secrets#ENGINE}
     * @param <T> type of the {@link io.helidon.integrations.vault.Secrets} the engine supports,
     *           such as {@code io.helidon.integrations.vault.Kv2Secrets}
     * @return instance of {@link io.helidon.integrations.vault.Secrets} specific to the used engine
     */
    <T extends Secrets> T secrets(Engine<T> engine);

    /**
     * Get access to secrets using the provided engine, using a custom mount point.
     *
     * @param engine engine to use, such as {@code Kv2Secrets#ENGINE}
     * @param mount mount point for the engine (such as when the same engine is configured more than once in the Vault)
     * @param <T> type of the {@link Secrets} the engine supports,
     *           such as {@code Kv2Secrets}
     * @return instance of {@link io.helidon.integrations.vault.Secrets} specific to the used engine
     */
    <T extends Secrets> T secrets(Engine<T> engine, String mount);

    /**
     * Get access to authentication method.
     *
     * @param method method to use, such as {@code io.helidon.integrations.vault.AuthMethod.TOKEN}
     * @param <T> type of the API class used by the method
     * @return instance of the API class specific to the used method
     */
    <T> T auth(AuthMethod<T> method);

    /**
     * Get access to authentication method, using a custom path.
     *
     * @param method method to use, such as {@code io.helidon.integrations.vault.AuthMethod.TOKEN}
     * @param path path for the method, such as when configuring multiple instances of the same method
     * @param <T> type of the API class used by the method
     * @return instance of the API class specific to the used method
     */
    <T> T auth(AuthMethod<T> method, String path);

    /**
     * Get access to sys operations on this Vault, such as to configure engines, policies etc. (if such operations are supported).
     *
     * @param api API implementation
     * @param <T> type of the API
     * @return API instance
     */
    <T> T sys(SysApi<T> api);

    /**
     * Fluent API builder for {@link Vault}.
     */
    class Builder implements io.helidon.common.Builder<Vault> {
        private static final Logger LOGGER = Logger.getLogger(Vault.class.getName());

        private final HelidonServiceLoader.Builder<VaultAuth> vaultAuths
                = HelidonServiceLoader.builder(ServiceLoader.load(VaultAuth.class));

        private FtHandler faultTolerance = FaultTolerance.builder().build();
        private Config config = Config.empty();
        private String address;
        private String token;
        private String baseNamespace;
        private Consumer<WebClient.Builder> webClientUpdater = it -> {};

        private Builder() {
        }

        @Override
        public Vault build() {
            List<VaultAuth> auths = vaultAuths.build().asList();

            boolean authenticated = false;

            RestApi restAccess = null;

            for (VaultAuth vaultAuth : auths) {
                Optional<RestApi> authenticate = vaultAuth.authenticate(config, this);
                if (authenticate.isPresent()) {
                    LOGGER.fine("Authenticated Vault " + address + " using " + vaultAuth.getClass().getName());
                    restAccess = authenticate.get();
                    authenticated = true;
                    break;
                }
            }

            if (!authenticated) {
                throw new VaultApiException("No Vault authentication discovered");
            }

            return new VaultImpl(this, restAccess);
        }

        /**
         * Add a {@link io.helidon.integrations.vault.spi.VaultAuth} to use with this Vault.
         * Also all {@link io.helidon.integrations.vault.spi.VaultAuth VaultAuths} discovered by service loader are used.
         *
         * @param vaultAuth vault authentication mechanism to use
         * @return updated builder
         */
        public Builder addVaultAuth(VaultAuth vaultAuth) {
            this.vaultAuths.addService(vaultAuth);
            return this;
        }

        /**
         * Configure address of the Vault, including scheme, host, and port.
         *
         * @param address address of the Vault
         * @return updated builder instance
         */
        public Builder address(String address) {
            this.address = address;
            return this;
        }

        /**
         * Configure token to use to connect to the Vault.
         *
         * @param token token to use
         * @return updated builder instance
         */
        public Builder token(String token) {
            this.token = token;
            return this;
        }

        /**
         * An {@link io.helidon.faulttolerance.FtHandler} can be configured to be used by all calls to the
         * Vault, to add support for retries, circuit breakers, bulkhead etc.
         *
         * @param faultTolerance fault tolerance handler to use
         * @return updated builder instance
         */
        public Builder faultTolerance(FtHandler faultTolerance) {
            this.faultTolerance = faultTolerance;
            return this;
        }

        /**
         * A consumer that updates {@link WebClient.Builder}.
         * The consumer may be invoked multiple times, for example when a Vault authentication
         * must use an un-authenticated Vault to authenticate.
         *
         * @param updater update the web client builder
         * @return updated builder instance
         */
        public Builder updateWebClient(Consumer<WebClient.Builder> updater) {
            this.webClientUpdater = updater;
            return this;
        }

        /**
         * Do not discover {@link io.helidon.integrations.vault.spi.VaultAuth} implementations
         * using a service loader.
         *
         * @return updated builder instance
         */
        public Builder disableVaultAuthDiscovery() {
            vaultAuths.useSystemServiceLoader(false);
            return this;
        }

        /**
         * Update this builder from configuration.
         *
         * @param config configuration to use
         * @return updated builder instance
         */
        public Builder config(Config config) {
            this.config = config;
            config.get("address").asString().ifPresent(this::address);
            config.get("token").asString().ifPresent(this::token);
            config.get("base-namespace").asString().ifPresent(this::baseNamespace);
            return this;
        }

        public Builder baseNamespace(String baseNamespace) {
            this.baseNamespace = baseNamespace;
            return this;
        }

        public Optional<String> address() {
            return Optional.ofNullable(address);
        }

        public Optional<String> token() {
            return Optional.ofNullable(token);
        }

        public Optional<String> baseNamespace() {
            return Optional.ofNullable(baseNamespace);
        }

        public FtHandler ftHandler() {
            return faultTolerance;
        }

        public Config config() {
            return config;
        }

        public Consumer<WebClient.Builder> webClientUpdater() {
            return webClientUpdater;
        }
    }
}
