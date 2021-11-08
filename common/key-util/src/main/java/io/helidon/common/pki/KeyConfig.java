/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.common.pki;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.configurable.Resource;
import io.helidon.common.configurable.ResourceException;
import io.helidon.config.Config;
import io.helidon.config.DeprecatedConfig;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Configuration of keystore, certificates and keys. This class is not RSA specific, though it is tested with RSA keys only.
 * <p>
 * Can be either built through a builder, or loaded from configuration.
 * <p>
 * Full configuration example (this class can be used to wrap either of: private key, public key, public key certificate, and
 * certification chain, and a list of certificates):
 * <pre>
 * # path to keystore (mandatory when loaded from config)
 * keystore.resource.path = "src/test/resources/keystore.p12"
 * # Keystore type
 * # PKCS12 or JKS
 * # defaults to jdk default (PKCS12 for latest JDK)
 * keystore-type = "JKS"
 * # password of the keystore (optional, defaults to empty)
 * keystore-passphrase = "password"
 * # alias of the certificate to get public key from (mandatory if public key is needed or public cert is needed)
 * cert-alias = "service_cert"
 * # alias of the key to sign request (mandatory if private key is needed)
 * key-alias = "myPrivateKey"
 * # password of the private key (usually the same as keystore - that's how openssl does it)
 * # also defaults to keystore-passphrase
 * key-passphrase = "password"
 * # certification chain - will add certificates from this cert chain
 * cert-chain = "alias1"
 * # path to PEM file with a private key. May be encrypted, though only with PCKS#8. To get the correct format (e.g. from
 * # openssl generated encrypted private key), use the following command:
 * # openssl pkcs8 -topk8 -in ./id_rsa -out ./id_rsa.p8
 * key-path = "path/to/private/key"
 * # path to PEM file with certificate chain (may contain more than one certificate)
 * cert-chain-path = "path/to/cert/chain/path"
 * </pre>
 */
public final class KeyConfig {
    private static final String DEFAULT_PRIVATE_KEY_ALIAS = "1";
    private static final Logger LOGGER = Logger.getLogger(KeyConfig.class.getName());
    private static final char[] EMPTY_CHARS = new char[0];

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final X509Certificate publicCert;
    private final List<X509Certificate> certChain = new LinkedList<>();
    private final List<X509Certificate> certificates = new LinkedList<>();

    private KeyConfig(PrivateKey privateKey,
                      PublicKey publicKey,
                      X509Certificate publicCert,
                      Collection<X509Certificate> certChain,
                      Collection<X509Certificate> certificates) {

        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.publicCert = publicCert;
        this.certChain.addAll(certChain);
        this.certificates.addAll(certificates);
    }

    /**
     * Load key config from config.
     *
     * @param config config instance located at keys configuration (expects "keystore-path" child)
     * @return KeyConfig loaded from config
     * @throws PkiException when keys or certificates fail to load from keystore or when misconfigured
     */
    public static KeyConfig create(Config config) throws PkiException {
        try {
            return fullBuilder().config(config).build();
        } catch (ResourceException e) {
            throw new PkiException("Failed to load from config", e);
        }
    }

    /**
     * Creates a new builder to configure instance.
     *
     * @return builder instance
     */
    public static Builder fullBuilder() {
        return new Builder();
    }

    /**
     * Build this instance from PEM files (usually a pair of private key and certificate chain).
     * Call {@link PemBuilder#build()} to build the instance.
     * If you need to add additional information to {@link KeyConfig}, use {@link PemBuilder#toFullBuilder()}.
     *
     * @return builder for PEM files
     */
    public static PemBuilder pemBuilder() {
        return new PemBuilder();
    }

    /**
     * Build this instance from a java keystore (such as PKCS12 keystore).
     * Call {@link KeystoreBuilder#build()} to build the instance.
     * If you need to add additional information to {@link KeyConfig}, use {@link PemBuilder#toFullBuilder()}.
     *
     * @return builder for Keystore
     */
    public static KeystoreBuilder keystoreBuilder() {
        return new KeystoreBuilder();
    }

    /**
     * The public key of this config if configured.
     *
     * @return the public key of this config or empty if not configured
     */
    public Optional<PublicKey> publicKey() {
        return Optional.ofNullable(publicKey);
    }

