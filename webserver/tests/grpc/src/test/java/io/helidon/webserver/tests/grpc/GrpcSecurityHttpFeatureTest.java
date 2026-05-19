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

package io.helidon.webserver.tests.grpc;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.security.Security;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.security.providers.httpauth.SecureUserStore;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.grpc.GrpcConfig;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.grpc.spi.GrpcServerService;
import io.helidon.webserver.grpc.strings.StringServiceGrpc;
import io.helidon.webserver.grpc.strings.Strings.StringMessage;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.security.SecurityFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import io.grpc.Metadata;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class GrpcSecurityHttpFeatureTest extends BaseServiceTest {
    private static final String PROTECTED_PATH = "/protected-http";
    private static final String USERNAME = "jack";
    private static final char[] PASSWORD = "password".toCharArray();
    private static final Map<String, TestUser> USERS = Map.of(USERNAME,
                                                              new TestUser(USERNAME, PASSWORD, Set.of("user")));

    private final Http1Client httpClient;
    private StringServiceGrpc.StringServiceBlockingStub blockingStub;

    GrpcSecurityHttpFeatureTest(WebServer server, Http1Client httpClient) {
        super(server);
        this.httpClient = httpClient;
    }

    @SetUpServer
    static void setupServer(WebServerConfig.Builder server) {
        server.featuresDiscoverServices(false)
                .addFeature(ContextFeature.create())
                .addFeature(SecurityFeature.builder()
                                    .security(buildSecurity())
                                    .build());
    }

    @SetUpRoute
    static void httpRouting(HttpRouting.Builder router) {
        router.get(PROTECTED_PATH, SecurityFeature.rolesAllowed("user"), (req, res) -> res.send("http-ok"));
    }

    @SetUpRoute
    static void grpcRouting(Router.RouterBuilder<?> router) {
        Context context = Context.builder()
                .build();
        context.register(buildSecurity());

        GrpcRouting.Builder grpcRouting = GrpcRouting.builder();
        Contexts.runInContext(context, () -> GrpcConfig.create(buildGrpcSecurityConfig().get("server.protocols.grpc"))
                .grpcServices()
                .forEach(service -> service.interceptors().forEach(grpcRouting::intercept)));

        router.addRouting(grpcRouting.service(new StringService()));
    }

    @BeforeEach
    void beforeEach() {
        super.beforeEach();
        blockingStub = StringServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        blockingStub = null;
        super.afterEach();
    }

    @Test
    void unauthenticatedGrpcCallIsRejectedWhenGrpcSecurityIsConfigured() {
        try (Http1ClientResponse response = httpClient.get(PROTECTED_PATH).request()) {
            assertThat(response.status(), is(Status.UNAUTHORIZED_401));
        }

        try (Http1ClientResponse response = httpClient.get(PROTECTED_PATH)
                .header(HeaderNames.AUTHORIZATION, basicAuth(USERNAME, PASSWORD))
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is("http-ok"));
        }

        StringMessage request = StringMessage.newBuilder()
                .setText("grpc-ok")
                .build();

        try {
            StringMessage response = blockingStub.upper(request);
            throw new AssertionError("Unauthenticated gRPC call reached application handler: " + response.getText());
        } catch (StatusRuntimeException e) {
            assertThat(e.getStatus().getCode(), is(Code.UNAUTHENTICATED));
        }

        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                    basicAuth(USERNAME, PASSWORD));
        StringMessage response = blockingStub
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                .upper(request);
        assertThat(response.getText(), is("GRPC-OK"));
    }

    @Test
    void grpcSecurityIsLoadedFromGrpcServicesConfig() {
        Context context = Context.builder().build();
        context.register(buildSecurity());

        List<GrpcServerService> services = Contexts.runInContext(context,
                                                                 () -> GrpcConfig.create(buildGrpcSecurityConfig()
                                                                                                 .get("server.protocols.grpc"))
                                                                         .grpcServices());

        assertThat(services.size(), is(1));
        assertThat(services.get(0).interceptors().isEmpty(), is(false));
    }

    private static Security buildSecurity() {
        return Security.builder()
                .addAuthenticationProvider(HttpBasicAuthProvider.builder()
                                                   .realm("helidon")
                                                   .userStore(buildUserStore()),
                                           "http-basic-auth")
                .build();
    }

    private static Config buildGrpcSecurityConfig() {
        return Config.just(ConfigSources.create(Map.of(
                "server.protocols.grpc.grpc-services.security.services.0.name", "StringService",
                "server.protocols.grpc.grpc-services.security.services.0.defaults.authenticate", "true")));
    }

    private static SecureUserStore buildUserStore() {
        return login -> Optional.ofNullable(USERS.get(login));
    }

    private static String basicAuth(String username, char[] password) {
        String token = username + ":" + new String(password);
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    private record TestUser(String login, char[] password, Set<String> roles) implements SecureUserStore.User {
        @Override
        public boolean isPasswordValid(char[] candidate) {
            return Arrays.equals(password(), candidate);
        }
    }
}
