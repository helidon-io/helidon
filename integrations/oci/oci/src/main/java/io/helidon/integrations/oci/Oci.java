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
 *
 */


package io.helidon.integrations.oci;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;


import io.helidon.config.Config;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.URLBasedX509CertificateSupplier;
import com.oracle.bmc.auth.internal.AuthUtils;


/**
 * OCI support for Helidon.
 */
public class Oci {

    private AuthenticationDetailsProvider provider;
    private ClientConfiguration clientConfig;

    private Oci() {
        //private constructor
    }

    private Oci(Builder builder) {
        provider = builder.configureProvider();
        clientConfig = builder.getClientConfig();
    }

    /**
     * The OCI create method.
     *
     * @param config from SE.
     * @return using builder pattern.
     */
    public static Oci create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Get the registered provider.
     *
     * @return AuthenticationDetailsProvider.
     */
    public AuthenticationDetailsProvider provider() {
        return provider;
    }

    /**
     * Get the Configured client.
     *
     * @return ClientConfiguration.
     */
    public ClientConfiguration clientConfig() {
        return clientConfig;
    }

    /**
     * Access to Builder.
     *
     * @return The Builder.
     */
    public static Builder builder() {
        return new Builder();
    }


    /**
     * Builder for {@link Oci}
     */
    public static final class Builder implements io.helidon.common.Builder<Oci> {

        private AuthenticationDetailsProvider provider;
        private ClientConfiguration clientConfig;

        private String ociAuthProfile;
        private String ociConfigPath;

        private String ociAuthFingerprint;
        private String ociAuthPassphraseCharacters;
        private String ociAuthTenancy;
        private String ociAuthUser;
        private String ociAuthPrivateKey;
        private String ociAuthKeyFile;
        private String ociAuthRegion;

        private Profile configProfile;

        private int clientConnectionTimeoutMillis = 3000;
        private int clientReadTimeoutMillis = 60000;

        /**
         * Build the OCI support.
         *
         * @return
         */
        @Override
        public Oci build() {
            provider = configureProvider();
            clientConfig = configureClient();
            return new Oci(this);
        }

        /**
         * Read the configuration.
         *
         * @param config
         * @return
         */
        public Builder config(Config config) {
            config.get("config.profile").as(Profile.class).ifPresent(this::configProfile);
            config.get("auth.profile").asString().ifPresent(this::ociAuthProfile);
            config.get("config.path").asString().ifPresent(this::ociConfigPath);
            config.get("auth.fingerprint").asString().ifPresent(this::ociAuthFingerprint);
            config.get("auth.passphrase.characters").asString().ifPresent(this::ociAuthPassphraseCharacters);
            config.get("auth.tenancy").asString().ifPresent(this::ociAuthTenancy);
            config.get("auth.user").asString().ifPresent(this::ociAuthUser);
            config.get("auth.private.key").asString().ifPresent(this::ociAuthPrivateKey);
            config.get("auth.keyFile").asString().ifPresent(this::ociAuthKeyFile);
            config.get("auth.region").asString().ifPresent(this::ociAuthRegion);

            config.get("client.connectionTimeoutMillis").asInt().ifPresent(this::clientConnectionTimeoutMillis);
            config.get("client.readTimeoutMillis").asInt().ifPresent(this::clientReadTimeoutMillis);

            return this;
        }

        /**
         * Configuration profile.
         */
        public enum Profile {
            /**
             * Should be used when external OCI config file provided
             */
            DEFAULT,
            /**
             * Should be used when Helidon configuration is the source for parameters
             */
            MANUAL
        }

        AuthenticationDetailsProvider configureProvider() {
            if (configProfile == Profile.DEFAULT) {
                ConfigFileAuthenticationDetailsProvider temp = null;
                try {
                    if (ociConfigPath == null) {
                        temp = new ConfigFileAuthenticationDetailsProvider(ociAuthProfile);
                    } else {
                        temp = new ConfigFileAuthenticationDetailsProvider(ociConfigPath, ociAuthProfile);
                    }
                } catch (final IOException ioException) {
                    temp = null;
                } finally {
                    return temp;
                }
            } else { //Should we use MANUAL here?
                SimpleAuthenticationDetailsProvider simpleProvider =
                        SimpleAuthenticationDetailsProvider.builder()
                                .tenantId(ociAuthTenancy)
                                .userId(ociAuthUser)
                                .fingerprint(ociAuthFingerprint)
                                .region(Region.valueOf(ociAuthRegion))
                                .passphraseCharacters(ociAuthPassphraseCharacters.toCharArray())
                                .privateKeySupplier(this::getPrivateKey)
                                .build();
                return simpleProvider;
            }
        }