    /**
     * The private key of this config if configured.
     *
     * @return the private key of this config or empty if not configured
     */
    public Optional<PrivateKey> privateKey() {
        return Optional.ofNullable(privateKey);
    }

    /**
     * The public X.509 Certificate if configured.
     *
     * @return the public certificate of this config or empty if not configured
     */
    public Optional<X509Certificate> publicCert() {
        return Optional.ofNullable(publicCert);
    }

    /**
     * The X.509 Certificate Chain.
     *
     * @return the certificate chain or empty list if not configured
     */
    public List<X509Certificate> certChain() {
        return Collections.unmodifiableList(certChain);
    }

    /**
     * The X.509 Certificates.
     *
     * @return the certificates configured or empty list if none configured
     */
    public List<X509Certificate> certs() {
        return Collections.unmodifiableList(certificates);
    }

    /**
     * Fluent API builder for {@link KeyConfig}.
     * Call {@link #build()} to create an instance.
     *
     * The keys may be loaded from multiple possible sources.
     *
     * @see KeyConfig#keystoreBuilder()
     * @see KeyConfig#pemBuilder()
     * @see KeyConfig#fullBuilder()
     */
    @Configured
    public static class Builder implements io.helidon.common.Builder<KeyConfig> {
        private PrivateKey explicitPrivateKey;
        private PublicKey explicitPublicKey;
        private X509Certificate explicitPublicCert;
        private final List<X509Certificate> explicitCertChain = new LinkedList<>();
        private final List<X509Certificate> explicitCertificates = new LinkedList<>();

        /**
         * Build a new instance of the configuration based on this builder.
         *
         * @return instance from this builder
         * @throws PkiException when keys or certificates fail to load from keystore or when misconfigured
         */
        @Override
        public KeyConfig build() throws PkiException {
            PrivateKey privateKey = this.explicitPrivateKey;
            PublicKey publicKey = this.explicitPublicKey;
            X509Certificate publicCert = this.explicitPublicCert;
            List<X509Certificate> certChain = new LinkedList<>(explicitCertChain);
            List<X509Certificate> certificates = new LinkedList<>(explicitCertificates);

            // fix public key if cert is provided
            if (null == publicKey && null != publicCert) {
                publicKey = publicCert.getPublicKey();
            }

            return new KeyConfig(privateKey, publicKey, publicCert, certChain, certificates);
        }

        /**
         * Configure a private key instance (rather then keystore and alias).
         *
         * @param privateKey private key instance
         * @return updated builder instance
         */
        public Builder privateKey(PrivateKey privateKey) {
            this.explicitPrivateKey = privateKey;
            return this;
        }

        /**
         * Configure a public key instance (rather then keystore and certificate alias).
         *
         * @param publicKey private key instance
         * @return updated builder instance
         */
        public Builder publicKey(PublicKey publicKey) {
            this.explicitPublicKey = publicKey;
            return this;
        }

        /**
         * Configure an X.509 certificate instance for public key certificate.
         *
         * @param certificate certificate instance
         * @return updated builder instance
         */
        public Builder publicKeyCert(X509Certificate certificate) {
            this.explicitPublicCert = certificate;
            return this;
        }

        /**
         * Add an X.509 certificate instance to the end of certification chain.
         *
         * @param certificate certificate to add to certification path
         * @return updated builder instance
         */
        public Builder addCertChain(X509Certificate certificate) {
            this.explicitCertChain.add(certificate);
            return this;
        }

        /**
         * Add a certificate to the list of certificates, used e.g. in a trust store.
         *
         * @param certificate X.509 certificate to trust
         * @return updated builder instance
         */
        public Builder addCert(X509Certificate certificate) {
            this.explicitCertificates.add(certificate);
            return this;
        }

        /**
         * Update this builder with information from a pem builder.
         *
         * @param builder builder obtained from {@link KeyConfig#pemBuilder()}
         * @return updated builder instance
         */
        @ConfiguredOption(key = "pem")
        public Builder updateWith(PemBuilder builder) {
            builder.updateBuilder(this);
            return this;
        }

        /**
         * Update this builder with information from a keystore builder.
         *
         * @param builder builder obtained from {@link KeyConfig#keystoreBuilder()} ()}
         * @return updated builder instance
         */
        @ConfiguredOption(key = "keystore")
        public Builder updateWith(KeystoreBuilder builder) {
            builder.updateBuilder(this);
            return this;
        }

