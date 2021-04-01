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

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.integrations.common.rest.ApiRequest;
import io.helidon.integrations.vault.VaultTokenBase;
import io.helidon.integrations.vault.auths.common.VaultRestApi;
import io.helidon.webclient.WebClientRequestBuilder;

class AppRoleRestApi extends VaultRestApi {
    private static final Logger LOGGER = Logger.getLogger(AppRoleRestApi.class.getName());

    private final AtomicReference<VaultTokenBase> currentToken = new AtomicReference<>();

    private final AppRoleAuthRx auth;
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
    protected Single<WebClientRequestBuilder> updateRequestBuilderCommon(WebClientRequestBuilder requestBuilder,
                                                                         String path,
                                                                         ApiRequest<?> request,
                                                                         Http.RequestMethod method,
                                                                         String requestId) {
        VaultTokenBase currentToken = this.currentToken.get();

        if (currentToken != null) {
            if (!currentToken.renewable() || currentToken.created().plus(currentToken.leaseDuration()).isAfter(Instant.now())) {
                requestBuilder.headers().add("X-Vault-Token", currentToken.token());
                return Single.just(requestBuilder);
            }
        }

        // we need to renew the token - this may be a concurrent operation as we do not care who wins
        return auth.login(Login.Request.create(appRoleId, secretId))
                .map(it -> {
                    VaultTokenBase token = it.token();
                    this.currentToken.set(token);
                    requestBuilder.headers().add("X-Vault-Token", token.token());
                    return requestBuilder;
                })
                .onError(throwable -> LOGGER.log(Level.WARNING, "Failed to renew AppRole token", throwable));
    }

    static class Builder extends VaultRestApi.BuilderBase<Builder> {
        private AppRoleAuthRx auth;
        private VaultTokenBase token;
        private String secretId;
        private String appRoleId;

        private Builder() {
        }

        public Builder auth(AppRoleAuthRx auth) {
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
