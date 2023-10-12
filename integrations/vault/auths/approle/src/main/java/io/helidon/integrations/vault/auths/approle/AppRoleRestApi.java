/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.integrations.common.rest.ApiRequest;
import io.helidon.integrations.vault.VaultTokenBase;
import io.helidon.integrations.vault.auths.common.VaultRestApi;
import io.helidon.webclient.api.HttpClientRequest;

class AppRoleRestApi extends VaultRestApi {
    private static final System.Logger LOGGER = System.getLogger(AppRoleRestApi.class.getName());

    private static final HeaderName VAULT_TOKEN_HEADER_NAME =  HeaderNames.create("X-Vault-Token");

    private final AtomicReference<VaultTokenBase> currentToken = new AtomicReference<>();

    private final AppRoleAuth auth;
    private final String appRoleId;
    private final String secretId;

    AppRoleRestApi(Builder builder) {
        super(builder);
        this.auth = builder.auth;
        this.appRoleId = builder.appRoleId;
        this.secretId = builder.secretId;
        this.currentToken.set(builder.token);
    }

    static Builder appRoleBuilder() {
        return new Builder();
    }

    @Override
    protected HttpClientRequest updateRequestBuilderCommon(HttpClientRequest requestBuilder,
                                                           String path,
                                                           ApiRequest<?> request,
                                                           Method method,
                                                           String requestId) {
        VaultTokenBase currentToken = this.currentToken.get();

        if (currentToken != null) {
            if (!currentToken.renewable() || currentToken.created().plus(currentToken.leaseDuration()).isAfter(Instant.now())) {
                requestBuilder.header(VAULT_TOKEN_HEADER_NAME, currentToken.token());
                return requestBuilder;
            }
        }

        try {
            // we need to renew the token - this may be a concurrent operation as we do not care who wins
            Login.Response response = auth.login(Login.Request.create(appRoleId, secretId));
            VaultTokenBase token = response.token();
            this.currentToken.set(token);
            requestBuilder.header(VAULT_TOKEN_HEADER_NAME, token.token());
            return requestBuilder;
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, "Failed to renew AppRole token", ex);
            throw ex;
        }
    }

    static class Builder extends VaultRestApi.BuilderBase<Builder> {
        private AppRoleAuth auth;
        private VaultTokenBase token;
        private String secretId;
        private String appRoleId;

        private Builder() {
        }

        public Builder auth(AppRoleAuth auth) {
            this.auth = auth;
            return this;
        }

        public Builder token(VaultTokenBase token) {
            this.token = token;
            return this;
        }

        public Builder secretId(String secretId) {
            this.secretId = secretId;
            return this;
        }

        public Builder appRoleId(String appRoleId) {
            this.appRoleId = appRoleId;
            return this;
        }

        @Override
        protected VaultRestApi doBuild() {
            return new AppRoleRestApi(this);
        }


    }
}
