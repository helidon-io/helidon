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

package io.helidon.integrations.oci.tls.certificates;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.Errors;
import io.helidon.config.Config;

/**
 * Blueprint configuration for {@link OciCertificatesTlsManager}.
 *
 * @see #builder()
 * @see #create()
 */
public interface OciCertificatesTlsManagerConfig extends OciCertificatesTlsManagerConfigBlueprint /*, Prototype.Api*/ {

    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static OciCertificatesTlsManagerConfig.Builder builder() {
        return new OciCertificatesTlsManagerConfig.Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static OciCertificatesTlsManagerConfig.Builder builder(OciCertificatesTlsManagerConfig instance) {
        return OciCertificatesTlsManagerConfig.builder().from(instance);
    }

    /**
     * Create a new instance from configuration.
     *
     * @param config used to configure the new instance
     * @return a new instance configured from configuration
     */
    static OciCertificatesTlsManagerConfig create(Config config) {
        return OciCertificatesTlsManagerConfig.builder().config(config).buildPrototype();
    }

    /**
     * Create a new instance with default values.
     *
     * @return a new instance
     */
    static OciCertificatesTlsManagerConfig create() {
        return OciCertificatesTlsManagerConfig.builder().buildPrototype();
    }

    /**
     * Fluent API builder base for {@link OciCertificatesTlsManager}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface
     */
    abstract class BuilderBase<BUILDER extends OciCertificatesTlsManagerConfig.BuilderBase<BUILDER, PROTOTYPE>,
            PROTOTYPE extends OciCertificatesTlsManagerConfig> {
        private Config config;
        private String caOcid;
        private String certOcid;
        private String compartmentOcid;
        private String keyOcid;
        private String schedule;
        private Supplier<char[]> keyPassword;
        private URI vaultCryptoEndpoint;
        private URI vaultManagementEndpoint;

        /**
         * Protected to support extensibility.
         */
        protected BuilderBase() {
        }

        BUILDER self() {
            return (BUILDER) this;
        }

        /**
         * Update this builder from an existing prototype instance.
         *
         * @param prototype existing prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(OciCertificatesTlsManagerConfig prototype) {
            schedule(prototype.schedule());
            vaultCryptoEndpoint(prototype.vaultCryptoEndpoint());
            vaultManagementEndpoint(prototype.vaultManagementEndpoint());
            compartmentOcid(prototype.compartmentOcid());
            caOcid(prototype.caOcid());
            certOcid(prototype.certOcid());
            keyOcid(prototype.keyOcid());
            keyPassword(prototype.keyPassword());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(OciCertificatesTlsManagerConfig.BuilderBase<?, ?> builder) {
            builder.schedule().ifPresent(this::schedule);
            builder.vaultCryptoEndpoint().ifPresent(this::vaultCryptoEndpoint);
            builder.vaultManagementEndpoint().ifPresent(this::vaultManagementEndpoint);
            builder.compartmentOcid().ifPresent(this::compartmentOcid);
            builder.caOcid().ifPresent(this::caOcid);
            builder.certOcid().ifPresent(this::certOcid);
            builder.keyOcid().ifPresent(this::keyOcid);
            builder.keyPassword().ifPresent(this::keyPassword);
            return self();
        }

        /**
         * Update builder from configuration (node of this type).
         * If a value is present in configuration, it would override currently configured values.
         *
         * @param config configuration instance used to obtain values to update this builder
         * @return updated builder instance
         */
        public BUILDER config(Config config) {
            Objects.requireNonNull(config);
            this.config = config;
            config.get("schedule").as(String.class).ifPresent(this::schedule);
            config.get("vault-crypto-endpoint").as(URI.class).ifPresent(this::vaultCryptoEndpoint);
            config.get("vault-management-endpoint").as(URI.class).ifPresent(this::vaultManagementEndpoint);
            config.get("compartment-ocid").as(String.class).ifPresent(this::compartmentOcid);
            config.get("ca-ocid").as(String.class).ifPresent(this::caOcid);
            config.get("cert-ocid").as(String.class).ifPresent(this::certOcid);
            config.get("key-ocid").as(String.class).ifPresent(this::keyOcid);
            keyPassword(config.get("key-password").asString().as(String::toCharArray).supplier());
            return self();
        }

        /**
         * The schedule for trigger a reload check, testing whether there is a new {@link javax.net.ssl.SSLContext} instance
         * available.
         *
         * @param schedule the schedule for reload
         * @return updated builder instance
         * @see #schedule()
         */
        public BUILDER schedule(String schedule) {
            Objects.requireNonNull(schedule);
            this.schedule = schedule;
            return self();
        }

        /**
         * The address to use for the OCI Key Management Service / Vault crypto usage.
         * Each OCI Vault has public crypto and management endpoints. We need to specify the crypto endpoint of the vault we are
         * rotating the private keys in. The implementation expects both client and server to store the private key in the same
         * vault.
         *
         * @param vaultCryptoEndpoint the address for the key management service / vault crypto usage
         * @return updated builder instance
         * @see #vaultCryptoEndpoint()
         */
        public BUILDER vaultCryptoEndpoint(URI vaultCryptoEndpoint) {
            Objects.requireNonNull(vaultCryptoEndpoint);
            this.vaultCryptoEndpoint = vaultCryptoEndpoint;
            return self();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #vaultManagementEndpoint()
         */
        public BUILDER clearVaultManagementEndpoint() {
            this.vaultManagementEndpoint = null;
            return self();
        }

        /**
         * The address to use for the OCI Key Management Service / Vault management usage.
         * The crypto endpoint of the vault we are rotating the private keys in.
         *
         * @param vaultManagementEndpoint the address for the key management service / vault management usage
         * @return updated builder instance
         * @see #vaultManagementEndpoint()
         */
        public BUILDER vaultManagementEndpoint(URI vaultManagementEndpoint) {
            Objects.requireNonNull(vaultManagementEndpoint);
            this.vaultManagementEndpoint = vaultManagementEndpoint;
            return self();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #compartmentOcid()
         */
        public BUILDER clearCompartmentOcid() {
            this.compartmentOcid = null;
            return self();
        }

        /**
         * The OCID of the compartment the services are in.
         *
         * @param compartmentOcid the compartment OCID
         * @return updated builder instance
         * @see #compartmentOcid()
         */
        public BUILDER compartmentOcid(String compartmentOcid) {
            Objects.requireNonNull(compartmentOcid);
            this.compartmentOcid = compartmentOcid;
            return self();
        }

        /**
         * The Certificate Authority OCID.
         *
         * @param caOcid certificate authority OCID
         * @return updated builder instance
         * @see #caOcid()
         */
        public BUILDER caOcid(String caOcid) {
            Objects.requireNonNull(caOcid);
            this.caOcid = caOcid;
            return self();
        }

        /**
         * The Certificate OCID.
         *
         * @param certOcid certificate OCID
         * @return updated builder instance
         * @see #certOcid()
         */
        public BUILDER certOcid(String certOcid) {
            Objects.requireNonNull(certOcid);
            this.certOcid = certOcid;
            return self();
        }

        /**
         * The Key OCID.
         *
         * @param keyOcid key OCID
         * @return updated builder instance
         * @see #keyOcid()
         */
        public BUILDER keyOcid(String keyOcid) {
            Objects.requireNonNull(keyOcid);
            this.keyOcid = keyOcid;
            return self();
        }

        /**
         * The Key password.
         *
         * @param keyPassword key password
         * @return updated builder instance
         * @see #keyPassword()
         */
        public BUILDER keyPassword(Supplier<char[]> keyPassword) {
            Objects.requireNonNull(keyPassword);
            this.keyPassword = keyPassword::get;
            return self();
        }

        /**
         * The Key password.
         *
         * @param keyPassword key password
         * @return updated builder instance
         * @see #keyPassword()
         */
        public BUILDER keyPassword(char[] keyPassword) {
            Objects.requireNonNull(keyPassword);
            this.keyPassword = () -> keyPassword;
            return self();
        }

        /**
         * The Key password.
         *
         * @param keyPassword key password
         * @return updated builder instance
         * @see #keyPassword()
         */
        public BUILDER keyPassword(String keyPassword) {
            Objects.requireNonNull(keyPassword);
            this.keyPassword = () -> keyPassword.toCharArray();
            return self();
        }

        /**
         * The schedule for trigger a reload check, testing whether there is a new {@link javax.net.ssl.SSLContext} instance
         * available.
         *
         * @return the schedule
         */
        public Optional<String> schedule() {
            return Optional.ofNullable(schedule);
        }

        /**
         * The address to use for the OCI Key Management Service / Vault crypto usage.
         * Each OCI Vault has public crypto and management endpoints. We need to specify the crypto endpoint of the vault we are
         * rotating the private keys in. The implementation expects both client and server to store the private key in the same
         * vault.
         *
         * @return the vault crypto endpoint
         */
        public Optional<URI> vaultCryptoEndpoint() {
            return Optional.ofNullable(vaultCryptoEndpoint);
        }

        /**
         * The address to use for the OCI Key Management Service / Vault management usage.
         * The crypto endpoint of the vault we are rotating the private keys in.
         *
         * @return the vault management endpoint
         */
        public Optional<URI> vaultManagementEndpoint() {
            return Optional.ofNullable(vaultManagementEndpoint);
        }

        /**
         * The OCID of the compartment the services are in.
         *
         * @return the compartment ocid
         */
        public Optional<String> compartmentOcid() {
            return Optional.ofNullable(compartmentOcid);
        }

        /**
         * The Certificate Authority OCID.
         *
         * @return the ca ocid
         */
        public Optional<String> caOcid() {
            return Optional.ofNullable(caOcid);
        }

        /**
         * The Certificate OCID.
         *
         * @return the cert ocid
         */
        public Optional<String> certOcid() {
            return Optional.ofNullable(certOcid);
        }

        /**
         * The Key OCID.
         *
         * @return the key ocid
         */
        public Optional<String> keyOcid() {
            return Optional.ofNullable(keyOcid);
        }

        /**
         * The Key password.
         *
         * @return the key password
         */
        public Optional<Supplier<char[]>> keyPassword() {
            return Optional.ofNullable(keyPassword);
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
            return "OciCertificatesTlsManagerConfigBuilder{"
                    + "schedule=" + schedule + ","
                    + "vaultCryptoEndpoint=" + vaultCryptoEndpoint + ","
                    + "vaultManagementEndpoint=" + vaultManagementEndpoint + ","
                    + "compartmentOcid=" + compartmentOcid + ","
                    + "caOcid=" + caOcid + ","
                    + "certOcid=" + certOcid + ","
                    + "keyOcid=" + keyOcid + ","
                    + "keyPassword=" + (keyPassword == null ? "null" : "****")
                    + "}";
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
            Errors.Collector collector = Errors.collector();
            if (schedule == null) {
                collector.fatal(getClass(), "Property \"schedule\" must not be null, but not set");
            }
            if (vaultCryptoEndpoint == null) {
                collector.fatal(getClass(), "Property \"vault-crypto-endpoint\" must not be null, but not set");
            }
            if (caOcid == null) {
                collector.fatal(getClass(), "Property \"ca-ocid\" must not be null, but not set");
            }
            if (certOcid == null) {
                collector.fatal(getClass(), "Property \"cert-ocid\" must not be null, but not set");
            }
            if (keyOcid == null) {
                collector.fatal(getClass(), "Property \"key-ocid\" must not be null, but not set");
            }
            if (keyPassword == null) {
                collector.fatal(getClass(), "Property \"key-password\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * The address to use for the OCI Key Management Service / Vault management usage.
         * The crypto endpoint of the vault we are rotating the private keys in.
         *
         * @param vaultManagementEndpoint the address for the key management service / vault management usage
         * @return updated builder instance
         * @see #vaultManagementEndpoint()
         */
        BUILDER vaultManagementEndpoint(Optional<? extends URI> vaultManagementEndpoint) {
            Objects.requireNonNull(vaultManagementEndpoint);
            this.vaultManagementEndpoint = vaultManagementEndpoint.orElse(null);
            return self();
        }

        /**
         * The OCID of the compartment the services are in.
         *
         * @param compartmentOcid the compartment OCID
         * @return updated builder instance
         * @see #compartmentOcid()
         */
        BUILDER compartmentOcid(Optional<? extends String> compartmentOcid) {
            Objects.requireNonNull(compartmentOcid);
            this.compartmentOcid = compartmentOcid.orElse(null);
            return self();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class OciCertificatesTlsManagerConfigImpl
                implements OciCertificatesTlsManagerConfig, Supplier<OciCertificatesTlsManager> {
            private final Optional<String> compartmentOcid;
            private final Optional<URI> vaultManagementEndpoint;
            private final String caOcid;
            private final String certOcid;
            private final String keyOcid;
            private final String schedule;
            private final Supplier<char[]> keyPassword;
            private final URI vaultCryptoEndpoint;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected OciCertificatesTlsManagerConfigImpl(OciCertificatesTlsManagerConfig.BuilderBase<?, ?> builder) {
                this.schedule = builder.schedule().get();
                this.vaultCryptoEndpoint = builder.vaultCryptoEndpoint().get();
                this.vaultManagementEndpoint = builder.vaultManagementEndpoint();
                this.compartmentOcid = builder.compartmentOcid();
                this.caOcid = builder.caOcid().get();
                this.certOcid = builder.certOcid().get();
                this.keyOcid = builder.keyOcid().get();
                this.keyPassword = builder.keyPassword().get();
            }

            //            @Override
            public OciCertificatesTlsManager build() {
                return OciCertificatesTlsManager.create(this);
            }

            @Override
            public OciCertificatesTlsManager get() {
                return build();
            }

            @Override
            public String schedule() {
                return schedule;
            }

            @Override
            public URI vaultCryptoEndpoint() {
                return vaultCryptoEndpoint;
            }

            @Override
            public Optional<URI> vaultManagementEndpoint() {
                return vaultManagementEndpoint;
            }

            @Override
            public Optional<String> compartmentOcid() {
                return compartmentOcid;
            }

            @Override
            public String caOcid() {
                return caOcid;
            }

            @Override
            public String certOcid() {
                return certOcid;
            }

            @Override
            public String keyOcid() {
                return keyOcid;
            }

            @Override
            public Supplier<char[]> keyPassword() {
                return keyPassword;
            }

            @Override
            public String toString() {
                return "OciCertificatesTlsManagerConfig{"
                        + "schedule=" + schedule + ","
                        + "vaultCryptoEndpoint=" + vaultCryptoEndpoint + ","
                        + "vaultManagementEndpoint=" + vaultManagementEndpoint + ","
                        + "compartmentOcid=" + compartmentOcid + ","
                        + "caOcid=" + caOcid + ","
                        + "certOcid=" + certOcid + ","
                        + "keyOcid=" + keyOcid + ","
                        + "keyPassword=" + (keyPassword == null ? "null" : "****")
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof OciCertificatesTlsManagerConfig other)) {
                    return false;
                }
                return Objects.equals(schedule, other.schedule())
                        && Objects.equals(vaultCryptoEndpoint, other.vaultCryptoEndpoint())
                        && Objects.equals(vaultManagementEndpoint, other.vaultManagementEndpoint())
                        && Objects.equals(compartmentOcid, other.compartmentOcid())
                        && Objects.equals(caOcid, other.caOcid())
                        && Objects.equals(certOcid, other.certOcid())
                        && Objects.equals(keyOcid, other.keyOcid())
                        && Objects.equals(keyPassword, other.keyPassword());
            }

            @Override
            public int hashCode() {
                return Objects.hash(schedule, vaultCryptoEndpoint, vaultManagementEndpoint, compartmentOcid, caOcid, certOcid,
                                    keyOcid, keyPassword);
            }

        }

    }

    /**
     * Fluent API builder for {@link OciCertificatesTlsManager}.
     */
    class Builder extends OciCertificatesTlsManagerConfig
                                  .BuilderBase<OciCertificatesTlsManagerConfig.Builder, OciCertificatesTlsManagerConfig>
            implements io.helidon.common.Builder<OciCertificatesTlsManagerConfig.Builder, OciCertificatesTlsManager> {

        private Builder() {
        }

        /**
         * Build the config instance.
         *
         * @return the built config instance
         */
        //        @Override
        public OciCertificatesTlsManagerConfig buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new OciCertificatesTlsManagerConfigImpl(this);
        }

        /**
         * Build the instance.
         *
         * @return the built instance
         */
        //        @Override
        public OciCertificatesTlsManager build() {
            return OciCertificatesTlsManager.create(this.buildPrototype());
        }

    }

}
