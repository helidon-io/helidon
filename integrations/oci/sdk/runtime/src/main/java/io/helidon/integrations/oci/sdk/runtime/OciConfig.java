/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.sdk.runtime;

import java.util.Objects;
import java.util.Optional;

import io.helidon.config.Config;

/**
 * Configuration used by {@link OciAuthenticationDetailsProvider}.
 *
 * @see OciExtension
 *
 * @see #builder()
 * @see #create()
 */
public interface OciConfig extends OciConfigBlueprint {
    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static Builder builder(OciConfig instance) {
        return OciConfig.builder().from(instance);
    }

    /**
     * Create a new instance from configuration.
     *
     * @param config used to configure the new instance
     * @return a new instance configured from configuration
     */
    static OciConfig create(Config config) {
        return OciConfig.builder().config(config).buildPrototype();
    }

    /**
     * Create a new instance with default values.
     *
     * @return a new instance
     */
    static OciConfig create() {
         return OciConfig.builder().buildPrototype();
    }

    /**
     * Fluent API builder base for {@link OciConfig}.
     *
     * @param <BUILDER> type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends OciConfig>
            implements io.helidon.common.Builder<BUILDER, PROTOTYPE> {
        private Config config;
        private String authStrategy;
        private final java.util.List<String> authStrategies = new java.util.ArrayList<>();
        private String configPath;
        private String configProfile = "DEFAULT";
        private String authFingerprint;
        private String authKeyFile = "oci_api_key.pem";
        private String authPrivateKeyPath;
        private char[] authPrivateKey;
        private char[] authPassphrase;
        private String authRegion;
        private String authTenantId;
        private String authUserId;
        private String imdsHostName = "169.254.169.254";
        private java.time.Duration imdsTimeout = java.time.Duration.parse("PT0.1S");

        /**
         * Protected to support extensibility.
         *
         */
        protected BuilderBase() {
        }

