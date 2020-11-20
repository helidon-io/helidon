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

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.logging.Level;

import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;

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
 * Implements {@link io.helidon.webserver.Service}
 *
 * @author Dmitry Aleksandrov
 * @author Tim Quinn
 */
public class Neo4jSupport implements Service {

    //authentication
    public final String username;
    public final String password;
    //general
    public final String uri;
    public final boolean encrypted;
    //pool
    public final boolean metricsEnabled;
    public final boolean logLeakedSessions;
    public final int maxConnectionPoolSize;
    public final Duration idleTimeBeforeConnectionTest;
    public final Duration maxConnectionLifetime;
    public final Duration connectionAcquisitionTimeout;
    //trust
    public final Strategy strategy;
    public final File certFile;
    public final boolean hostnameVerificationEnabled;
    public boolean disabled;
    //helpers
    private Driver driver;

    private Neo4jSupport(Builder builder) {
        this.username = builder.username;
        this.password = builder.password;
        this.uri = builder.uri;
        this.encrypted = builder.encrypted;
        this.disabled = builder.disabled;
        //pool
        this.metricsEnabled = builder.metricsEnabled;
        this.logLeakedSessions = builder.logLeakedSessions;
        this.maxConnectionPoolSize = builder.maxConnectionPoolSize;
        this.idleTimeBeforeConnectionTest = builder.idleTimeBeforeConnectionTest;
        this.maxConnectionLifetime = builder.maxConnectionLifetime;
        this.connectionAcquisitionTimeout = builder.connectionAcquisitionTimeout;
        //trust
        this.strategy = builder.strategy;
        this.certFile = builder.certFile;
        this.hostnameVerificationEnabled = builder.hostnameVerificationEnabled;

        this.driver = initDriver();
    }

