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

package io.helidon.microprofile.grpc.tests;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.grpc.api.Grpc;
import io.helidon.grpc.core.ResponseHelper;
import io.helidon.microprofile.grpc.tests.test.Services;
import io.helidon.microprofile.grpc.tests.test.UnaryServiceGrpc;
import io.helidon.microprofile.testing.junit5.AddConfigBlock;
import io.helidon.security.Security;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.security.providers.httpauth.SecureUserStore;

import io.grpc.Metadata;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AddConfigBlock(type = "yaml", value = """
        grpc:
          grpc-services:
            security:
              services:
                - name: "UnaryService"
                  defaults:
                    authenticate: true
        """)
class GrpcSecurityTest extends BaseServiceTest {
    private static final String USERNAME = "jack";
    private static final char[] PASSWORD = "password".toCharArray();
    private static final Map<String, TestUser> USERS = Map.of(USERNAME,
                                                              new TestUser(USERNAME, PASSWORD, Set.of("user")));

    @Inject
    GrpcSecurityTest(WebTarget webTarget) {
        super(webTarget);
    }

    @Test
    void shouldSecureMicroProfileGrpcServiceFromConfig() {
        Services.TestRequest request = Services.TestRequest.newBuilder()
                .setMessage("hello")
                .build();
        UnaryServiceGrpc.UnaryServiceBlockingStub noCredentials =
                UnaryServiceGrpc.newBlockingStub(grpcClient().channel());
        UnaryServiceGrpc.UnaryServiceBlockingStub withCredentials =
                noCredentials.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(basicAuthHeaders()));

        Services.TestResponse response = withCredentials.requestResponse(request);
        assertThat(response.getMessage(), is("secure hello"));

        SecureUnaryService.INVOCATIONS.set(0);
        StatusRuntimeException thrown =
                assertThrows(StatusRuntimeException.class, () -> noCredentials.requestResponse(request));
        assertThat(thrown.getStatus().getCode(), is(Code.UNAUTHENTICATED));
        assertThat(SecureUnaryService.INVOCATIONS.get(), is(0));
    }

    private static Metadata basicAuthHeaders() {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                    basicAuth(USERNAME, PASSWORD));
        return headers;
    }

    private static String basicAuth(String username, char[] password) {
        String token = username + ":" + new String(password);
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    @ApplicationScoped
    public static class SecurityProducer {
        @Produces
        @ApplicationScoped
        Security security() {
            return Security.builder()
                    .addAuthenticationProvider(HttpBasicAuthProvider.builder()
                                                       .realm("helidon")
                                                       .userStore(login -> Optional.ofNullable(USERS.get(login))),
                                               "http-basic-auth")
                    .build();
        }
    }

    @Grpc.GrpcService("UnaryService")
    @ApplicationScoped
    public static class SecureUnaryService {
        private static final AtomicInteger INVOCATIONS = new AtomicInteger();

        @Grpc.Unary("requestResponse")
        public void requestResponse(Services.TestRequest request, StreamObserver<Services.TestResponse> observer) {
            INVOCATIONS.incrementAndGet();
            ResponseHelper.complete(observer,
                                    Services.TestResponse.newBuilder()
                                            .setMessage("secure " + request.getMessage())
                                            .build());
        }
    }

    private record TestUser(String login, char[] password, Set<String> roles) implements SecureUserStore.User {
        @Override
        public boolean isPasswordValid(char[] candidate) {
            return Arrays.equals(password(), candidate);
        }
    }
}
