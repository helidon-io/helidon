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
 */

package io.helidon.integrations.oci.vault.health;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Objects;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.health.common.BuiltInHealthCheck;
import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.vault.GetVault;
import io.helidon.integrations.oci.vault.OciVault;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;

import static io.helidon.common.http.Http.Status.OK_200;

/**
 * Liveness check for an OCI's Vault. Reads OCI properties from '~/.oci/config'.
 */
@Liveness
@ApplicationScoped  // this will be ignored if not within CDI
@BuiltInHealthCheck
public final class OciVaultHealthCheck implements HealthCheck {
    private static final Logger LOGGER = Logger.getLogger(OciVaultHealthCheck.class.getName());

    private final String vaultId;
    private final OciVault ociVault;

    @Inject
    OciVaultHealthCheck(@ConfigProperty(name = "oci.vault.vault-ocid") String vaultId,
                        OciVault ociVault) {
        this.vaultId = vaultId;
        this.ociVault = ociVault;
    }

    private OciVaultHealthCheck(Builder builder) {
        this.vaultId = builder.vaultId;
        this.ociVault = builder.vault;
    }

    /**
     * Create a new fluent API builder to configure a new health check.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create an instance.
     *
     * @param config the config.
     * @return an instance.
     */
    public static OciVaultHealthCheck create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Checks that the OCI vault is accessible, if defined. Will report error only if not
     * defined or not accessible. Can block since all health checks are called asynchronously.
     *
     * @return a response
     */
    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("vault");
        try {
            ApiOptionalResponse<GetVault.Response> r =  ociVault.getVault(GetVault.Request.builder().vaultId(vaultId));
            builder.state(r.status().equals(OK_200));
            LOGGER.fine(() -> "OCI vault health check " + vaultId + " returned status code " + r.status().code());
        } catch (Throwable t) {
            builder.down()
                    .withData("Error", t.getClass().getName())
                    .withData("Message", t.getMessage());
            LOGGER.fine(() -> "OCI vault health check " + vaultId + " exception " + t.getMessage());
        } finally {
            builder.withData("vaultId", vaultId);
        }
        return builder.build();
    }

    /**
     * Fluent API builder for {@link OciVaultHealthCheck}.
     */
    public static final class Builder implements io.helidon.common.Builder<OciVaultHealthCheck> {

        private String vaultId;
        private OciVault vault;

        private Builder() {
        }

        @Override
        public OciVaultHealthCheck build() {
            Objects.requireNonNull(vaultId);
            Objects.requireNonNull(vault);
            return new OciVaultHealthCheck(this);
        }

        /**
         * Set up this builder using config.
         *
         * @param config the config.
         * @return the builder.
         */
        public Builder config(Config config) {
            config.get("oci.vault.vault-ocid").asString().ifPresent(this::vaultId);
            return this;
        }

        /**
         * Set the vault's OCID.
         *
         * @param vaultId vault ID.
         * @return the builder.
         */
        public Builder vaultId(String vaultId) {
            this.vaultId = vaultId;
            return this;
        }

        /**
         * Set the vault client.
         *
         * @param vault vault client.
         * @return the builder.
         */
        public Builder ociVault(OciVault vault) {
            this.vault = vault;
            return this;
        }
    }
}
