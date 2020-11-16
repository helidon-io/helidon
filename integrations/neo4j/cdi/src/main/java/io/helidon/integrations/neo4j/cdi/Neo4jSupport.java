package io.helidon.integrations.neo4j.cdi;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;

import io.helidon.config.Config;

/**
 * Created by Dmitry Alexandrov on 12.11.20.
 */
public class Neo4jSupport {

    public final String username;
    public final String password;
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
    public org.neo4j.driver.Config.TrustStrategy internalRepresentation;

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

    }

    public static Neo4jSupport create(Config config) {
        return builder().config(config).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public org.neo4j.driver.Config.TrustStrategy toInternalRepresentation() {

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
        public int maxConnectionPoolSize;
        public Duration idleTimeBeforeConnectionTest;
        public Duration maxConnectionLifetime;
        public Duration connectionAcquisitionTimeout;

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
            config.get("driver.authentication.username").asString().ifPresent(this::username);
            config.get("driver.authentication.password").asString().ifPresent(this::password);
            config.get("driver.uri").asString().ifPresent(this::uri);
            config.get("driver.encrypted").asBoolean().ifPresent(this::encrypted);
            config.get("driver.disabled").asBoolean().ifPresent(this::disabled);

            //pool
            config.get("pool.metricsEnabled").asBoolean().ifPresent(this::metricsEnabled);
            config.get("pool.logLeakedSessions").asBoolean().ifPresent(this::logLeakedSessions);
            config.get("pool.maxConnectionPoolSize").asInt().ifPresent(this::maxConnectionPoolSize);
            config.get("pool.idleTimeBeforeConnectionTest").asLong().ifPresent(this::idleTimeBeforeConnectionTest);
            config.get("pool.maxConnectionLifetime").asLong().ifPresent(this::maxConnectionLifetime);
            config.get("pool.connectionAcquisitionTimeout").asLong().ifPresent(this::connectionAcquisitionTimeout);

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

        //TODO: is this ok???
        public Builder idleTimeBeforeConnectionTest(long idleTimeBeforeConnectionTest) {
            this.idleTimeBeforeConnectionTest = Duration.ofSeconds(maxConnectionPoolSize);
            return this;
        }

        public Builder maxConnectionLifetime(long maxConnectionLifetime) {
            this.maxConnectionLifetime = Duration.ofSeconds(maxConnectionLifetime);
            return this;
        }

        public Builder connectionAcquisitionTimeout(long connectionAcquisitionTimeout) {
            this.connectionAcquisitionTimeout = Duration.ofSeconds(connectionAcquisitionTimeout);
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
