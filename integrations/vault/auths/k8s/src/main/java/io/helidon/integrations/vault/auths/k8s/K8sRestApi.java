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

package io.helidon.integrations.vault.auths.k8s;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.integrations.common.rest.ApiRequest;
import io.helidon.integrations.vault.VaultTokenBase;
import io.helidon.integrations.vault.auths.common.VaultRestApi;
import io.helidon.webclient.api.HttpClientRequest;

class K8sRestApi extends VaultRestApi {

    private static final HeaderName VAULT_TOKEN_HEADER_NAME = HeaderNames.create("X-Vault-Token");
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
    protected HttpClientRequest updateRequestBuilderCommon(HttpClientRequest requestBuilder,
                                                            String path,
                                                            ApiRequest<?> request,
                                                            Method method,
                                                            String requestId) {
        VaultTokenBase k8sToken = currentToken.get();

        if (k8sToken != null) {
            if (!k8sToken.renewable() || k8sToken.created().plus(k8sToken.leaseDuration()).isAfter(Instant.now())) {
                requestBuilder.header(VAULT_TOKEN_HEADER_NAME, k8sToken.token());
                return requestBuilder;
            }
        }

        // we need to renew the token - this may be a concurrent operation, though we do not care who wins
        Login.Response response = auth.login(Login.Request.create(roleName, jwtToken));
        VaultTokenBase token = response.token();
        currentToken.set(token);
        requestBuilder.header(VAULT_TOKEN_HEADER_NAME, token.token());
        return requestBuilder;
    }

    static class Builder extends VaultRestApi.BuilderBase<Builder> {
        private K8sAuth auth;
        private String roleName;
        private String jwtToken;

        private Builder() {
        }

        Builder auth(K8sAuth auth) {
            this.auth = auth;
            return this;
        }

        Builder roleName(String roleName) {
            this.roleName = roleName;
            return this;
        }

        Builder jwtToken(String jwtToken) {
            this.jwtToken = jwtToken;
            return this;
        }

        @Override
        protected VaultRestApi doBuild() {
            return new K8sRestApi(this);
        }
    }
}