        /**
         * Updated this builder instance from configuration.
         * Keys configured will override existing fields in this builder, others will be left intact.
         * If certification path is already defined, configuration based cert-path will be added.
         *
         * @param config configuration to update this builder from
         * @return updated builder instance
         */
        public Builder config(Config config) {
            updateWith(pemBuilder().config(config));
            updateWith(keystoreBuilder().config(config));

            return this;
        }
    }

    /**
     * Builder for resources from a java keystore (PKCS12, JKS etc.). Obtain an instance through {@link
     * KeyConfig#keystoreBuilder()}.
     */
    @Configured(ignoreBuildMethod = true)
    public static final class KeystoreBuilder implements io.helidon.common.Builder<KeyConfig> {
        private static final String DEFAULT_KEYSTORE_TYPE = "PKCS12";

        private String keystoreType = DEFAULT_KEYSTORE_TYPE;
        private char[] keystorePassphrase = EMPTY_CHARS;
        private char[] keyPassphrase = null;
        private String keyAlias;
        private String certAlias;
        private String certChainAlias;
        private boolean addAllCertificates;
        private final List<String> certificateAliases = new LinkedList<>();
        private final StreamHolder keystoreStream = new StreamHolder("keystore");

        private KeystoreBuilder() {
        }

        /**
         * If you want to build a trust store, call this method to add all
         * certificates present in the keystore to certificate list.
         *
         * @return updated builder instance
         */
        @ConfiguredOption(type = Boolean.class, value = "false")
        public KeystoreBuilder trustStore() {
            return trustStore(true);
        }

        private KeystoreBuilder trustStore(boolean isTrustStore) {
            this.addAllCertificates = isTrustStore;
            return this;
        }

        /**
         * Add an alias to list of aliases used to generate a trusted set of certificates.
         *
         * @param alias alias of a certificate
         * @return updated builder instance
         */
        public KeystoreBuilder addCertAlias(String alias) {
            certificateAliases.add(alias);
            return this;
        }

        /**
         * Keystore resource definition.
         *
         * @param keystore keystore resource, from file path, classpath, URL etc.
         * @return updated builder instance
         */
        @ConfiguredOption(key = "resource", required = true)
        public KeystoreBuilder keystore(Resource keystore) {
            this.keystoreStream.stream(keystore);
            return this;
        }

        /**
         * Set type of keystore.
         * Defaults to "PKCS12", expected are other keystore types supported by java then can store keys under aliases.
         *
         * @param keystoreType keystore type to load the key
         * @return updated builder instance
         */
        @ConfiguredOption(key = "type", value = "PKCS12")
        public KeystoreBuilder keystoreType(String keystoreType) {
            this.keystoreType = keystoreType;
            return this;
        }

        /**
         * Pass-phrase of the keystore (supported with JKS and PKCS12 keystores).
         *
         * @param keystorePassphrase keystore pass-phrase
         * @return updated builder instance
         */
        public KeystoreBuilder keystorePassphrase(char[] keystorePassphrase) {
            this.keystorePassphrase = Arrays.copyOf(keystorePassphrase, keystorePassphrase.length);

            return this;
        }

        /**
         * Pass-phrase of the keystore (supported with JKS and PKCS12 keystores).
         *
         * @param keystorePassword keystore password to use, calls {@link #keystorePassphrase(char[])}
         * @return updated builder instance
         */
        @ConfiguredOption(key = "passphrase")
        public KeystoreBuilder keystorePassphrase(String keystorePassword) {
            return keystorePassphrase(keystorePassword.toCharArray());
        }

        /**
         * Alias of the private key in the keystore.
         *
         * @param keyAlias alias of the key in the keystore
         * @return updated builder instance
         */
        @ConfiguredOption(key = "key.alias", value = "1")
        public KeystoreBuilder keyAlias(String keyAlias) {
            this.keyAlias = keyAlias;
            return this;
        }

        /**
         * Alias of X.509 certificate of public key.
         * Used to load both the certificate and public key.
         *
         * @param alias alias under which the certificate is stored in the keystore
         * @return updated builder instance
         */
        @ConfiguredOption(key = "cert.alias")
        public KeystoreBuilder certAlias(String alias) {
            this.certAlias = alias;
            return this;
        }

