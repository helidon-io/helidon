/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault.auths.token;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.media.type.MediaType;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.integrations.common.rest.ApiRequest;
import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.Vault;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class TokenVaultAuthRedirectTest {
    private static final String VAULT_TOKEN = "helidon-vault-token";
    private static final String VAULT_NAMESPACE = "helidon-vault-namespace";
    private static final HeaderName VAULT_TOKEN_HEADER_NAME = HeaderNames.create("X-Vault-Token");
    private static final HeaderName VAULT_NAMESPACE_HEADER_NAME = HeaderNames.create("X-Vault-Namespace");

    @Test
    void stripsVaultHeadersOnCrossOriginRedirect() {
        AtomicReference<CapturedHeaders> capturedHeaders = new AtomicReference<>();

        WebServer redirectTarget = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .routing(rules -> rules.get("/v1/capture", (req, res) -> {
                    capturedHeaders.set(capturedHeaders(req));
                    res.status(Status.NO_CONTENT_204).send();
                }))
                .build()
                .start();

        WebServer trustedVault = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .routing(rules -> rules.get("/v1/sys/health", (req, res) -> {
                    res.status(Status.TEMPORARY_REDIRECT_307)
                            .header(HeaderNames.LOCATION,
                                    "http://127.0.0.1:" + redirectTarget.port() + "/v1/capture")
                            .send();
                }))
                .build()
                .start();

        try {
            SimpleResponse response = invokeHealth(trustedVault);

            assertThat(response.status(), is(Status.NO_CONTENT_204));
            assertThat(capturedHeaders.get(), is(notNullValue()));
            assertThat(capturedHeaders.get().token(), is(nullValue()));
            assertThat(capturedHeaders.get().namespace(), is(nullValue()));
        } finally {
            trustedVault.stop();
            redirectTarget.stop();
        }
    }

    @Test
    void preservesVaultHeadersOnSameOriginRedirect() {
        AtomicReference<CapturedHeaders> capturedHeaders = new AtomicReference<>();

        WebServer trustedVault = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .routing(rules -> rules.get("/v1/sys/health", (req, res) -> {
                            res.status(Status.TEMPORARY_REDIRECT_307)
                                    .header(HeaderNames.LOCATION, "/v1/capture")
                                    .send();
                        })
                        .get("/v1/capture", (req, res) -> {
                            capturedHeaders.set(capturedHeaders(req));
                            res.status(Status.NO_CONTENT_204).send();
                        }))
                .build()
                .start();

        try {
            SimpleResponse response = invokeHealth(trustedVault);

            assertThat(response.status(), is(Status.NO_CONTENT_204));
            assertThat(capturedHeaders.get(), is(notNullValue()));
            assertThat(capturedHeaders.get().token(), is(VAULT_TOKEN));
            assertThat(capturedHeaders.get().namespace(), is(VAULT_NAMESPACE));
        } finally {
            trustedVault.stop();
        }
    }

    @SuppressWarnings("removal")
    private static SimpleResponse invokeHealth(WebServer trustedVault) {
        Vault.Builder vaultBuilder = Vault.builder()
                .disableVaultAuthDiscovery()
                .address("http://127.0.0.1:" + trustedVault.port())
                .baseNamespace(VAULT_NAMESPACE);

        RestApi restApi = TokenVaultAuth.builder()
                .token(VAULT_TOKEN)
                .build()
                .authenticate(io.helidon.common.config.Config.empty(), vaultBuilder)
                .orElseThrow();

        return restApi.invoke(Method.GET, "/sys/health", new EmptyRequest(), SimpleResponse.builder());
    }

    private static CapturedHeaders capturedHeaders(ServerRequest request) {
        return new CapturedHeaders(request.headers().first(VAULT_TOKEN_HEADER_NAME).orElse(null),
                                  request.headers().first(VAULT_NAMESPACE_HEADER_NAME).orElse(null));
    }

    private record CapturedHeaders(String token, String namespace) {
    }

    private static final class EmptyRequest implements ApiRequest<EmptyRequest> {
        @Override
        public EmptyRequest addHeader(String name, String... value) {
            return this;
        }

        @Override
        public EmptyRequest addQueryParam(String name, String... value) {
            return this;
        }

        @Override
        public EmptyRequest requestMediaType(MediaType mediaType) {
            return this;
        }

        @Override
        public EmptyRequest responseMediaType(MediaType mediaType) {
            return this;
        }

        @Override
        public EmptyRequest requestId(String requestId) {
            return this;
        }

        @Override
        public Map<String, List<String>> headers() {
            return Map.of();
        }

        @Override
        public Map<String, List<String>> queryParams() {
            return Map.of();
        }

        @Override
        public Optional<MediaType> requestMediaType() {
            return Optional.empty();
        }

        @Override
        public Optional<MediaType> responseMediaType() {
            return Optional.empty();
        }

        @Override
        public Optional<String> requestId() {
            return Optional.empty();
        }
    }

    private static final class SimpleResponse extends ApiResponse {
        private SimpleResponse(Builder builder) {
            super(builder);
        }

        static Builder builder() {
            return new Builder();
        }

        private static final class Builder extends ApiResponse.Builder<Builder, SimpleResponse> {
            @Override
            public SimpleResponse build() {
                return new SimpleResponse(this);
            }
        }
    }
}
