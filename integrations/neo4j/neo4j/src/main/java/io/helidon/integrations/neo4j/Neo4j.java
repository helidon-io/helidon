/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.integrations.neo4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.logging.Level;

import io.helidon.config.Config;

import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Logging;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Main entry point for Neo4j support for Helidon.
 * Performs configuration and the prepared driver.
 *
 */
public final class Neo4j {
    private final Driver driver;

    private Neo4j(Builder builder) {
        this.driver = builder.driver;
    }

    /**
     * Create the Neo4j support using builder.
     *
     * @param config from the extenal configuration
     * @return Neo4j support
     */
    public static Neo4j create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Following the builder pattern.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The main entry point to the Neo4j Support.
     *
     * @return neo4j driver
     */
    public Driver driver() {
        return driver;
    }

    /**
     * Fluent API builder for Neo4j.
     */
    public static final class Builder implements io.helidon.common.Builder<Neo4j> {
        private boolean encrypted;
        private boolean authenticationEnabled = true;
        private String username;
        private String password;
        private String uri;

        //pool
        private boolean metricsEnabled;
        private boolean logLeakedSessions;
        private int maxConnectionPoolSize = 100;
        private Duration idleTimeBeforeConnectionTest = Duration.ofMillis(-1);
        private Duration maxConnectionLifetime = Duration.ofHours(1);
        private Duration connectionAcquisitionTimeout = Duration.ofMinutes(1);

        //trust
        private TrustStrategy trustStrategy = TrustStrategy.TRUST_SYSTEM_CA_SIGNED_CERTIFICATES;
        private Path certFile;
        private boolean hostnameVerificationEnabled;

        // explicit driver
        private Driver driver;

        private Builder() {
        }

        /**
         *
         * @return
         */
        @Override
        public Neo4j build() {
            if (driver == null) {
                driver = initDriver();
            }
            return new Neo4j(this);
        }

        /**
         * Read the configuration from external file and initialize the builder.
         * @param config external configuration
         * @return the builder
         */
        public Builder config(Config config) {
            config.get("authentication.username").asString().ifPresent(this::username);
            config.get("authentication.password").asString().ifPresent(this::password);
            config.get("authentication.enabled").asBoolean().ifPresent(this::authenticationEnabled);
            config.get("uri").asString().ifPresent(this::uri);
            config.get("encrypted").asBoolean().ifPresent(this::encrypted);

            //pool
            config.get("pool.metricsEnabled").asBoolean().ifPresent(this::metricsEnabled);
            config.get("pool.logLeakedSessions").asBoolean().ifPresent(this::logLeakedSessions);
            config.get("pool.maxConnectionPoolSize").asInt().ifPresent(this::maxConnectionPoolSize);
            config.get("pool.idleTimeBeforeConnectionTest").as(Duration.class).ifPresent(this::idleTimeBeforeConnectionTest);
            config.get("pool.maxConnectionLifetime").as(Duration.class).ifPresent(this::maxConnectionLifetime);
            config.get("pool.connectionAcquisitionTimeout").as(Duration.class).ifPresent(this::connectionAcquisitionTimeout);

            //trust
            config.get("trustsettings.trustStrategy").asString().map(TrustStrategy::valueOf).ifPresent(this::trustStrategy);
            config.get("trustsettings.certificate").as(Path.class).ifPresent(this::certificate);
            config.get("trustsettings.hostnameVerificationEnabled").asBoolean().ifPresent(this::hostnameVerificationEnabled);

            return this;
        }

        /**
         * Create username.
         *
         * @param username
         * @return Builder
         */
        public Builder username(String username) {
            Objects.requireNonNull(username);
            this.username = username;
            this.authenticationEnabled = true;
            return this;

        }

        /**
         * Create password.
         *
         * @param password
         * @return Builder
         */
        public Builder password(String password) {
            Objects.requireNonNull(password);
            this.password = password;
            return this;
        }

        /**
         * Create uri.
         *
         * @param uri
         * @return Builder
         */
        public Builder uri(String uri) {
            Objects.requireNonNull(uri);
            this.uri = uri;
            return this;
        }

        /**
         * Enable ecrypted field.
         *
         * @param encrypted
         * @return Builder
         */
        public Builder encrypted(boolean encrypted) {
            this.encrypted = encrypted;
            return this;
        }

        /**
         * Enable authentication.
         *
         * @param authenticationEnabled
         * @return Builder
         */
        public Builder authenticationEnabled(boolean authenticationEnabled) {
            this.authenticationEnabled = authenticationEnabled;
            return this;
        }

        /**
         * Enagle metrics.
         *
         * @param metricsEnabled
         * @return Builder
         */
        public Builder metricsEnabled(boolean metricsEnabled) {
            this.metricsEnabled = metricsEnabled;
            return this;
        }

        /**
         * Eable log leaked sessions.
         *
         * @param logLeakedSessions
         * @return Builder
         */
        public Builder logLeakedSessions(boolean logLeakedSessions) {
            this.logLeakedSessions = logLeakedSessions;
            return this;
        }

        /**
         * Set pool size.
         *
         * @param maxConnectionPoolSize
         * @return Builder
         */
        public Builder maxConnectionPoolSize(int maxConnectionPoolSize) {
            this.maxConnectionPoolSize = maxConnectionPoolSize;
            return this;
        }

        /**
         * Set idle time.
         *
         * @param idleTimeBeforeConnectionTest
         * @return Builder
         */
        public Builder idleTimeBeforeConnectionTest(Duration idleTimeBeforeConnectionTest) {
            Objects.requireNonNull(idleTimeBeforeConnectionTest);
            this.idleTimeBeforeConnectionTest = idleTimeBeforeConnectionTest;
            return this;
        }

