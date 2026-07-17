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

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode;
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
import io.helidon.webserver.grpc.GrpcProtocolConfigProvider;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.grpc.security.GrpcSecurity;
import io.helidon.webserver.grpc.spi.GrpcServerService;
import io.helidon.webserver.grpc.strings.StringServiceGrpc;
import io.helidon.webserver.grpc.strings.Strings.StringMessage;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.security.SecurityFeature;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import io.grpc.Metadata;
import io.grpc.ServerInterceptor;
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
        router.addRouting(GrpcRouting.builder()
                                  .config(buildGrpcSecurityConfig())
                                  .service(new StringService()));
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
        Config config = buildGrpcSecurityConfig();
        List<GrpcServerService> services = GrpcConfig.create(config.get("server.protocols.grpc"))
                .grpcServices();
        GrpcRouting routing = GrpcRouting.builder()
                .config(config)
                .build();
        GrpcRouting protocolRouting = GrpcRouting.builder()
                .config(config.get("server.protocols.grpc"))
                .build();

        GrpcServerService securityService = services.stream()
                .filter(GrpcSecurity.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertThat(securityService.interceptors().isEmpty(), is(false));
        assertGrpcSecurityInterceptor(routing, true);
        assertGrpcSecurityInterceptor(protocolRouting, true);
    }

    @Test
    void grpcSecurityIsLoadedFromGrpcConfigNode() {
        Config config = buildGrpcSecurityConfig("grpc");
        GrpcRouting routing = GrpcRouting.builder()
                .config(config)
                .build();
        GrpcRouting grpcRouting = GrpcRouting.builder()
                .config(config.get("grpc"))
                .build();

        assertGrpcSecurityInterceptor(routing, true);
        assertGrpcSecurityInterceptor(grpcRouting, true);
    }

    @Test
    void programmaticGrpcSecurityOverridesDiscoveredGrpcSecurity() {
        GrpcSecurity programmaticSecurity = GrpcSecurity.create(buildSecurity(), Config.empty());
        GrpcRouting routing = GrpcRouting.builder()
                .config(buildGrpcSecurityConfig())
                .intercept(programmaticSecurity)
                .build();

        List<GrpcSecurity> securityInterceptors = routing.interceptors()
                .stream()
                .filter(GrpcSecurity.class::isInstance)
                .map(GrpcSecurity.class::cast)
                .toList();
        assertThat(securityInterceptors, is(List.of(programmaticSecurity)));
    }

    @Test
    void protocolConfigDoesNotInstantiateRoutingServices() {
        Config config = Config.just(ConfigSources.create(Map.of("grpc-services-discover-services", "true")));
        GrpcConfig protocolConfig = new GrpcProtocolConfigProvider().create(config, "grpc");

        assertThat(protocolConfig.grpcServices().isEmpty(), is(true));
    }

    @Test
    void disabledGrpcSecurityDoesNotRequireSecurityConfig() {
        Config legacyConfig = Config.just(ConfigSources.create(Map.of(
                "server.protocols.grpc.grpc-services.security.enabled", "false")));
        GrpcRouting legacyRouting = GrpcRouting.builder()
                .config(legacyConfig)
                .build();
        GrpcRouting protocolRouting = GrpcRouting.builder()
                .config(legacyConfig.get("server.protocols.grpc"))
                .build();
        Config config = Config.just(ConfigSources.create(Map.of(
                "grpc.grpc-services.security.enabled", "false")));
        GrpcRouting routing = GrpcRouting.builder()
                .config(config)
                .build();

        assertGrpcSecurityInterceptor(legacyRouting, false);
        assertGrpcSecurityInterceptor(routing, false);
        assertGrpcSecurityInterceptor(protocolRouting, false);
    }

    @Test
    void excludedGrpcSecurityPreservesConfiguredGrpcServicesWithoutLeafValues() {
        Config config = Config.just(ConfigSources.create(ConfigNode.ObjectNode.builder()
                .addValue("security.providers.0.http-basic-auth.realm", "helidon")
                .addObject("server.protocols.grpc.grpc-services.security", ConfigNode.ObjectNode.empty())
                .addObject("server.protocols.grpc.grpc-services.tracing", ConfigNode.ObjectNode.empty())
                .build()));

        GrpcRouting routing = GrpcRouting.builder()
                .config(config)
                .addExcludedServiceName("security")
                .build();

        assertThat(routing.interceptors().stream().anyMatch(GrpcSecurity.class::isInstance), is(false));
        assertThat(routing.interceptors().stream()
                           .anyMatch(GrpcSecurityHttpFeatureTest::isGrpcTracingInterceptor),
                   is(true));
    }

    @Test
    void excludedGrpcSecurityDoesNotDropTypedServiceNamedSecurity() {
        Config config = Config.just(ConfigSources.create(ConfigNode.ObjectNode.builder()
                .addObject("server.protocols.grpc.grpc-services.security", ConfigNode.ObjectNode.builder()
                        .addValue("type", "tracing")
                        .build())
                .build()));

        GrpcRouting routing = GrpcRouting.builder()
                .config(config)
                .addExcludedServiceName("security")
                .build();

        assertThat(routing.interceptors().stream().anyMatch(GrpcSecurity.class::isInstance), is(false));
        assertThat(routing.interceptors().stream()
                           .anyMatch(GrpcSecurityHttpFeatureTest::isGrpcTracingInterceptor),
                   is(true));
    }

    @Test
    void excludedGrpcSecurityDropsCustomNamedSecurityService() {
        Config config = Config.just(ConfigSources.create(ConfigNode.ObjectNode.builder()
                .addValue("security.providers.0.http-basic-auth.realm", "helidon")
                .addObject("server.protocols.grpc.grpc-services.authn", ConfigNode.ObjectNode.builder()
                        .addValue("type", "security")
                        .build())
                .addObject("server.protocols.grpc.grpc-services.tracing", ConfigNode.ObjectNode.empty())
                .build()));

        GrpcRouting routing = GrpcRouting.builder()
                .config(config)
                .addExcludedServiceName("authn")
                .build();

        assertGrpcSecurityInterceptor(routing, false);
        assertThat(routing.interceptors().stream()
                           .anyMatch(GrpcSecurityHttpFeatureTest::isGrpcTracingInterceptor),
                   is(true));
    }

    @Test
    void excludedGrpcSecurityDropsListConfiguredCustomNamedSecurityService() {
        Config config = Config.just(ConfigSources.create(ConfigNode.ObjectNode.builder()
                .addValue("security.providers.0.http-basic-auth.realm", "helidon")
                .addList("server.protocols.grpc.grpc-services", ConfigNode.ListNode.builder()
                        .addObject(ConfigNode.ObjectNode.builder()
                                           .addValue("type", "security")
                                           .addValue("name", "authn")
                                           .build())
                        .addObject(ConfigNode.ObjectNode.builder()
                                           .addValue("type", "tracing")
                                           .build())
                        .build())
                .build()));

        GrpcRouting routing = GrpcRouting.builder()
                .config(config)
                .addExcludedServiceName("authn")
                .build();

        assertGrpcSecurityInterceptor(routing, false);
        assertThat(routing.interceptors().stream()
                           .anyMatch(GrpcSecurityHttpFeatureTest::isGrpcTracingInterceptor),
                   is(true));
    }

    private static boolean isGrpcTracingInterceptor(ServerInterceptor interceptor) {
        return interceptor.getClass()
                .getName()
                .endsWith(".GrpcTracingInterceptor");
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
        return buildGrpcSecurityConfig("server.protocols.grpc");
    }

    private static Config buildGrpcSecurityConfig(String configPrefix) {
        return Config.just(ConfigSources.create(Map.of(
                "security.providers.0.http-basic-auth.realm", "helidon",
                "security.providers.0.http-basic-auth.users.0.login", USERNAME,
                "security.providers.0.http-basic-auth.users.0.password", new String(PASSWORD),
                "security.providers.0.http-basic-auth.users.0.roles.0", "user",
                configPrefix + ".grpc-services.security.services.0.name", "StringService",
                configPrefix + ".grpc-services.security.services.0.defaults.authenticate", "true")));
    }

    private static void assertGrpcSecurityInterceptor(GrpcRouting routing, boolean expected) {
        assertThat(routing.interceptors().stream().anyMatch(GrpcSecurity.class::isInstance), is(expected));
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