        /**
         * Alias of an X.509 chain.
         *
         * @param alias alias of certificate chain in the keystore
         * @return updated builder instance
         */
        @ConfiguredOption(key = "cert-chain.alias")
        public KeystoreBuilder certChainAlias(String alias) {
            this.certChainAlias = alias;
            return this;
        }

        /**
         * Pass-phrase of the key in the keystore (used for private keys).
         * This is (by default) the same as keystore passphrase - only configure
         * if it differs from keystore passphrase.
         *
         * @param privateKeyPassphrase pass-phrase of the key
         * @return updated builder instance
         */
        public KeystoreBuilder keyPassphrase(char[] privateKeyPassphrase) {
            this.keyPassphrase = Arrays.copyOf(privateKeyPassphrase, privateKeyPassphrase.length);

            return this;
        }

        /**
         * Pass-phrase of the key in the keystore (used for private keys).
         * This is (by default) the same as keystore passphrase - only configure
         * if it differs from keystore passphrase.
         *
         * @param privateKeyPassphrase pass-phrase of the key
         * @return updated builder instance
         */
        @ConfiguredOption(key = "key.passphrase")
        public KeystoreBuilder keyPassphrase(String privateKeyPassphrase) {
            return keyPassphrase(privateKeyPassphrase.toCharArray());
        }

        /**
         * Create an instance of {@link KeyConfig} based on this builder.
         *
         * @return new key config based on a keystore
         */
        @Override
        public KeyConfig build() {
            return toFullBuilder().build();
        }

        /**
         * Create a builder for {@link KeyConfig} from this keystore builder. This allows you to enhance the config
         * with additional (explicit) fields.
         *
         * @return builder of {@link KeyConfig}
         */
        public Builder toFullBuilder() {
            return updateBuilder(KeyConfig.fullBuilder());
        }

        private Builder updateBuilder(Builder builder) {
            if (keystoreStream.isSet()) {
                if (null == keyPassphrase) {
                    keyPassphrase = keystorePassphrase;
                }
                KeyStore keyStore;

                try {
                    keyStore = PkiUtil.loadKeystore(keystoreType,
                                                    keystoreStream.stream(),
                                                    keystorePassphrase,
                                                    keystoreStream.message());
                } finally {
                    keystoreStream.closeStream();
                }

                // attempt to read private key
                boolean guessing = false;
                if (null == keyAlias) {
                    keyAlias = DEFAULT_PRIVATE_KEY_ALIAS;
                    guessing = true;
                }
                try {
                    builder.privateKey(PkiUtil.loadPrivateKey(keyStore, keyAlias, keyPassphrase));
                } catch (Exception e) {
                    if (guessing) {
                        LOGGER.log(Level.FINEST, "Failed to read private key from default alias", e);
                    } else {
                        throw e;
                    }
                }

                List<X509Certificate> certChain = null;
                if (null == certChainAlias) {
                    guessing = true;
                    // by default, cert chain uses the same alias as private key
                    certChainAlias = keyAlias;
                } else {
                    guessing = false;
                }

                if (null != certChainAlias) {
                    try {
                        certChain = PkiUtil.loadCertChain(keyStore, certChainAlias);
                        certChain.forEach(builder::addCertChain);
                    } catch (Exception e) {
                        if (guessing) {
                            LOGGER.log(Level.FINEST, "Failed to certificate chain from alias \"" + certChainAlias + "\"", e);
                        } else {
                            throw e;
                        }
                    }
                }

                if (null == certAlias) {
                    // no explicit public key certificate, just load it from cert chain if present
                    if (null != certChain && !certChain.isEmpty()) {
                        builder.publicKeyCert(certChain.get(0));
                    }
                } else {
                    builder.publicKeyCert(PkiUtil.loadCertificate(keyStore, certAlias));
                }

                if (addAllCertificates) {
                    PkiUtil.loadCertificates(keyStore).forEach(builder::addCert);
                } else {
                    certificateAliases.forEach(it -> builder.addCert(PkiUtil.loadCertificate(keyStore, it)));
                }
            }
            return builder;
        }