        ClientConfiguration configureClient() {
            ClientConfiguration clientConfig
                    = ClientConfiguration.builder()
                    .connectionTimeoutMillis(clientConnectionTimeoutMillis)
                    .readTimeoutMillis(clientReadTimeoutMillis)
                    .build();
            return clientConfig;
        }

        /**
         * Access point to the provider.
         *
         * @return AuthenticationDetailsProvider as the main entry point.
         */
        public AuthenticationDetailsProvider getProvider() {
            return provider;
        }

        /**
         * Access point to the Client configuration.
         *
         * @return ClientConfiguration built and configured.
         */
        public ClientConfiguration getClientConfig() {
            return clientConfig;
        }

        private InputStream getPrivateKey() {
            if (ociAuthPrivateKey == null || ociAuthPrivateKey.trim().isEmpty()) {
                final String pemFormattedPrivateKeyFilePath =
                        Optional.ofNullable(ociAuthKeyFile)
                                .orElse(Paths.get(System.getProperty("user.home"), ".oci/oci_api_key.pem").toString());
                assert pemFormattedPrivateKeyFilePath != null;
                try {
                    return new BufferedInputStream(Files.newInputStream(Paths.get(pemFormattedPrivateKeyFilePath)));
                } catch (final IOException ioException) {
                    throw new RuntimeException(ioException.getMessage(), ioException);
                }
            } else {
                return new BufferedInputStream(new ByteArrayInputStream(ociAuthPrivateKey.getBytes(StandardCharsets.UTF_8)));
            }
        }

        /**
         * OCI Auth profile.
         *
         * @param ociAuthProfile
         * @return builder
         */
        public Builder ociAuthProfile(String ociAuthProfile) {
            this.ociAuthProfile = ociAuthProfile;
            return this;
        }

        /**
         * OCI Config path.
         *
         * @param ociConfigPath
         * @return builder
         */
        public Builder ociConfigPath(String ociConfigPath) {
            this.ociConfigPath = ociConfigPath;
            return this;
        }

        /**
         * OCI Auth Fingerprint.
         *
         * @param ociAuthFingerprint
         * @return builder
         */
        public Builder ociAuthFingerprint(String ociAuthFingerprint) {
            this.ociAuthFingerprint = ociAuthFingerprint;
            return this;
        }

        /**
         * OCI Auth Passphrase Characters.
         *
         * @param ociAuthPassphraseCharacters
         * @return builder
         */
        public Builder ociAuthPassphraseCharacters(String ociAuthPassphraseCharacters) {
            this.ociAuthPassphraseCharacters = ociAuthPassphraseCharacters;
            return this;
        }

        /**
         * OCI Auth Tenancy.
         *
         * @param ociAuthTenancy
         * @return builder
         */
        public Builder ociAuthTenancy(String ociAuthTenancy) {
            this.ociAuthTenancy = ociAuthTenancy;
            return this;
        }

        /**
         *  OCI Auth User.
         *
         * @param ociAuthUser
         * @return builder
         */
        public Builder ociAuthUser(String ociAuthUser) {
            this.ociAuthUser = ociAuthUser;
            return this;
        }

        /**
         * OCI Auth Private Key.
         *
         * @param ociAuthPrivateKey
         * @return builder
         */
        public Builder ociAuthPrivateKey(String ociAuthPrivateKey) {
            this.ociAuthPrivateKey = ociAuthPrivateKey;
            return this;
        }

        /**
         * OCI Auth Key File.
         *
         * @param ociAuthKeyFile
         * @return builder
         */
        public Builder ociAuthKeyFile(String ociAuthKeyFile) {
            this.ociAuthKeyFile = ociAuthKeyFile;
            return this;
        }

        /**
         * OCI Auth Region.
         *
         * @param ociAuthRegion
         * @return builder
         */
        public Builder ociAuthRegion(String ociAuthRegion) {
            this.ociAuthRegion = ociAuthRegion;
            return this;
        }

        /**
         * Config Profile.
         *
         * @param configProfile
         * @return builder
         */
        public Builder configProfile(Profile configProfile) {
            this.configProfile = configProfile;
            return this;
        }

        /**
         * Client Connection Timeout Millis.
         *
         * @param clientConnectionTimeoutMillis
         * @return builder
         */
        public Builder clientConnectionTimeoutMillis(int clientConnectionTimeoutMillis) {
            this.clientConnectionTimeoutMillis = clientConnectionTimeoutMillis;
            return this;
        }

        /**
         * Client Read Timeout Millis.
         *
         * @param clientReadTimeoutMillis
         * @return builder
         */
        public Builder clientReadTimeoutMillis(int clientReadTimeoutMillis) {
            this.clientReadTimeoutMillis = clientReadTimeoutMillis;
            return this;
        }
    }
}
