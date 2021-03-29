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

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.integrations.common.rest.ApiRequest;
import io.helidon.integrations.vault.VaultTokenBase;
import io.helidon.integrations.vault.auths.common.VaultRestApi;
import io.helidon.webclient.WebClientRequestBuilder;

class K8sRestApi extends VaultRestApi {
    private final AtomicReference<VaultTokenBase> currentToken = new AtomicReference<>();

    private final K8sAuth auth;
    private final String roleName;
    private final String jwtToken;

    K8sRestApi(Builder builder) {
        super(builder);

        this.auth = builder.auth;
        this.roleName = builder.roleName;
        this.jwtToken = builder.jwtToken;
    }

    static Builder k8sBuilder() {
        return new Builder();
    }

    @Override
    protected Single<WebClientRequestBuilder> updateRequestBuilderCommon(WebClientRequestBuilder requestBuilder,
                                                                         String path,
                                                                         ApiRequest<?> request,
                                                                         Http.RequestMethod method,
                                                                         String requestId) {
        VaultTokenBase k8sToken = currentToken.get();

        if (k8sToken != null) {
            if (!k8sToken.renewable() || k8sToken.created().plus(k8sToken.leaseDuration()).isAfter(Instant.now())) {
                requestBuilder.headers().add("X-Vault-Token", k8sToken.token());
                return Single.just(requestBuilder);
            }
        }

        // we need to renew the token - this may be a concurrent operation, though we do not care who wins
        return auth.login(Login.Request.create(roleName, jwtToken))
                .map(it -> {
                    VaultTokenBase token = it.token();
                    currentToken.set(token);
                    requestBuilder.headers().add("X-Vault-Token", token.token());
                    return requestBuilder;
                });
    }

    static class Builder extends VaultRestApi.BuilderBase<Builder> {
        private K8sAuth auth;
        private String roleName;
        private String jwtToken;
        private VaultTokenBase token;

        private Builder() {
        }

        public Builder token(VaultTokenBase token) {
            this.token = token;
            return this;
        }

        public Builder auth(K8sAuth auth) {
            this.auth = auth;
            return this;
        }

        public Builder roleName(String roleName) {
            this.roleName = roleName;
            return this;
        }

        public Builder jwtToken(String jwtToken) {
            this.jwtToken = jwtToken;
            return this;
        }

        @Override
        protected VaultRestApi doBuild() {
            return new K8sRestApi(this);
        }
    }
}