        /**
         * Update this builder from configuration.
         * The following keys are expected under key {@code keystore}:
         * <ul>
         * <li>{@code resource}: resource configuration as understood by {@link io.helidon.common.configurable.Resource}</li>
         * <li>{@code type}: type of keystore (defaults to PKCS12)</li>
         * <li>{@code passphrase}: passphrase of keystore, if required</li>
         * <li>{@code key.alias}: alias of private key, if wanted (defaults to "1")</li>
         * <li>{@code key.passphrase}: passphrase of private key if differs from keystore passphrase</li>
         * <li>{@code cert.alias}: alias of public certificate (to obtain public key)</li>
         * <li>{@code cert-chain.alias}: alias of certificate chain</li>
         * <li>{@code trust-store}: true if this is a trust store (and we should load all certificates from it), defaults to false</li>
         * </ul>
         *
         * @param config configuration instance
         * @return updated builder instance
         */
        public KeystoreBuilder config(Config config) {
            Config keystoreConfig = config.get("keystore");

            // the actual resource (file, classpath) with the bytes of the keystore
            keystoreConfig.get("resource").as(Resource::create).ifPresent(this::keystore);
            // this is the old, deprecated approach to have backward compatibility with configuration
            // if configured this way, a warning is logged
            Resource.create(config, "keystore").ifPresent(this::keystore);

            // all these settings are moved to keystore key

            // type of keystore
            DeprecatedConfig.get(config, "keystore.type", "keystore-type")
                    .asString()
                    .ifPresent(this::keystoreType);
            // password of the keystore
            DeprecatedConfig.get(config, "keystore.passphrase", "keystore-passphrase")
                    .asString()
                    .map(String::toCharArray)
                    .ifPresent(this::keystorePassphrase);
            // private key alias
            DeprecatedConfig.get(config, "keystore.key.alias", "key-alias")
                    .asString()
                    .ifPresent(this::keyAlias);
            // private key password
            DeprecatedConfig.get(config, "keystore.key.passphrase", "key-passphrase")
                    .asString()
                    .map(String::toCharArray)
                    .ifPresent(this::keyPassphrase);
            DeprecatedConfig.get(config, "keystore.cert.alias", "cert-alias")
                    .asString()
                    .ifPresent(this::certAlias);
            DeprecatedConfig.get(config, "keystore.cert-chain.alias", "cert-chain")
                    .asString()
                    .ifPresent(this::certChainAlias);
            // whether this is a keystore (with a private key) or a trust store (just trusted public keys/certificates)
            DeprecatedConfig.get(config, "keystore.trust-store", "trust-store")
                    .asBoolean()
                    .ifPresent(this::trustStore);

            return this;
        }
    }

    /**
     * Builder for PEM files - accepts private key and certificate chain. Obtain an instance through {@link
     * KeyConfig#pemBuilder()}.
     *
     * If you have "standard" linux/unix private key, you must run "
     * {@code openssl pkcs8 -topk8 -in ./id_rsa -out ./id_rsa.p8}" on it to work with this builder for password protected
     * file; or "{@code openssl pkcs8 -topk8 -in ./id_rsa -out ./id_rsa_nocrypt.p8 -nocrypt}" for unprotected file.
     *
     * The only supported format is PKCS#8. If you have a different format, you must to transform it to PKCS8 PEM format (to
     * use this builder), or to PKCS#12 keystore format (and use {@link KeystoreBuilder}).
     */
    @Configured(ignoreBuildMethod = true)
    public static final class PemBuilder implements io.helidon.common.Builder<KeyConfig> {
        private final StreamHolder privateKeyStream = new StreamHolder("privateKey");
        private final StreamHolder publicKeyStream = new StreamHolder("publicKey");
        private final StreamHolder certChainStream = new StreamHolder("certChain");
        private char[] pemKeyPassphrase;

        private PemBuilder() {
        }

        /**
         * Read a private key from PEM format from a resource definition.
         *
         * @param resource key resource (file, classpath, URL etc.)
         * @return updated builder instance
         */
        @ConfiguredOption(key = "key.resource")
        public PemBuilder key(Resource resource) {
            privateKeyStream.stream(resource);
            return this;
        }

        /**
         * Read a public key from PEM format from a resource definition.
         *
         * @param resource key resource (file, classpath, URL etc.)
         * @return updated builder instance
         */
        public PemBuilder publicKey(Resource resource) {
            publicKeyStream.stream(resource);
            return this;
        }