    public static Neo4jSupport create(Config config) {
        return builder().config(config).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Neo4j base config helper method
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
     * The main entry point to the Neo4j Support
     * @return
     */
    public Driver driver() {
        return driver;
    }

    /**
     * Neo4j base driver construction method
     *
     * @return
     */
    private Driver initDriver() {
        AuthToken authToken = AuthTokens.none();
        if (!disabled) {
            authToken = AuthTokens.basic(username, password);
        }

        org.neo4j.driver.Config.ConfigBuilder configBuilder = createBaseConfig();
        configureSsl(configBuilder);
        configurePoolSettings(configBuilder);

        return GraphDatabase.driver(uri, authToken, configBuilder.build());

    }

    /**
     * Currently our service does not any endpoints
     *
     * @param rules a routing rules to update
     */
    @Override
    public void update(Routing.Rules rules) {
        // If Neo4J support in Helidon adds no new endpoints,
        // then we do not need to do anything here.
    }

    private org.neo4j.driver.Config.TrustStrategy toInternalRepresentation() {

        org.neo4j.driver.Config.TrustStrategy internalRepresentation;
        Strategy nonNullStrategy = strategy == null ? Strategy.TRUST_SYSTEM_CA_SIGNED_CERTIFICATES : strategy;
        switch (nonNullStrategy) {
        case TRUST_ALL_CERTIFICATES:
            internalRepresentation = org.neo4j.driver.Config.TrustStrategy.trustAllCertificates();
            break;
        case TRUST_SYSTEM_CA_SIGNED_CERTIFICATES:
            internalRepresentation = org.neo4j.driver.Config.TrustStrategy.trustSystemCertificates();
            break;
        case TRUST_CUSTOM_CA_SIGNED_CERTIFICATES:
            if (certFile.isFile()) {
                internalRepresentation = org.neo4j.driver.Config.TrustStrategy.trustCustomCertificateSignedBy(certFile);
            } else {
                throw new RuntimeException("Configured trust strategy requires a certificate file.");
            }
            break;
        default:
            throw new RuntimeException("Unknown trust strategy: " + this.strategy.name());
        }

        if (hostnameVerificationEnabled) {
            internalRepresentation.withHostnameVerification();
        } else {
            internalRepresentation.withoutHostnameVerification();
        }
        return internalRepresentation;
    }

    private void configureSsl(org.neo4j.driver.Config.ConfigBuilder configBuilder) {

        if (encrypted) {
            configBuilder.withEncryption();
            configBuilder.withTrustStrategy(toInternalRepresentation());
        } else {
            configBuilder.withoutEncryption();
        }
    }

    private void configurePoolSettings(org.neo4j.driver.Config.ConfigBuilder configBuilder) {

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

    public enum Strategy {
        TRUST_ALL_CERTIFICATES,
        TRUST_CUSTOM_CA_SIGNED_CERTIFICATES,
        TRUST_SYSTEM_CA_SIGNED_CERTIFICATES
    }

    public static class Builder implements io.helidon.common.Builder<Neo4jSupport> {
        public boolean encrypted;
        public boolean disabled;
        public String username;
        public String password;
        public String uri;

        //pool
        public boolean metricsEnabled;
        public boolean logLeakedSessions;
        public int maxConnectionPoolSize = 100;
        public Duration idleTimeBeforeConnectionTest = Duration.ofMillis(-1);
        public Duration maxConnectionLifetime = Duration.ofHours(1);
        public Duration connectionAcquisitionTimeout = Duration.ofMinutes(1);

        //trust
        public Strategy strategy;
        public File certFile;
        public boolean hostnameVerificationEnabled;

        private Builder() {
        }

        @Override
        public Neo4jSupport build() {
            return new Neo4jSupport(this);
        }

        public Builder config(Config config) {
            config.get("authentication.username").asString().ifPresent(this::username);
            config.get("authentication.password").asString().ifPresent(this::password);
            config.get("uri").asString().ifPresent(this::uri);
            config.get("encrypted").asBoolean().ifPresent(this::encrypted);
            config.get("cddisabled").asBoolean().ifPresent(this::disabled);

            //pool
            config.get("pool.metricsEnabled").asBoolean().ifPresent(this::metricsEnabled);
            config.get("pool.logLeakedSessions").asBoolean().ifPresent(this::logLeakedSessions);
            config.get("pool.maxConnectionPoolSize").asInt().ifPresent(this::maxConnectionPoolSize);
            config.get("pool.idleTimeBeforeConnectionTest").asString().ifPresent(this::idleTimeBeforeConnectionTest);
            config.get("pool.maxConnectionLifetime").asString().ifPresent(this::maxConnectionLifetime);
            config.get("pool.connectionAcquisitionTimeout").asString().ifPresent(this::connectionAcquisitionTimeout);

            //trust
            config.get("trustsettings.strategy").asString().ifPresent(this::strategy);
            config.get("trustsettings.certFile").asString().ifPresent(this::certFile);
            config.get("trustsettings.hostnameVerificationEnabled").asBoolean().ifPresent(this::hostnameVerificationEnabled);

            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;

        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        public Builder encrypted(boolean encrypted) {
            this.encrypted = encrypted;
            return this;
        }

        public Builder disabled(boolean disabled) {
            this.disabled = disabled;
            return this;
        }

        //pool
        public Builder metricsEnabled(boolean metricsEnabled) {
            this.metricsEnabled = metricsEnabled;
            return this;
        }

        public Builder logLeakedSessions(boolean logLeakedSessions) {
            this.logLeakedSessions = logLeakedSessions;
            return this;
        }

        public Builder maxConnectionPoolSize(int maxConnectionPoolSize) {
            this.maxConnectionPoolSize = maxConnectionPoolSize;
            return this;
        }

        public Builder idleTimeBeforeConnectionTest(String idleTimeBeforeConnectionTest) {
            this.idleTimeBeforeConnectionTest = Duration.parse(idleTimeBeforeConnectionTest);
            return this;
        }

        public Builder maxConnectionLifetime(String maxConnectionLifetime) {
            this.maxConnectionLifetime = Duration.parse(maxConnectionLifetime);
            return this;
        }

        public Builder connectionAcquisitionTimeout(String connectionAcquisitionTimeout) {
            this.connectionAcquisitionTimeout = Duration.parse(connectionAcquisitionTimeout);
            return this;
        }

        public Builder strategy(String strategy) {
            this.strategy = Strategy.valueOf(strategy);
            return this;
        }

        public Builder certFile(String certFile) {
            this.certFile = Path.of(certFile).toFile();
            return this;
        }

        public Builder hostnameVerificationEnabled(boolean hostnameVerificationEnabled) {
            this.hostnameVerificationEnabled = hostnameVerificationEnabled;
            return this;
        }
    }
}
