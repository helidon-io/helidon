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

package io.helidon.integrations.vault.auths.k8s;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.Logger;

import javax.annotation.Priority;

import io.helidon.config.Config;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.auths.common.NoVaultAuth;
import io.helidon.integrations.vault.spi.VaultAuth;

/**
 * Vault authentication for Kubernetes (k8s).
 */
@Priority(2000)
public class K8sVaultAuth implements VaultAuth {
    private static final Logger LOGGER = Logger.getLogger(K8sVaultAuth.class.getName());

    private final String serviceAccountToken;
    private final String tokenRole;

    /**
     * Constructor required for Java Service Loader.
     *
     * @deprecated please use {@link #builder()}
     */
    @Deprecated
    public K8sVaultAuth() {
        this.serviceAccountToken = null;
        this.tokenRole = null;
    }

    private K8sVaultAuth(Builder builder) {
        this.serviceAccountToken = builder.serviceAccountToken;
        this.tokenRole = builder.tokenRole;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Optional<RestApi> authenticate(Config config, Vault.Builder vaultBuilder) {
        boolean enabled = config.get("auth.k8s.enabled").asBoolean().orElse(true);

        if (!enabled) {
            return Optional.empty();
        }

        String jwtToken;
        if (this.serviceAccountToken == null) {
            Optional<String> maybeToken = config.get("auth.k8s.service-account-token").asString()
                    .or(() -> {
                        Path tokenPath = Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/token");
                        if (!Files.exists(tokenPath)) {
                            return Optional.empty();
                        }
                        try {
                            return Optional.of(Files.readString(tokenPath));
                        } catch (IOException e) {
                            throw new VaultApiException("Failed to read token from " + tokenPath.toAbsolutePath(), e);
                        }
                    });

            if (maybeToken.isEmpty()) {
                return Optional.empty();
            }
            jwtToken = maybeToken.get();
        } else {
            jwtToken = serviceAccountToken;
        }

        String roleName = Optional.ofNullable(this.tokenRole)
                .or(() -> config.get("auth.k8s.token-role")
                        .asString()
                        .asOptional())
                .orElseThrow(() -> new VaultApiException("Token role must be defined when using Kubernetes vault "
                                                                 + "authentication."));

        // this may be changed in the future, when running with a sidecar (there should be a way to get the address from evn)
        String address = vaultBuilder.address()
                .orElseThrow(() -> new VaultApiException("Address is required when using k8s authentication"));

        Vault.Builder loginVaultBuilder = Vault.builder()
                // explicitly use default
                .address(address)
                .disableVaultAuthDiscovery()
                .faultTolerance(vaultBuilder.ftHandler())
                .updateWebClient(it -> vaultBuilder.webClientUpdater().accept(it))
                .addVaultAuth(NoVaultAuth.create());

        vaultBuilder.baseNamespace().ifPresent(loginVaultBuilder::baseNamespace);

        Vault loginVault = loginVaultBuilder.build();

        LOGGER.info("Authenticated Vault " + address + " using k8s, role \"" + roleName + "\"");
        return Optional.of(K8sRestApi.k8sBuilder()
                                   .webClientBuilder(webclient -> {
                                       webclient.baseUri(address + "/v1");
                                       vaultBuilder.baseNamespace()
                                               .ifPresent(ns -> webclient.addHeader("X-Vault-Namespace", ns));
                                       vaultBuilder.webClientUpdater().accept(webclient);
                                   })
                                   .faultTolerance(vaultBuilder.ftHandler())
                                   .auth(loginVault.auth(K8sAuth.AUTH_METHOD))
                                   .roleName(roleName)
                                   .jwtToken(jwtToken)
                                   .build());
    }

    public static class Builder implements io.helidon.common.Builder<K8sVaultAuth> {
        private String serviceAccountToken;
        private String tokenRole;

        private Builder() {
        }

        @Override
        public K8sVaultAuth build() {
            return new K8sVaultAuth(this);
        }

        public Builder serviceAccountToken(String serviceAccountToken) {
            this.serviceAccountToken = serviceAccountToken;
            return this;
        }

        public Builder tokenRole(String tokenRole) {
            this.tokenRole = tokenRole;
            return this;
        }
    }
}