        /**
         * Passphrase for private key. If the key is encrypted (and in PEM PKCS#8 format), this passphrase will be used to
         * decrypt it.
         *
         * @param passphrase passphrase used to encrypt the private key
         * @return updated builder instance
         */
        public PemBuilder keyPassphrase(char[] passphrase) {
            this.pemKeyPassphrase = Arrays.copyOf(passphrase, passphrase.length);

            return this;
        }

        /**
         * Passphrase for private key. If the key is encrypted (and in PEM PKCS#8 format), this passphrase will be used to
         * decrypt it.
         *
         * @param passphrase passphrase used to encrypt the private key
         * @return updated builder instance
         */
        @ConfiguredOption(key = "key.passphrase")
        public PemBuilder keyPassphrase(String passphrase) {
            return keyPassphrase(passphrase.toCharArray());
        }

        /**
         * Load certificate chain from PEM resource.
         *
         * @param resource resource (e.g. classpath, file path, URL etc.)
         * @return updated builder instance
         */
        @ConfiguredOption(key = "cert-chain.resource")
        public PemBuilder certChain(Resource resource) {
            certChainStream.stream(resource);
            return this;
        }

        /**
         * Build {@link KeyConfig} based on information from PEM files only.
         *
         * @return new instance configured from this builder
         */
        @Override
        public KeyConfig build() {
            return toFullBuilder().build();
        }

        /**
         * Get a builder filled from this builder to add additional information (such as public key from certificate etc.).
         *
         * @return builder for {@link KeyConfig}
         */
        public Builder toFullBuilder() {
            return updateBuilder(KeyConfig.fullBuilder());
        }

        private Builder updateBuilder(Builder builder) {
            if (privateKeyStream.isSet()) {
                builder.privateKey(PemReader.readPrivateKey(privateKeyStream.stream(), pemKeyPassphrase));
            }
            if (publicKeyStream.isSet()) {
                builder.publicKey(PemReader.readPublicKey(publicKeyStream.stream()));
            }

            if (certChainStream.isSet()) {
                List<X509Certificate> chain = PemReader.readCertificates(certChainStream.stream());
                chain.forEach(builder::addCertChain);
                if (!chain.isEmpty()) {
                    builder.publicKeyCert(chain.get(0));
                }
            }

            return builder;
        }

        /**
         * Update this builder from configuration.
         * Expected keys:
         * <ul>
         * <li>pem-key-path - path to PEM private key file (PKCS#8 format)</li>
         * <li>pem-key-resource-path - path to resource on classpath </li>
         * <li>pem-key-passphrase - passphrase of private key if encrypted</li>
         * <li>pem-cert-chain-path - path to certificate chain PEM file</li>
         * <li>pem-cert-chain-resource-path - path to resource on classpath</li>
         * </ul>
         *
         * @param config configuration to update builder from
         * @return updated builder instance
         */
        public PemBuilder config(Config config) {
            Config pemConfig = config.get("pem");
            // this is the new approach
            pemConfig.get("key.resource").as(Resource::create).ifPresent(this::key);
            pemConfig.get("key.passphrase").asString().map(String::toCharArray).ifPresent(this::keyPassphrase);
            pemConfig.get("cert-chain.resource").as(Resource::create).ifPresent(this::certChain);

            // and this is the old approach
            Resource.create(config, "pem-key").ifPresent(this::key);
            config.get("pem-key-passphrase").asString().map(String::toCharArray).ifPresent(this::keyPassphrase);
            Resource.create(config, "pem-cert-chain").ifPresent(this::certChain);

            return this;
        }
    }

    private static final class StreamHolder {
        private final String baseMessage;
        private InputStream inputStream;
        private String message;

        private StreamHolder(String message) {
            this.baseMessage = message;
            this.message = message;
        }

        private boolean isSet() {
            return inputStream != null;
        }

        private void stream(Resource resource) {
            closeStream();
            Objects.requireNonNull(resource, "Resource for \"" + message + "\" must not be null");

            this.inputStream = resource.stream();
            this.message = message + ":" + resource.sourceType() + ":" + resource.location();
        }

        private InputStream stream() {
            return inputStream;
        }

        private String message() {
            return message;
        }

        private void closeStream() {
            if (null != inputStream) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to close input stream: " + message, e);
                }
            }
            message = baseMessage;
        }
    }
}

