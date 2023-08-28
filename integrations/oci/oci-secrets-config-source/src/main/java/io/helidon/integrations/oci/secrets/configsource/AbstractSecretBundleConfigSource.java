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
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.config.AbstractConfigSource;
import io.helidon.config.AbstractConfigSourceBuilder;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.spi.ConfigNode.ValueNode;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.secrets.Secrets;
import com.oracle.bmc.secrets.SecretsClient;

import static io.helidon.integrations.oci.sdk.runtime.OciExtension.ociAuthenticationProvider;
import static java.lang.System.Logger.Level.WARNING;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * An {@link AbstractConfigSource} that encapsulates functionality common to both {@link SecretBundleLazyConfigSource}
 * and {@link SecretBundleNodeConfigSource}.
 *
 * @param <B> the type of {@link AbstractConfigSourceBuilder} subclass used to build instances of this class
 *
 * @see SecretBundleLazyConfigSource
 *
 * @see SecretBundleNodeConfigSource
 */
public abstract sealed class AbstractSecretBundleConfigSource<B extends AbstractConfigSourceBuilder<B, Void>>
    extends AbstractConfigSource
    permits SecretBundleLazyConfigSource, SecretBundleNodeConfigSource {

    private static final Logger LOGGER = System.getLogger(AbstractSecretBundleConfigSource.class.getName());

    static final String VAULT_OCID_PROPERTY_NAME = "vault-ocid";

    /**
     * Creates a new {@link AbstractSecretBundleConfigSource}.
     *
     * @param b a builder
     */
    protected AbstractSecretBundleConfigSource(B b) {
        super(b);
    }

    static ValueNode valueNode(String base64EncodedContent, Base64.Decoder base64Decoder) {
        String decodedContent = new String(base64Decoder.decode(base64EncodedContent), UTF_8);
        return ValueNode.create(decodedContent.intern());
    }

    /**
     * An {@link AbstractConfigSourceBuilder} used to build instances of {@link AbstractSecretBundleConfigSource}.
     *
     * @param <B> the builder subclass
     */
    public abstract static sealed class Builder<B extends AbstractConfigSourceBuilder<B, Void>>
        extends AbstractConfigSourceBuilder<B, Void>
        permits SecretBundleLazyConfigSource.Builder, SecretBundleNodeConfigSource.Builder {

        private Supplier<? extends Secrets> secretsSupplier;

        private String vaultOcid;

        /**
         * Creates a new {@link Builder}.
         */
        protected Builder() {
            super();
            SecretsClient.Builder scb = SecretsClient.builder();
            this.secretsSupplier = () -> scb.build(adpSupplier().get());
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
        public B config(Config metaConfig) {
            metaConfig.get("change-watcher")
                .asNode()
                .ifPresent(n -> {
                        throw new ConfigException("Invalid meta-configuration key: change-watcher: "
                                                  + "Change watching is not supported by "
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
         * Sets the (required) OCID of the OCI vault from which an {@link AbstractSecretBundleConfigSource} will
         * retrieve values.
         *
         * @param vaultOcid a valid OCID identifying an OCI vault; must not be {@code null}
         *
         * @return this {@link Builder}
         *
         * @exception NullPointerException if {@code vaultId} is {@code null}
         */
        @SuppressWarnings("unchecked")
        public B vaultOcid(String vaultOcid) {
            this.vaultOcid = Objects.requireNonNull(vaultOcid, "vaultOcid");
            return (B) this;
        }

        String vaultOcid() {
            return this.vaultOcid;
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
        @SuppressWarnings("unchecked")
        public B secretsSupplier(Supplier<? extends Secrets> secretsSupplier) {
            this.secretsSupplier = Objects.requireNonNull(secretsSupplier, "secretsSupplier");
            return (B) this;
        }

        Supplier<? extends Secrets> secretsSupplier() {
            return this.secretsSupplier;
        }

        static LazyValue<? extends BasicAuthenticationDetailsProvider> adpSupplier() {
            return LazyValue.create(() -> (BasicAuthenticationDetailsProvider) ociAuthenticationProvider().get());
        }

    }

}