        /**
         * Set max life time.
         *
         * @param maxConnectionLifetime
         * @return Builder
         */
        public Builder maxConnectionLifetime(Duration maxConnectionLifetime) {
            Objects.requireNonNull(maxConnectionLifetime);
            this.maxConnectionLifetime = maxConnectionLifetime;
            return this;
        }

        /**
         * Set connection acquisition timeout.
         *
         * @param connectionAcquisitionTimeout
         * @return Builder
         */
        public Builder connectionAcquisitionTimeout(Duration connectionAcquisitionTimeout) {
            Objects.requireNonNull(connectionAcquisitionTimeout);
            this.connectionAcquisitionTimeout = connectionAcquisitionTimeout;
            return this;
        }

        /**
         * Set trust strategy.
         *
         * @param strategy
         * @return Builder
         */
        public Builder trustStrategy(TrustStrategy strategy) {
            this.trustStrategy = strategy;
            return this;
        }

        /**
         * Set certificate path.
         *
         * @param certFile
         * @return Builder
         */
        public Builder certificate(Path certFile) {
            this.certFile = certFile;
            return this;
        }

        /**
         * Enable hostname verification.
         *
         * @param hostnameVerificationEnabled
         * @return Builder
         */
        public Builder hostnameVerificationEnabled(boolean hostnameVerificationEnabled) {
            this.hostnameVerificationEnabled = hostnameVerificationEnabled;
            return this;
        }

        /**
         * Neo4j base driver construction method.
         *
         * @return the driver
         */
        private Driver initDriver() {
            AuthToken authToken = AuthTokens.none();
            if (authenticationEnabled) {
                authToken = AuthTokens.basic(username, password);
            }

            org.neo4j.driver.Config.ConfigBuilder configBuilder = createBaseConfig();
            configureSsl(configBuilder);
            configurePoolSettings(configBuilder);

            return GraphDatabase.driver(uri, authToken, configBuilder.build());

        }

        private void configureSsl(org.neo4j.driver.Config.ConfigBuilder configBuilder) {

            if (encrypted) {
                configBuilder.withEncryption();
                configBuilder.withTrustStrategy(toInternalTrustStrategy());
            } else {
                configBuilder.withoutEncryption();
            }
        }

        private org.neo4j.driver.Config.TrustStrategy toInternalTrustStrategy() {
            org.neo4j.driver.Config.TrustStrategy internalRepresentation;
            switch (trustStrategy) {
            case TRUST_ALL_CERTIFICATES:
                internalRepresentation = org.neo4j.driver.Config.TrustStrategy.trustAllCertificates();
                break;
            case TRUST_SYSTEM_CA_SIGNED_CERTIFICATES:
                internalRepresentation = org.neo4j.driver.Config.TrustStrategy.trustSystemCertificates();
                break;
            case TRUST_CUSTOM_CA_SIGNED_CERTIFICATES:
                if (certFile == null) {
                    throw new Neo4jException("Configured trust trustStrategy " + trustStrategy.name()
                            + " requires a certificate file, "
                            + "configured through builder, or using trustsettings.certificate "
                            + "configuration property.");
                }
                if (Files.isRegularFile(certFile)) {
                    internalRepresentation = org.neo4j.driver.Config.TrustStrategy
                            .trustCustomCertificateSignedBy(certFile.toFile());
                } else {
                    throw new Neo4jException("Configured trust trustStrategy requires a certificate file, but got: " + certFile
                            .toAbsolutePath());
                }
                break;
            default:
                throw new Neo4jException("Unknown trust trustStrategy: " + this.trustStrategy.name());
            }

            if (hostnameVerificationEnabled) {
                internalRepresentation.withHostnameVerification();
            } else {
                internalRepresentation.withoutHostnameVerification();
            }
            return internalRepresentation;
        }

        private void configurePoolSettings(org.neo4j.driver.Config.ConfigBuilder configBuilder) {

            if (logLeakedSessions) {
                configBuilder.withLeakedSessionsLogging();
            }
            configBuilder.withMaxConnectionPoolSize(maxConnectionPoolSize);
            configBuilder.withConnectionLivenessCheckTimeout(idleTimeBeforeConnectionTest.toMillis(), MILLISECONDS);
            configBuilder.withMaxConnectionLifetime(maxConnectionLifetime.toMillis(), MILLISECONDS);
            configBuilder.withConnectionAcquisitionTimeout(connectionAcquisitionTimeout.toMillis(), MILLISECONDS);

            if (metricsEnabled) {
                configBuilder.withDriverMetrics();
            } else {
                configBuilder.withoutDriverMetrics();
            }
        }

        /**
         * Neo4j base config helper method.
         *
         * @return
         */
        private static org.neo4j.driver.Config.ConfigBuilder createBaseConfig() {
            org.neo4j.driver.Config.ConfigBuilder configBuilder = org.neo4j.driver.Config.builder();
            Logging logging;
            try {
                logging = Logging.slf4j();
            } catch (Exception e) {
                logging = Logging.javaUtilLogging(Level.INFO);
            }
            configBuilder.withLogging(logging);
            return configBuilder;
        }

        /**
         * Security trustStrategy.
         */
        public enum TrustStrategy {
            /**
             * Trust all.
             */
            TRUST_ALL_CERTIFICATES,
            /**
             * Trust custom certificates.
             */
            TRUST_CUSTOM_CA_SIGNED_CERTIFICATES,
            /**
             * Trust system CA.
             */
            TRUST_SYSTEM_CA_SIGNED_CERTIFICATES
        }

    }
}
