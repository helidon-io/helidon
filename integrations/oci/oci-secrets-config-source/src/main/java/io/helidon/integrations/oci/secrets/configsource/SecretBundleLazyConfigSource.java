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
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.config.AbstractConfigSource;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.LazyConfigSource;

import com.oracle.bmc.secrets.Secrets;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.requests.GetSecretBundleByNameRequest;

/**
 * An {@link AbstractConfigSource} and a {@link LazyConfigSource} implementation that sources its values from the Oracle
 * Cloud Infrastructure (OCI) <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/secrets/package-summary.html">Secrets
 * Retrieval</a> and <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/vault/package-summary.html">Vault</a> APIs.
 */
public final class SecretBundleLazyConfigSource
    extends AbstractSecretBundleConfigSource<SecretBundleLazyConfigSource.Builder>
    implements LazyConfigSource {


    /*
     * Static fields.
     */


    private static final Logger LOGGER = System.getLogger(SecretBundleLazyConfigSource.class.getName());


    /*
     * Instance fields.
     */


    private final Function<? super String, ? extends Optional<ConfigNode>> nodeFunction;


    /*
     * Constructors.
     */


    private SecretBundleLazyConfigSource(Builder b) {
        super(b);
        String vaultOcid = b.vaultOcid();
        if (vaultOcid == null) {
            this.nodeFunction = secretName -> Optional.empty();
        } else {
            LazyValue<? extends Secrets> secretsSupplier = LazyValue.create(b.secretsSupplier()::get);
            this.nodeFunction = secretName -> node(secretsSupplier, vaultOcid, secretName);
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
     * SecretBundleLazyConfigSource} instances.
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


    /*
     * Inner and nested classes.
     */


    /**
     * An {@link AbstractSecretBundleConfigSource.Builder} that {@linkplain #build() builds} {@link
     * SecretBundleLazyConfigSource} instances.
     */
    public static final class Builder extends AbstractSecretBundleConfigSource.Builder<Builder> {


        /*
         * Constructors.
         */


        private Builder() {
            super();
        }


        /*
         * Instance methods.
         */


        /**
         * Creates and returns a new {@link SecretBundleLazyConfigSource} instance initialized from the state of this
         * {@link Builder}.
         *
         * @return a new {@link SecretBundleLazyConfigSource}
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
        @Override // AbstractSecretBundleConfigSource.Builder<Builder, Void>
        public Builder config(Config metaConfig) {
            metaConfig.get("polling-strategy")
                .asNode()
                .ifPresent(n -> {
                        throw new ConfigException("Invalid meta-configuration key: polling-strategy: "
                                                  + "Polling is not supported by "
                                                  + this.getClass().getName() + " instances");
                    });
            return super.config(metaConfig);
        }

    }

}
