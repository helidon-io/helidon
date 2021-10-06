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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.helidon.config.Config;
import io.helidon.health.common.BuiltInHealthCheck;
import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.vault.GetVault;
import io.helidon.integrations.oci.vault.OciVault;
import io.helidon.integrations.oci.vault.OciVaultRx;

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

    private final List<String> vaultIds;
    private final OciVault ociVault;

    @Inject
    OciVaultHealthCheck(@ConfigProperty(name = "oci.vault.healthchecks") List<String> vaultIds,
                        OciVault ociVault) {
        this.vaultIds = vaultIds;
        this.ociVault = ociVault;
    }

    private OciVaultHealthCheck(Builder builder) {
        this.vaultIds = builder.vaultIds;
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

        boolean status = true;
        for (String vaultId : vaultIds) {
            try {
                ApiOptionalResponse<GetVault.Response> r =  ociVault.getVault(GetVault.Request.builder().vaultId(vaultId));
                LOGGER.fine(() -> "OCI vault health check " + vaultId + " returned status code " + r.status().code());
                r.entity().ifPresentOrElse(e -> {
                    String id = e.displayName() != null && !e.displayName().isEmpty() ? e.displayName() : vaultId;
                    builder.withData(id, r.status().code());
                }, () -> builder.withData(vaultId, r.status().code()));
                status = status && r.status().equals(OK_200);
            } catch (Throwable t) {
                LOGGER.fine(() -> "OCI vault health check " + vaultId + " exception " + t.getMessage());
                status = false;
                builder.withData(vaultId, t.getMessage());
            }
        }

        builder.state(status);
        return builder.build();
    }

    /**
     * Fluent API builder for {@link OciVaultHealthCheck}.
     */
    public static final class Builder implements io.helidon.common.Builder<OciVaultHealthCheck> {

        private OciVaultRx vaultRx;
        private OciVault vault;
        private final List<String> vaultIds = new ArrayList<>();

        private Builder() {
        }

        @Override
        public OciVaultHealthCheck build() {
            if (vaultRx == null) {
                vaultRx = OciVaultRx.create();
            }
            this.vault = OciVault.create(vaultRx);
            return new OciVaultHealthCheck(this);
        }

        /**
         * Set up this builder using config.
         *
         * @param config the config.
         * @return the builder.
         */
        public Builder config(Config config) {
            config.get("oci.vault.healthchecks").asList(String.class).ifPresent(this.vaultIds::addAll);
            return this;
        }

        /**
         * Set the vault's OCID.
         *
         * @param vaultId vault ID.
         * @return the builder.
         */
        public Builder addVaultId(String vaultId) {
            this.vaultIds.add(vaultId);
            return this;
        }

        /**
         * Set the vault client.
         *
         * @param vaultRx vault client.
         * @return the builder.
         */
        public Builder ociVault(OciVaultRx vaultRx) {
            this.vaultRx = vaultRx;
            return this;
        }
    }
}
