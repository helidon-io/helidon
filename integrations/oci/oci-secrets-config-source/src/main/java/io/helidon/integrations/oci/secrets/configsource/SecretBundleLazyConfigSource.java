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
package io.helidon.integrations.oci.secrets.configsource;

import java.lang.System.Logger;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.config.AbstractConfigSource;
import io.helidon.config.AbstractConfigSourceBuilder;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ValueNode;
import io.helidon.config.spi.LazyConfigSource;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.secrets.Secrets;
import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.requests.GetSecretBundleByNameRequest;

import static io.helidon.integrations.oci.sdk.runtime.OciExtension.ociAuthenticationProvider;
import static java.lang.System.Logger.Level.WARNING;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * An {@link AbstractConfigSource} and a {@link LazyConfigSource} implementation that sources its values from the Oracle
 * Cloud Infrastructure (OCI) <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/secrets/package-summary.html">Secrets
 * Retrieval</a> and <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/vault/package-summary.html">Vault</a> APIs.
 */
public final class SecretBundleLazyConfigSource extends AbstractConfigSource implements LazyConfigSource {


    /*
     * Static fields.
     */


    private static final Logger LOGGER = System.getLogger(SecretBundleLazyConfigSource.class.getName());

    private static final String VAULT_OCID_PROPERTY_NAME = "vault-ocid";


    /*
     * Instance fields.
     */


    private final Function<? super String, ? extends Optional<ConfigNode>> nodeFunction;


    /*
     * Constructors.
     */


    private SecretBundleLazyConfigSource(Builder b) {
        super(b);
        if (b.vaultOcid == null) {
            this.nodeFunction = secretName -> Optional.empty();
        } else {
            LazyValue<? extends Secrets> secretsSupplier = LazyValue.create(b.secretsSupplier::get);
            this.nodeFunction = secretName -> node(secretsSupplier, b.vaultOcid, secretName);
        }
    }


    /*
     * Instance methods.
     */


    @Deprecated // For use by the Helidon Config subsystem only.
    @Override // NodeConfigSource
    public Optional<ConfigNode> node(String key) {
        return this.nodeFunction.apply(key);
    }


    /*
     * Static methods.
     */


    /**
     * Creates and returns a new {@link Builder} for {@linkplain Builder#build() building} {@link
     * SecretBundleConfigSource} instances.
     *
     * @return a new {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    private static Optional<ConfigNode> node(LazyValue<? extends Secrets> secretsSupplier, String vaultOcid, String secretName) {
        Secrets s = secretsSupplier.get();
        return node(() -> s.getSecretBundleByName(request(vaultOcid, secretName)).getSecretBundle().getSecretBundleContent());
    }

    private static Optional<ConfigNode> node(Supplier<?> secretBundleContentDetailsSupplier) {
        Object secretBundleContentDetails = secretBundleContentDetailsSupplier.get();
        if (secretBundleContentDetails instanceof Base64SecretBundleContentDetails base64SecretBundleContentDetails) {
            return Optional.of(valueNode(base64SecretBundleContentDetails.getContent(), Base64.getDecoder()));
        }
        return Optional.empty();
    }

    private static GetSecretBundleByNameRequest request(String vaultOcid, String secretName) {
        return GetSecretBundleByNameRequest.builder()
            .vaultId(vaultOcid)
            .secretName(secretName)
            .build();
    }

    static ValueNode valueNode(String base64EncodedContent, Base64.Decoder base64Decoder) {
        String decodedContent = new String(base64Decoder.decode(base64EncodedContent), UTF_8);
        return ValueNode.create(decodedContent.intern());
    }


    /*
     * Inner and nested classes.
     */


    /**
     * An {@link AbstractConfigSourceBuilder} that {@linkplain #build() builds} {@link SecretBundleConfigSource}
     * instances.
     */
    public static final class Builder extends AbstractConfigSourceBuilder<Builder, Void> {


        /*
         * Instance fields.
         */


        private Supplier<? extends Secrets> secretsSupplier;

        private String vaultOcid;


        /*
         * Constructors.
         */


        private Builder() {
            super();
            Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier =
                LazyValue.create(() -> (BasicAuthenticationDetailsProvider) ociAuthenticationProvider().get());
            SecretsClient.Builder scb = SecretsClient.builder();
            this.secretsSupplier = () -> scb.build(adpSupplier.get());
        }


        /*
         * Instance methods.
         */


        /**
         * Creates and returns a new {@link SecretBundleConfigSource} instance initialized from the state of this {@link
         * Builder}.
         *
         * @return a new {@link SecretBundleConfigSource}
         */
        public SecretBundleLazyConfigSource build() {
            return new SecretBundleLazyConfigSource(this);
        }

        /**
         * Configures this {@link Builder} from the supplied meta-configuration.
         *
         * @param metaConfig the meta-configuration; must not be {@code null}
         *
         * @return this {@link Builder}
         *
         * @exception NullPointerException if {@code metaConfig} is {@code null}
         */
        @Override // AbstractConfigSourceBuilder<Builder, Void>
        public Builder config(Config metaConfig) {
            metaConfig.get("change-watcher")
                .asNode()
                .ifPresent(n -> {
                        throw new ConfigException("Invalid meta-configuration key: change-watcher: "
                                                  + "Change watching is not supported by "
                                                  + this.getClass().getName() + " instances");
                    });
            metaConfig.get("polling-strategy")
                .asNode()
                .ifPresent(n -> {
                        throw new ConfigException("Invalid meta-configuration key: polling-strategy: "
                                                  + "Polling is not supported by "
                                                  + this.getClass().getName() + " instances");
                    });
            metaConfig.get("vault-ocid")
                .asString()
                .filter(Predicate.not(String::isBlank))
                .ifPresentOrElse(this::vaultOcid,
                                 () -> {
                                     if (LOGGER.isLoggable(WARNING)) {
                                         LOGGER.log(WARNING,
                                                    "No meta-configuration value supplied for "
                                                    + metaConfig.key().toString() + "." + VAULT_OCID_PROPERTY_NAME
                                                    + "); resulting ConfigSource will be empty");
                                     }
                                 });
            return super.config(metaConfig);
        }

        /**
         * Uses the supplied {@link Supplier} of {@link Secrets} instances, instead of the default one, for
         * communicating with the OCI Secrets Retrieval API.
         *
         * @param secretsSupplier the non-default {@link Supplier} to use; must not be {@code null}
         *
         * @return this {@link Builder}
         *
         * @exception NullPointerException if {@code secretsSupplier} is {@code null}
         */
        public Builder secretsSupplier(Supplier<? extends Secrets> secretsSupplier) {
            this.secretsSupplier = Objects.requireNonNull(secretsSupplier, "secretsSupplier");
            return this;
        }

        /**
         * Sets the (required) OCID of the OCI vault from which a {@link SecretBundleConfigSource} will retrieve values.
         *
         * @param vaultOcid a valid OCID identifying an OCI vault; must not be {@code null}
         *
         * @return this {@link Builder}
         *
         * @exception NullPointerException if {@code vaultId} is {@code null}
         */
        public Builder vaultOcid(String vaultOcid) {
            this.vaultOcid = Objects.requireNonNull(vaultOcid, "vaultOcid");
            return this;
        }

    }

}