        /**
         * Update this builder from an existing prototype instance.
         *
         * @param prototype existing prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(OciConfig prototype) {
            authStrategy(prototype.authStrategy());
            addAuthStrategies(prototype.authStrategies());
            configPath(prototype.configPath());
            configProfile(prototype.configProfile());
            authFingerprint(prototype.authFingerprint());
            authKeyFile(prototype.authKeyFile());
            authPrivateKeyPath(prototype.authPrivateKeyPath());
            authPrivateKey(prototype.authPrivateKey());
            authPassphrase(prototype.authPassphrase());
            authRegion(prototype.authRegion());
            authTenantId(prototype.authTenantId());
            authUserId(prototype.authUserId());
            imdsHostName(prototype.imdsHostName());
            imdsTimeout(prototype.imdsTimeout());
            return identity();
        }
        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(BuilderBase<?, ?> builder) {
            builder.authStrategy().ifPresent(this::authStrategy);
            addAuthStrategies(builder.authStrategies());
            builder.configPath().ifPresent(this::configPath);
            builder.configProfile().ifPresent(this::configProfile);
            builder.authFingerprint().ifPresent(this::authFingerprint);
            authKeyFile(builder.authKeyFile());
            builder.authPrivateKeyPath().ifPresent(this::authPrivateKeyPath);
            builder.authPrivateKey().ifPresent(this::authPrivateKey);
            builder.authPassphrase().ifPresent(this::authPassphrase);
            builder.authRegion().ifPresent(this::authRegion);
            builder.authTenantId().ifPresent(this::authTenantId);
            builder.authUserId().ifPresent(this::authUserId);
            imdsHostName(builder.imdsHostName());
            imdsTimeout(builder.imdsTimeout());
            return identity();
        }
        /**
         * Handles providers and decorators.
         */
        protected void preBuildPrototype() {
        }
        /**
         * Validates required properties.
         */
        protected void validatePrototype() {
        }
        /**
         * Update builder from configuration (node of this type).
         * If a value is present in configuration, it would override currently configured values.
         *
         * @param config configuration instance used to obtain values to update this builder
         * @return updated builder instance
         */
//        @Override
        public BUILDER config(Config config) {
            Objects.requireNonNull(config);
            this.config = config;
            config.get("auth-strategy").as(String.class).ifPresent(this::authStrategy);
            config.get("auth-strategies").asList(String.class).ifPresent(this::authStrategies);
            config.get("config.path").as(String.class).ifPresent(this::configPath);
            config.get("config.profile").as(String.class).ifPresent(this::configProfile);
            config.get("auth.fingerprint").as(String.class).ifPresent(this::authFingerprint);
            config.get("auth.keyFile").as(String.class).ifPresent(this::authKeyFile);
            config.get("auth.private-key-path").as(String.class).ifPresent(this::authPrivateKeyPath);
            config.get("auth.private-key").asString().map(String::toCharArray).ifPresent(this::authPrivateKey);
            config.get("auth.passphrase").asString().map(String::toCharArray).ifPresent(this::authPassphrase);
            config.get("auth.region").as(String.class).ifPresent(this::authRegion);
            config.get("auth.tenant-id").as(String.class).ifPresent(this::authTenantId);
            config.get("auth.user-id").as(String.class).ifPresent(this::authUserId);
            config.get("imds.hostname").as(String.class).ifPresent(this::imdsHostName);
            config.get("imds.timeout.milliseconds").as(java.time.Duration.class).ifPresent(this::imdsTimeout);
            return identity();
        }
        /**
         * The singular authentication strategy to apply. This will be preferred over {@link #authStrategies()} if both are
         * present.
         *
         * @param authStrategy the singular authentication strategy to be applied
         * @return updated builder instance
         * @see #authStrategy()
         */
        BUILDER authStrategy(Optional<? extends String> authStrategy) {
            Objects.requireNonNull(authStrategy);
            this.authStrategy = authStrategy.orElse(null);
            return identity();
        }
        /**
         * Clear existing value of this property.
         * @return updated builder instance
         * @see #authStrategy()
         */
        public  BUILDER clearAuthStrategy() {
            this.authStrategy = null;
            return identity();
        }
        /**
         * The singular authentication strategy to apply. This will be preferred over {@link #authStrategies()} if both are
         * present.
         *
         * @param authStrategy the singular authentication strategy to be applied
         * @return updated builder instance
         * @see #authStrategy()
         */
        public BUILDER authStrategy(String authStrategy) {
            Objects.requireNonNull(authStrategy);
            this.authStrategy = authStrategy;
            return identity();
        }
        /**
         * The list of authentication strategies that will be attempted by
         * {@link com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider} when one is
         * called for. This is only used if {@link #authStrategy()} is not present.
         *
         * <ul>
         * <li>{@code auto} - if present in the list, or if no value
         * for this property exists, the behavior will be as if {@code
         * config,config-file,instance-principals,resource-principal}
         * were supplied instead.</li>
         * <li>{@code config} - the
         * {@link com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider}
         * will be used, customized with other configuration
         * properties described here.</li>
         * <li>{@code config-file} - the
         * {@link com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider}
         * will be used, customized with other configuration
         * properties described here.</li>
         * <li>{@code instance-principals} - the
         * {@link com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider}
         * will be used.</li>
         * <li>{@code resource-principal} - the
         * {@link com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider}
         * will be used.</li>
         * </ul>
         * <p>
         * If there are many strategy descriptors supplied, the
         * first one that is deemed to be available or suitable will
         * be used and all others will be ignored.
         *
         * @param authStrategies the list of authentication strategies that will be applied, defaulting to {@code auto}
         * @return updated builder instance
         * @see #authStrategies()
         */
        public BUILDER authStrategies(java.util.List<? extends String> authStrategies) {
            Objects.requireNonNull(authStrategies);
            this.authStrategies.clear();
            this.authStrategies.addAll(authStrategies);
            return identity();
        }
        /**
         * The list of authentication strategies that will be attempted by
         * {@link com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider} when one is
         * called for. This is only used if {@link #authStrategy()} is not present.
         *
         * <ul>
         * <li>{@code auto} - if present in the list, or if no value
         * for this property exists, the behavior will be as if {@code
         * config,config-file,instance-principals,resource-principal}
         * were supplied instead.</li>
         * <li>{@code config} - the
         * {@link com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider}
         * will be used, customized with other configuration
         * properties described here.</li>
         * <li>{@code config-file} - the
         * {@link com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider}
         * will be used, customized with other configuration
         * properties described here.</li>
         * <li>{@code instance-principals} - the
         * {@link com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider}
         * will be used.</li>
         * <li>{@code resource-principal} - the
         * {@link com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider}
         * will be used.</li>
         * </ul>
         * <p>
         * If there are many strategy descriptors supplied, the
         * first one that is deemed to be available or suitable will
         * be used and all others will be ignored.
         *
         * @param authStrategies the list of authentication strategies that will be applied, defaulting to {@code auto}
         * @return updated builder instance
         * @see #authStrategies()
         */
        public BUILDER addAuthStrategies(java.util.List<? extends String> authStrategies) {
            Objects.requireNonNull(authStrategies);
            this.authStrategies.addAll(authStrategies);
            return identity();
        }
        /**
         * The OCI configuration profile path.
         * <p>
         * This configuration property has an effect only when {@code config-file} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #fileConfigIsPresent()}.
         * When it is present, this property must also be present and then the
         * {@linkplain com.oracle.bmc.ConfigFileReader#parse(String)}
         * method will be passed this value. It is expected to be passed with a
         * valid OCI configuration file path.
         *
         * @param configPath the OCI configuration profile path
         * @return updated builder instance
         * @see #configPath()
         */
        BUILDER configPath(Optional<? extends String> configPath) {
            Objects.requireNonNull(configPath);
            this.configPath = configPath.orElse(null);
            return identity();
        }
        /**
         * Clear existing value of this property.
         * @return updated builder instance
         * @see #configPath()
         */
        public  BUILDER clearConfigPath() {
            this.configPath = null;
            return identity();
        }
        /**
         * The OCI configuration profile path.
         * <p>
         * This configuration property has an effect only when {@code config-file} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #fileConfigIsPresent()}.
         * When it is present, this property must also be present and then the
         * {@linkplain com.oracle.bmc.ConfigFileReader#parse(String)}
         * method will be passed this value. It is expected to be passed with a
         * valid OCI configuration file path.
         *
         * @param configPath the OCI configuration profile path
         * @return updated builder instance
         * @see #configPath()
         */
        public BUILDER configPath(String configPath) {
            Objects.requireNonNull(configPath);
            this.configPath = configPath;
            return identity();
        }
        /**
         * The OCI configuration/auth profile name.
         * <p>
         * This configuration property has an effect only when {@code config-file} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #fileConfigIsPresent()}.
         * When it is present, this property may also be optionally provided in order to override the default
         * {@value #DEFAULT_PROFILE_NAME}.
         *
         * @param configProfile the optional OCI configuration/auth profile name
         * @return updated builder instance
         * @see #configProfile()
         */
        BUILDER configProfile(Optional<? extends String> configProfile) {
            Objects.requireNonNull(configProfile);
            this.configProfile = configProfile.orElse(null);
            return identity();
        }
        /**
         * Clear existing value of this property.
         * @return updated builder instance
         * @see #configProfile()
         */
        public  BUILDER clearConfigProfile() {
            this.configProfile = null;
            return identity();
        }
        /**
         * The OCI configuration/auth profile name.
         * <p>
         * This configuration property has an effect only when {@code config-file} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #fileConfigIsPresent()}.
         * When it is present, this property may also be optionally provided in order to override the default
         * {@value #DEFAULT_PROFILE_NAME}.
         *
         * @param configProfile the optional OCI configuration/auth profile name
         * @return updated builder instance
         * @see #configProfile()
         */
        public BUILDER configProfile(String configProfile) {
            Objects.requireNonNull(configProfile);
            this.configProfile = configProfile;
            return identity();
        }
        /**
         * The OCI authentication fingerprint.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the <a
         * href="https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm">API signing key's fingerprint</a>.
         * See {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getFingerprint()} for more details.
         *
         * @param authFingerprint the OCI authentication fingerprint
         * @return updated builder instance
         * @see #authFingerprint()
         */
        BUILDER authFingerprint(Optional<? extends String> authFingerprint) {
            Objects.requireNonNull(authFingerprint);
            this.authFingerprint = authFingerprint.orElse(null);
            return identity();
        }
        /**
         * Clear existing value of this property.
         * @return updated builder instance
         * @see #authFingerprint()
         */
        public  BUILDER clearAuthFingerprint() {
            this.authFingerprint = null;
            return identity();
        }
        /**
         * The OCI authentication fingerprint.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the <a
         * href="https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm">API signing key's fingerprint</a>.
         * See {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getFingerprint()} for more details.
         *
         * @param authFingerprint the OCI authentication fingerprint
         * @return updated builder instance
         * @see #authFingerprint()
         */
        public BUILDER authFingerprint(String authFingerprint) {
            Objects.requireNonNull(authFingerprint);
            this.authFingerprint = authFingerprint;
            return identity();
        }
        /**
         * The OCI authentication key file.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPrivateKey()}. This file must exist in the
         * {@code user.home} directory. Alternatively, this property can be set using either {@link #authPrivateKey()} or
         * using {@link #authPrivateKeyPath()}.
         *
         * @param authKeyFile the OCI authentication key file
         * @return updated builder instance
         * @see #authKeyFile()
         */
        public BUILDER authKeyFile(String authKeyFile) {
            Objects.requireNonNull(authKeyFile);
            this.authKeyFile = authKeyFile;
            return identity();
        }
        /**
         * The OCI authentication key file path.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPrivateKey()}. This file path is
         * an alternative for using {@link #authKeyFile()} where the file must exist in the {@code user.home} directory.
         * Alternatively, this property can be set using {@link #authPrivateKey()}.
         *
         * @param authPrivateKeyPath the OCI authentication key file path
         * @return updated builder instance
         * @see #authPrivateKeyPath()
         */
        BUILDER authPrivateKeyPath(Optional<? extends String> authPrivateKeyPath) {
            Objects.requireNonNull(authPrivateKeyPath);
            this.authPrivateKeyPath = authPrivateKeyPath.orElse(null);
            return identity();
        }
        /**
         * Clear existing value of this property.
         * @return updated builder instance
         * @see #authPrivateKeyPath()
         */
        public  BUILDER clearAuthPrivateKeyPath() {
            this.authPrivateKeyPath = null;
            return identity();
        }
        /**
         * The OCI authentication key file path.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPrivateKey()}. This file path is
         * an alternative for using {@link #authKeyFile()} where the file must exist in the {@code user.home} directory.
         * Alternatively, this property can be set using {@link #authPrivateKey()}.
         *
         * @param authPrivateKeyPath the OCI authentication key file path
         * @return updated builder instance
         * @see #authPrivateKeyPath()
         */
        public BUILDER authPrivateKeyPath(String authPrivateKeyPath) {
            Objects.requireNonNull(authPrivateKeyPath);
            this.authPrivateKeyPath = authPrivateKeyPath;
            return identity();
        }
        /**
         * The OCI authentication private key.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPrivateKey()}. Alternatively, this property
         * can be set using either {@link #authKeyFile()} residing in the {@code user.home} directory, or using
         * {@link #authPrivateKeyPath()}.
         *
         * @param authPrivateKey the OCI authentication private key
         * @return updated builder instance
         * @see #authPrivateKey()
         */
        BUILDER authPrivateKey(Optional<? extends char[]> authPrivateKey) {
            Objects.requireNonNull(authPrivateKey);
            this.authPrivateKey = authPrivateKey.orElse(null);
            return identity();
        }
        /**
         * Clear existing value of this property.
         * @return updated builder instance
         * @see #authPrivateKey()
         */
        public  BUILDER clearAuthPrivateKey() {
            this.authPrivateKey = null;
            return identity();
        }
        /**
         * The OCI authentication private key.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPrivateKey()}. Alternatively, this property
         * can be set using either {@link #authKeyFile()} residing in the {@code user.home} directory, or using
         * {@link #authPrivateKeyPath()}.
         *
         * @param authPrivateKey the OCI authentication private key
         * @return updated builder instance
         * @see #authPrivateKey()
         */
        public BUILDER authPrivateKey(char[] authPrivateKey) {
            Objects.requireNonNull(authPrivateKey);
            this.authPrivateKey = authPrivateKey;
            return identity();
        }
        /**
         * The OCI authentication private key.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPrivateKey()}. Alternatively, this property
         * can be set using either {@link #authKeyFile()} residing in the {@code user.home} directory, or using
         * {@link #authPrivateKeyPath()}.
         *
         * @param authPrivateKey the OCI authentication private key
         * @return updated builder instance
         * @see #authPrivateKey()
         */
        public BUILDER authPrivateKey(String authPrivateKey) {
            Objects.requireNonNull(authPrivateKey);
            this.authPrivateKey = authPrivateKey.toCharArray();
            return identity();
        }
        /**
         * The OCI authentication passphrase.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPassphraseCharacters()}.
         *
         * @param authPassphrase the OCI authentication passphrase
         * @return updated builder instance
         * @see #authPassphrase()
         */
        BUILDER authPassphrase(Optional<? extends char[]> authPassphrase) {
            Objects.requireNonNull(authPassphrase);
            this.authPassphrase = authPassphrase.orElse(null);
            return identity();
        }
        /**
         * Clear existing value of this property.
         * @return updated builder instance
         * @see #authPassphrase()
         */
        public  BUILDER clearAuthPassphrase() {
            this.authPassphrase = null;
            return identity();
        }
        /**
         * The OCI authentication passphrase.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPassphraseCharacters()}.
         *
         * @param authPassphrase the OCI authentication passphrase
         * @return updated builder instance
         * @see #authPassphrase()
         */
        public BUILDER authPassphrase(char[] authPassphrase) {
            Objects.requireNonNull(authPassphrase);
            this.authPassphrase = authPassphrase;
            return identity();
        }
        /**
         * The OCI authentication passphrase.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPassphraseCharacters()}.
         *
         * @param authPassphrase the OCI authentication passphrase
         * @return updated builder instance
         * @see #authPassphrase()
         */
        public BUILDER authPassphrase(String authPassphrase) {
            Objects.requireNonNull(authPassphrase);
            this.authPassphrase = authPassphrase.toCharArray();
            return identity();
        }
        /**
         * The OCI region.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, either this property or {@link com.oracle.bmc.auth.RegionProvider} must be provide a value in order
         * to set the {@linkplain com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider#getRegion()}.
         *
         * @param authRegion the OCI region
         * @return updated builder instance
         * @see #authRegion()
         */
        BUILDER authRegion(Optional<? extends String> authRegion) {
            Objects.requireNonNull(authRegion);
            this.authRegion = authRegion.orElse(null);
            return identity();
        }
        /**
         * Clear existing value of this property.
         * @return updated builder instance
         * @see #authRegion()
         */
        public  BUILDER clearAuthRegion() {
            this.authRegion = null;
            return identity();
        }
        /**
         * The OCI region.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, either this property or {@link com.oracle.bmc.auth.RegionProvider} must be provide a value in order
         * to set the {@linkplain com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider#getRegion()}.
         *
         * @param authRegion the OCI region
         * @return updated builder instance
         * @see #authRegion()
         */
        public BUILDER authRegion(String authRegion) {
            Objects.requireNonNull(authRegion);
            this.authRegion = authRegion;
            return identity();
        }
        /**
         * The OCI tenant id.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider#getTenantId()}.
         *
         * @param authTenantId the OCI tenant id
         * @return updated builder instance
         * @see #authTenantId()
         */
        BUILDER authTenantId(Optional<? extends String> authTenantId) {
            Objects.requireNonNull(authTenantId);
            this.authTenantId = authTenantId.orElse(null);
            return identity();
        }
        /**
         * Clear existing value of this property.
         * @return updated builder instance
         * @see #authTenantId()
         */
        public  BUILDER clearAuthTenantId() {
            this.authTenantId = null;
            return identity();
        }
        /**
         * The OCI tenant id.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider#getTenantId()}.
         *
         * @param authTenantId the OCI tenant id
         * @return updated builder instance
         * @see #authTenantId()
         */
        public BUILDER authTenantId(String authTenantId) {
            Objects.requireNonNull(authTenantId);
            this.authTenantId = authTenantId;
            return identity();
        }
        /**
         * The OCI user id.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider#getUserId()}.
         *
         * @param authUserId the OCI user id
         * @return updated builder instance
         * @see #authUserId()
         */
        BUILDER authUserId(Optional<? extends String> authUserId) {
            Objects.requireNonNull(authUserId);
            this.authUserId = authUserId.orElse(null);
            return identity();
        }
        /**
         * Clear existing value of this property.
         * @return updated builder instance
         * @see #authUserId()
         */
        public  BUILDER clearAuthUserId() {
            this.authUserId = null;
            return identity();
        }
        /**
         * The OCI user id.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider#getUserId()}.
         *
         * @param authUserId the OCI user id
         * @return updated builder instance
         * @see #authUserId()
         */
        public BUILDER authUserId(String authUserId) {
            Objects.requireNonNull(authUserId);
            this.authUserId = authUserId;
            return identity();
        }
        /**
         * The OCI IMDS hostname.
         * <p>
         * This configuration property is used to identify the metadata service url.
         *
         * @param imdsHostName the OCI IMDS hostname
         * @return updated builder instance
         * @see #imdsHostName()
         */
        public BUILDER imdsHostName(String imdsHostName) {
            Objects.requireNonNull(imdsHostName);
            this.imdsHostName = imdsHostName;
            return identity();
        }
        /**
         * The OCI IMDS connection timeout. This is used to auto-detect availability.
         * <p>
         * This configuration property is used when attempting to connect to the metadata service.
         *
         * @param imdsTimeout the OCI IMDS connection timeout
         * @return updated builder instance
         * @see #imdsTimeout()
         */
        public BUILDER imdsTimeout(java.time.Duration imdsTimeout) {
            Objects.requireNonNull(imdsTimeout);
            this.imdsTimeout = imdsTimeout;
            return identity();
        }
        /**
         * The singular authentication strategy to apply. This will be preferred over {@link #authStrategies()} if both are
         * present.
         *
         * @return the auth strategy
         */
        public Optional<String> authStrategy() {
            return Optional.ofNullable(authStrategy);
        }
        /**
         * The list of authentication strategies that will be attempted by
         * {@link com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider} when one is
         * called for. This is only used if {@link #authStrategy()} is not present.
         *
         * <ul>
         * <li>{@code auto} - if present in the list, or if no value
         * for this property exists, the behavior will be as if {@code
         * config,config-file,instance-principals,resource-principal}
         * were supplied instead.</li>
         * <li>{@code config} - the
         * {@link com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider}
         * will be used, customized with other configuration
         * properties described here.</li>
         * <li>{@code config-file} - the
         * {@link com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider}
         * will be used, customized with other configuration
         * properties described here.</li>
         * <li>{@code instance-principals} - the
         * {@link com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider}
         * will be used.</li>
         * <li>{@code resource-principal} - the
         * {@link com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider}
         * will be used.</li>
         * </ul>
         * <p>
         * If there are many strategy descriptors supplied, the
         * first one that is deemed to be available or suitable will
         * be used and all others will be ignored.
         *
         * @return the auth strategies
         */
        public java.util.List<String> authStrategies() {
            return authStrategies;
        }
        /**
         * The OCI configuration profile path.
         * <p>
         * This configuration property has an effect only when {@code config-file} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #fileConfigIsPresent()}.
         * When it is present, this property must also be present and then the
         * {@linkplain com.oracle.bmc.ConfigFileReader#parse(String)}
         * method will be passed this value. It is expected to be passed with a
         * valid OCI configuration file path.
         *
         * @return the config path
         */
        public Optional<String> configPath() {
            return Optional.ofNullable(configPath);
        }
        /**
         * The OCI configuration/auth profile name.
         * <p>
         * This configuration property has an effect only when {@code config-file} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #fileConfigIsPresent()}.
         * When it is present, this property may also be optionally provided in order to override the default
         * {@value #DEFAULT_PROFILE_NAME}.
         *
         * @return the config profile
         */
        public Optional<String> configProfile() {
            return Optional.ofNullable(configProfile);
        }
        /**
         * The OCI authentication fingerprint.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the <a
         * href="https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm">API signing key's fingerprint</a>.
         * See {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getFingerprint()} for more details.
         *
         * @return the auth fingerprint
         */
        public Optional<String> authFingerprint() {
            return Optional.ofNullable(authFingerprint);
        }
        /**
         * The OCI authentication key file.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPrivateKey()}. This file must exist in the
         * {@code user.home} directory. Alternatively, this property can be set using either {@link #authPrivateKey()} or
         * using {@link #authPrivateKeyPath()}.
         *
         * @return the auth key file
         */
        public String authKeyFile() {
            return authKeyFile;
        }
        /**
         * The OCI authentication key file path.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPrivateKey()}. This file path is
         * an alternative for using {@link #authKeyFile()} where the file must exist in the {@code user.home} directory.
         * Alternatively, this property can be set using {@link #authPrivateKey()}.
         *
         * @return the auth private key path
         */
        public Optional<String> authPrivateKeyPath() {
            return Optional.ofNullable(authPrivateKeyPath);
        }
        /**
         * The OCI authentication private key.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPrivateKey()}. Alternatively, this property
         * can be set using either {@link #authKeyFile()} residing in the {@code user.home} directory, or using
         * {@link #authPrivateKeyPath()}.
         *
         * @return the auth private key
         */
        public Optional<char[]> authPrivateKey() {
            return Optional.ofNullable(authPrivateKey);
        }
        /**
         * The OCI authentication passphrase.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPassphraseCharacters()}.
         *
         * @return the auth passphrase
         */
        public Optional<char[]> authPassphrase() {
            return Optional.ofNullable(authPassphrase);
        }
        /**
         * The OCI region.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, either this property or {@link com.oracle.bmc.auth.RegionProvider} must be provide a value in order
         * to set the {@linkplain com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider#getRegion()}.
         *
         * @return the auth region
         */
        public Optional<String> authRegion() {
            return Optional.ofNullable(authRegion);
        }
        /**
         * The OCI tenant id.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}. This is also known as {@link #simpleConfigIsPresent()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider#getTenantId()}.
         *
         * @return the auth tenant id
         */
        public Optional<String> authTenantId() {
            return Optional.ofNullable(authTenantId);
        }
        /**
         * The OCI user id.
         * <p>
         * This configuration property has an effect only when {@code config} is, explicitly or implicitly,
         * present in the value for the {@link #authStrategies()}.
         * When it is present, this property must be provided in order to set the
         * {@linkplain com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider#getUserId()}.
         *
         * @return the auth user id
         */
        public Optional<String> authUserId() {
            return Optional.ofNullable(authUserId);
        }
        /**
         * The OCI IMDS hostname.
         * <p>
         * This configuration property is used to identify the metadata service url.
         *
         * @return the imds host name
         */
        public String imdsHostName() {
            return imdsHostName;
        }
        /**
         * The OCI IMDS connection timeout. This is used to auto-detect availability.
         * <p>
         * This configuration property is used when attempting to connect to the metadata service.
         *
         * @return the imds timeout
         */
        public java.time.Duration imdsTimeout() {
            return imdsTimeout;
        }
        /**
         * If this instance was configured, this would be the config instance used.
         *
         * @return config node used to configure this builder, or empty if not configured
         */
        public Optional<Config> config() {
            return Optional.ofNullable(config);
        }
        @Override
        public String toString() {
            return "OciConfigBuilder{"
                         + "authStrategy=" + authStrategy + ","
                         + "authStrategies=" + authStrategies + ","
                         + "configPath=" + configPath + ","
                         + "configProfile=" + configProfile + ","
                         + "authFingerprint=" + authFingerprint + ","
                         + "authKeyFile=" + authKeyFile + ","
                         + "authPrivateKeyPath=" + authPrivateKeyPath + ","
                         + "authPrivateKey=" + (authPrivateKey == null ? "null" : "****") + ","
                         + "authPassphrase=" + (authPassphrase == null ? "null" : "****") + ","
                         + "authRegion=" + authRegion + ","
                         + "authTenantId=" + authTenantId + ","
                         + "authUserId=" + authUserId + ","
                         + "imdsHostName=" + imdsHostName + ","
                         + "imdsTimeout=" + imdsTimeout
                        + "}";
        }
        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class OciConfigImpl implements OciConfig {
            private final Optional<String> authStrategy;
            private final java.util.List<String> authStrategies;
            private final Optional<String> configPath;
            private final Optional<String> configProfile;
            private final Optional<String> authFingerprint;
            private final String authKeyFile;
            private final Optional<String> authPrivateKeyPath;
            private final Optional<char[]> authPrivateKey;
            private final Optional<char[]> authPassphrase;
            private final Optional<String> authRegion;
            private final Optional<String> authTenantId;
            private final Optional<String> authUserId;
            private final String imdsHostName;
            private final java.time.Duration imdsTimeout;

            /**
             * Create an instance providing a builder.
             * @param builder extending builder base of this prototype
             */
            protected OciConfigImpl(BuilderBase<?, ?> builder) {
                this.authStrategy =  builder.authStrategy();
                this.authStrategies = java.util.List.copyOf(builder.authStrategies());
                this.configPath =  builder.configPath();
                this.configProfile =  builder.configProfile();
                this.authFingerprint =  builder.authFingerprint();
                this.authKeyFile =  builder.authKeyFile();
                this.authPrivateKeyPath =  builder.authPrivateKeyPath();
                this.authPrivateKey =  builder.authPrivateKey();
                this.authPassphrase =  builder.authPassphrase();
                this.authRegion =  builder.authRegion();
                this.authTenantId =  builder.authTenantId();
                this.authUserId =  builder.authUserId();
                this.imdsHostName =  builder.imdsHostName();
                this.imdsTimeout =  builder.imdsTimeout();
            }

    @Override
    public Optional<String> authStrategy() {
        return authStrategy;
    }

    @Override
    public java.util.List<String> authStrategies() {
        return authStrategies;
    }

    @Override
    public Optional<String> configPath() {
        return configPath;
    }

    @Override
    public Optional<String> configProfile() {
        return configProfile;
    }

    @Override
    public Optional<String> authFingerprint() {
        return authFingerprint;
    }

    @Override
    public String authKeyFile() {
        return authKeyFile;
    }

    @Override
    public Optional<String> authPrivateKeyPath() {
        return authPrivateKeyPath;
    }

    @Override
    public Optional<char[]> authPrivateKey() {
        return authPrivateKey;
    }

    @Override
    public Optional<char[]> authPassphrase() {
        return authPassphrase;
    }

    @Override
    public Optional<String> authRegion() {
        return authRegion;
    }

    @Override
    public Optional<String> authTenantId() {
        return authTenantId;
    }

    @Override
    public Optional<String> authUserId() {
        return authUserId;
    }

    @Override
    public String imdsHostName() {
        return imdsHostName;
    }

    @Override
    public java.time.Duration imdsTimeout() {
        return imdsTimeout;
    }
        @Override
        public String toString() {
            return "OciConfig{"
                         + "authStrategy=" + authStrategy + ","
                         + "authStrategies=" + authStrategies + ","
                         + "configPath=" + configPath + ","
                         + "configProfile=" + configProfile + ","
                         + "authFingerprint=" + authFingerprint + ","
                         + "authKeyFile=" + authKeyFile + ","
                         + "authPrivateKeyPath=" + authPrivateKeyPath + ","
                         + "authPrivateKey=" + (authPrivateKey.isPresent() ? "****" : "null") + ","
                         + "authPassphrase=" + (authPassphrase.isPresent() ? "****" : "null") + ","
                         + "authRegion=" + authRegion + ","
                         + "authTenantId=" + authTenantId + ","
                         + "authUserId=" + authUserId + ","
                         + "imdsHostName=" + imdsHostName + ","
                         + "imdsTimeout=" + imdsTimeout
                        + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof OciConfig other)) {
                return false;
            }
            return Objects.equals(authStrategy, other.authStrategy())
                    && Objects.equals(authStrategies, other.authStrategies())
                    && Objects.equals(configPath, other.configPath())
                    && Objects.equals(configProfile, other.configProfile())
                    && Objects.equals(authFingerprint, other.authFingerprint())
                    && Objects.equals(authKeyFile, other.authKeyFile())
                    && Objects.equals(authPrivateKeyPath, other.authPrivateKeyPath())
                    && Objects.equals(authPrivateKey, other.authPrivateKey())
                    && Objects.equals(authPassphrase, other.authPassphrase())
                    && Objects.equals(authRegion, other.authRegion())
                    && Objects.equals(authTenantId, other.authTenantId())
                    && Objects.equals(authUserId, other.authUserId())
                    && Objects.equals(imdsHostName, other.imdsHostName())
                    && Objects.equals(imdsTimeout, other.imdsTimeout());
        }

        @Override
        public int hashCode() {
            return Objects.hash(authStrategy, authStrategies, configPath, configProfile, authFingerprint, authKeyFile,
                                authPrivateKeyPath, authPrivateKey, authPassphrase, authRegion, authTenantId, authUserId,
                                imdsHostName, imdsTimeout);
        }
        }
    }

    /**
     * Fluent API builder for {@link OciConfig}.
     */
    class Builder extends BuilderBase<Builder, OciConfig> implements io.helidon.common.Builder<Builder, OciConfig> {
        private Builder() {
        }

        /**
         * Builds the prototype.
         *
         * @return the prototype
         */
        public OciConfig buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new OciConfigImpl(this);
        }

        @Override
        public OciConfig build() {
            return buildPrototype();
        }

    }
}
