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

package io.helidon.integrations.vault.auths.approle;

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
 * Vault authentication for AppRole.
 */
@Priority(1000)
public class AppRoleVaultAuth implements VaultAuth {
    private static final Logger LOGGER = Logger.getLogger(AppRoleVaultAuth.class.getName());

    private final String appRoleId;
    private final String secretId;

    /**
     * Constructor required for Java Service Loader.
     *
     * @deprecated please use {@link #builder()}
     */
    @Deprecated
    public AppRoleVaultAuth() {
        this.appRoleId = null;
        this.secretId = null;
    }

    private AppRoleVaultAuth(Builder builder) {
        this.appRoleId = builder.appRoleId;
        this.secretId = builder.secretId;
    }

    /**
     * Create a new builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Optional<RestApi> authenticate(Config config, Vault.Builder vaultBuilder) {
        boolean enabled = config.get("auth.app-role.enabled").asBoolean().orElse(true);

        if (!enabled) {
            return Optional.empty();
        }
        Optional<String> maybeAppRoleId = Optional.ofNullable(this.appRoleId)
                .or(() -> config.get("auth.app-role.role-id")
                        .asString()
                        .asOptional());

        if (maybeAppRoleId.isEmpty()) {
            LOGGER.fine("AppRole vault authentication not used, as app-role.role-id is not defined");
            return Optional.empty();
        }

        String appRoleId = maybeAppRoleId.get();
        String secretId = Optional.ofNullable(this.secretId)
                .or(() -> config.get("auth.app-role.secret-id")
                        .asString()
                        .asOptional())
                .orElseThrow(() -> new VaultApiException("AppRole ID is defined (" + appRoleId + "), but secret id is not. "
                                                                 + "Cannot "
                                                                 + "authenticate."));

        LOGGER.finest("Will try to login to Vault using app role id: " + appRoleId + " and a secret id.");

        // this may be changed in the future, when running with a sidecar (there should be a way to get the address from evn)
        String address = vaultBuilder.address()
                .orElseThrow(() -> new VaultApiException("Address is required when using k8s authentication"));

        Vault.Builder loginVaultBuilder = Vault.builder()
                // explicitly use default
                .address(address)
                .disableVaultAuthDiscovery()
                .updateWebClient(vaultBuilder.webClientUpdater())
                .faultTolerance(vaultBuilder.ftHandler())
                .addVaultAuth(NoVaultAuth.create());

        vaultBuilder.baseNamespace().ifPresent(loginVaultBuilder::baseNamespace);

        Vault loginVault = loginVaultBuilder.build();

        AppRoleAuthRx auth = loginVault.auth(AppRoleAuthRx.AUTH_METHOD);

        LOGGER.info("Authenticated Vault " + address + " using AppRole, roleId \"" + appRoleId + "\"");
        return Optional.of(AppRoleRestApi.appRoleBuilder()
                                   .webClientBuilder(webclient -> {
                                       webclient.baseUri(address + "/v1");
                                       vaultBuilder.baseNamespace()
                                               .ifPresent(ns -> webclient.addHeader("X-Vault-Namespace", ns));
                                       vaultBuilder.webClientUpdater().accept(webclient);
                                   })
                                   .faultTolerance(vaultBuilder.ftHandler())
                                   .auth(auth)
                                   .appRoleId(appRoleId)
                                   .secretId(secretId)
                                   .build());

    }

    /**
     * Fluent API builder for {@link io.helidon.integrations.vault.auths.approle.AppRoleVaultAuth}.
     */
    public static class Builder implements io.helidon.common.Builder<AppRoleVaultAuth> {
        private String appRoleId;
        private String secretId;

        private Builder() {
        }

        @Override
        public AppRoleVaultAuth build() {
            return new AppRoleVaultAuth(this);
        }

        /**
         * ID of the AppRole.
         *
         * @param appRoleId AppRole ID
         * @return updated builder
         */
        public Builder appRoleId(String appRoleId) {
            this.appRoleId = appRoleId;
            return this;
        }

        /**
         * Secret ID generated for the AppRole.
         *
         * @param secretId secret ID
         * @return updated builder
         */
        public Builder secretId(String secretId) {
            this.secretId = secretId;
            return this;
        }
    }

}
