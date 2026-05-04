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

package io.helidon.security.integration.grpc;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.common.LogConfig;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import io.helidon.grpc.server.ServiceDescriptor;
import io.helidon.grpc.server.test.Echo;
import io.helidon.grpc.server.test.EchoServiceGrpc;
import io.helidon.grpc.server.test.StringServiceGrpc;
import io.helidon.grpc.server.test.Strings;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Security;
import io.helidon.security.Subject;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.security.spi.AuthorizationProvider;

import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import services.EchoService;
import services.StringService;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DefaultSecurityFromConfigTest {
    private static final String META_CONFIG_PROPERTY = "io.helidon.config.meta-config";

    private static final AtomicInteger CONFIG_AUTHORIZER_ROLE_CHECKS = new AtomicInteger();

    private static GrpcServer grpcServer;

    private static TestCallCredentials adminCreds = new TestCallCredentials("Ted", "secret");

    private static TestCallCredentials userCreds = new TestCallCredentials("Bob", "password");

    private static EchoServiceGrpc.EchoServiceBlockingStub adminEchoStub;

    private static EchoServiceGrpc.EchoServiceBlockingStub userEchoStub;

    private static StringServiceGrpc.StringServiceBlockingStub adminStringStub;

    private static StringServiceGrpc.StringServiceBlockingStub userStringStub;

    private static StringServiceGrpc.StringServiceBlockingStub noCredsStringStub;

    @BeforeAll
    public static void startServer() throws Exception {
        LogConfig.configureRuntime();

        GrpcRouting routing = withDefaultConfig(() -> GrpcRouting.builder()
                .register(new EchoService())
                .register(new StringService())
                .build());

        grpcServer = startGrpcServer("default-security-from-config", routing);

        Channel channel = InProcessChannelBuilder.forName(grpcServer.configuration().name())
                .build();

        adminEchoStub = EchoServiceGrpc.newBlockingStub(channel).withCallCredentials(adminCreds);
        userEchoStub = EchoServiceGrpc.newBlockingStub(channel).withCallCredentials(userCreds);
        adminStringStub = StringServiceGrpc.newBlockingStub(channel).withCallCredentials(adminCreds);
        userStringStub = StringServiceGrpc.newBlockingStub(channel).withCallCredentials(userCreds);
        noCredsStringStub = StringServiceGrpc.newBlockingStub(channel);
    }

    @AfterAll
    public static void cleanup() {
        grpcServer.shutdown();
    }

    @Test
    public void shouldApplyGlobalSettingsFromConfig() {
        Strings.StringMessage message = userStringStub.lower(toMessage("ABCD"));
        assertThat(message.getText(), is("abcd"));

        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class, () ->
                noCredsStringStub.lower(toMessage("FOO")));

        assertThat(thrown.getStatus().getCode(), is(Status.UNAUTHENTICATED.getCode()));
    }

    @Test
    public void shouldApplyServiceSettingsFromConfig() {
        Echo.EchoResponse response = adminEchoStub.echo(Echo.EchoRequest.newBuilder().setMessage("foo").build());
        assertThat(response.getMessage(), is("foo"));

        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class, () ->
                userEchoStub.echo(Echo.EchoRequest.newBuilder().setMessage("foo").build()));

        assertThat(thrown.getStatus().getCode(), is(Status.PERMISSION_DENIED.getCode()));
    }

    @Test
    public void shouldApplyMethodSettingsFromConfig() {
        Strings.StringMessage message = adminStringStub.upper(toMessage("abcd"));
        assertThat(message.getText(), is("ABCD"));

        Iterator<Strings.StringMessage> it = userStringStub.split(toMessage("a b c d"));
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class, it::hasNext);

        assertThat(thrown.getStatus().getCode(), is(Status.PERMISSION_DENIED.getCode()));
    }

    @Test
    public void shouldApplySettingsFromExplicitRootConfig() throws Exception {
        GrpcRouting routing = GrpcRouting.builder(secureServicesConfig())
                .register(new EchoService())
                .register(new StringService())
                .build();
        GrpcServer server = startGrpcServer("default-security-explicit-config", routing);
        try {
            Channel channel = InProcessChannelBuilder.forName(server.configuration().name())
                    .build();
            EchoServiceGrpc.EchoServiceBlockingStub userEcho =
                    EchoServiceGrpc.newBlockingStub(channel).withCallCredentials(userCreds);

            StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class, () ->
                    userEcho.echo(Echo.EchoRequest.newBuilder().setMessage("foo").build()));
            assertThat(thrown.getStatus().getCode(), is(Status.PERMISSION_DENIED.getCode()));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void shouldApplyServiceAndMethodSettingsToPrebuiltDescriptors() throws Exception {
        GrpcRouting routing = withDefaultConfig(() -> GrpcRouting.builder()
                .register(ServiceDescriptor.builder(new EchoService()).build())
                .register(ServiceDescriptor.builder(new StringService()).build())
                .build());
        GrpcServer server = startGrpcServer("default-security-prebuilt", routing);
        try {
            Channel channel = InProcessChannelBuilder.forName(server.configuration().name())
                    .build();
            EchoServiceGrpc.EchoServiceBlockingStub userEcho =
                    EchoServiceGrpc.newBlockingStub(channel).withCallCredentials(userCreds);
            StringServiceGrpc.StringServiceBlockingStub userString =
                    StringServiceGrpc.newBlockingStub(channel).withCallCredentials(userCreds);

            StatusRuntimeException serviceThrown = assertThrows(StatusRuntimeException.class, () ->
                    userEcho.echo(Echo.EchoRequest.newBuilder().setMessage("foo").build()));
            assertThat(serviceThrown.getStatus().getCode(), is(Status.PERMISSION_DENIED.getCode()));

            Iterator<Strings.StringMessage> it = userString.split(toMessage("a b c d"));
            StatusRuntimeException methodThrown = assertThrows(StatusRuntimeException.class, it::hasNext);
            assertThat(methodThrown.getStatus().getCode(), is(Status.PERMISSION_DENIED.getCode()));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void shouldRejectUnknownConfiguredMethod() {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("invalid-method-secure-services.conf"))
                .build();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> GrpcRouting.builder(config)
                .register(new StringService())
                .build());
        assertThat(thrown.getMessage(), is("No method exists with name 'Upperr'"));
    }

    @Test
    public void shouldNotAddDefaultSecurityWithoutGrpcServerConfig() {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("application.yaml"))
                .build();
        GrpcRouting routing = GrpcRouting.builder(config)
                .register(new EchoService())
                .build();

        assertThat(grpcSecurityInterceptorCount(routing), is(0L));
    }

    @Test
    public void shouldNotAddDefaultSecurityWhenSameDefaultConfigSecurityIsRegistered() {
        GrpcRouting routing = withDefaultConfig(() -> {
            Config config = Config.create();
            return GrpcRouting.builder()
                    .intercept(GrpcSecurity.create(config.get("security")))
                    .register(new EchoService())
                    .build();
        });

        assertThat(grpcSecurityInterceptorCount(routing), is(1L));
    }

    @Test
    public void shouldPreserveDefaultSecurityWhenExplicitSecurityIsRegisteredLate() throws Exception {
        Config config = secureServicesConfig();
        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.create(config.get("security.providers.0.http-basic-auth")))
                .build();

        GrpcRouting routing = withDefaultConfig(() -> GrpcRouting.builder()
                .register(new EchoService())
                .register(new StringService())
                .intercept(GrpcSecurity.create(security).securityDefaults(GrpcSecurity.authenticate()))
                .build());
        assertThat(grpcSecurityInterceptorCount(routing), is(2L));
        GrpcServer server = startGrpcServer("default-security-explicit", routing);
        try {
            Channel channel = InProcessChannelBuilder.forName(server.configuration().name())
                    .build();
            EchoServiceGrpc.EchoServiceBlockingStub userEcho =
                    EchoServiceGrpc.newBlockingStub(channel).withCallCredentials(userCreds);
            StringServiceGrpc.StringServiceBlockingStub noCredsString = StringServiceGrpc.newBlockingStub(channel);

            StatusRuntimeException serviceThrown = assertThrows(StatusRuntimeException.class, () ->
                    userEcho.echo(Echo.EchoRequest.newBuilder().setMessage("foo").build()));
            assertThat(serviceThrown.getStatus().getCode(), is(Status.PERMISSION_DENIED.getCode()));

            StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class, () ->
                    noCredsString.lower(toMessage("FOO")));
            assertThat(thrown.getStatus().getCode(), is(Status.UNAUTHENTICATED.getCode()));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void shouldUseConfigSecurityForDefaultSecurityWithExplicitSecurity() throws Exception {
        Config config = namedAuthorizerConfig();
        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.create(config.get("security.providers.0.http-basic-auth")))
                .build();

        GrpcRouting routing = GrpcRouting.builder(config)
                .register(new EchoService())
                .intercept(GrpcSecurity.create(security).securityDefaults(GrpcSecurity.authenticate()))
                .build();
        assertThat(grpcSecurityInterceptorCount(routing), is(2L));
        GrpcServer server = startGrpcServer("default-security-config-context", routing);
        try {
            Channel channel = InProcessChannelBuilder.forName(server.configuration().name())
                    .build();
            EchoServiceGrpc.EchoServiceBlockingStub userEcho =
                    EchoServiceGrpc.newBlockingStub(channel).withCallCredentials(userCreds);

            Echo.EchoResponse response = userEcho.echo(Echo.EchoRequest.newBuilder().setMessage("foo").build());
            assertThat(response.getMessage(), is("foo"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void shouldNotReprocessDefaultSecurityWhenExplicitConfigSecurityIsRegistered() throws Exception {
        Config config = namedAuthorizerConfig();
        GrpcRouting routing = GrpcRouting.builder(config)
                .intercept(GrpcSecurity.create(config.get("security")))
                .register(new EchoService())
                .build();
        assertThat(grpcSecurityInterceptorCount(routing), is(1L));

        CONFIG_AUTHORIZER_ROLE_CHECKS.set(0);
        GrpcServer server = startGrpcServer("default-security-explicit-config-context", routing);
        try {
            Channel channel = InProcessChannelBuilder.forName(server.configuration().name())
                    .build();
            EchoServiceGrpc.EchoServiceBlockingStub userEcho =
                    EchoServiceGrpc.newBlockingStub(channel).withCallCredentials(userCreds);

            Echo.EchoResponse response = userEcho.echo(Echo.EchoRequest.newBuilder().setMessage("foo").build());
            assertThat(response.getMessage(), is("foo"));
            assertThat(CONFIG_AUTHORIZER_ROLE_CHECKS.get(), is(1));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void shouldNotReuseConfigHandlerAcrossMethodContextHandlers() throws Exception {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("service-default-secure-services.conf"))
                .build();
        ServiceDescriptor stringService = ServiceDescriptor.builder(new StringService())
                .intercept("Lower", GrpcSecurity.rolesAllowed("user"))
                .intercept("Upper", GrpcSecurity.rolesAllowed("admin"))
                .build();
        GrpcRouting routing = GrpcRouting.builder(config)
                .register(stringService)
                .build();

        GrpcServer server = startGrpcServer("default-security-method-context", routing);
        try {
            Channel channel = InProcessChannelBuilder.forName(server.configuration().name())
                    .build();
            StringServiceGrpc.StringServiceBlockingStub userString =
                    StringServiceGrpc.newBlockingStub(channel).withCallCredentials(userCreds);
            StringServiceGrpc.StringServiceBlockingStub adminString =
                    StringServiceGrpc.newBlockingStub(channel).withCallCredentials(adminCreds);

            Strings.StringMessage lower = userString.lower(toMessage("ABCD"));
            assertThat(lower.getText(), is("abcd"));

            StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class, () ->
                    userString.upper(toMessage("abcd")));
            assertThat(thrown.getStatus().getCode(), is(Status.PERMISSION_DENIED.getCode()));

            Strings.StringMessage upper = adminString.upper(toMessage("abcd"));
            assertThat(upper.getText(), is("ABCD"));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void shouldApplyDefaultSecurityAfterDifferentExplicitConfigSecurity() throws Exception {
        GrpcRouting routing = GrpcRouting.builder(secureServicesConfig())
                .intercept(GrpcSecurity.create(namedAuthorizerConfig().get("security")))
                .register(new EchoService())
                .build();
        assertThat(grpcSecurityInterceptorCount(routing), is(2L));

        GrpcServer server = startGrpcServer("default-security-different-config-context", routing);
        try {
            Channel channel = InProcessChannelBuilder.forName(server.configuration().name())
                    .build();
            EchoServiceGrpc.EchoServiceBlockingStub userEcho =
                    EchoServiceGrpc.newBlockingStub(channel).withCallCredentials(userCreds);

            StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class, () ->
                    userEcho.echo(Echo.EchoRequest.newBuilder().setMessage("foo").build()));
            assertThat(thrown.getStatus().getCode(), is(Status.PERMISSION_DENIED.getCode()));
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void shouldApplyDefaultSecurityAfterDifferentUnmatchedConfigSecurity() throws Exception {
        GrpcRouting routing = GrpcRouting.builder(secureServicesConfig())
                .intercept(GrpcSecurity.create(namedAuthorizerConfig().get("security")))
                .intercept(GrpcSecurity.create(unmatchedConfig().get("security")))
                .register(new EchoService())
                .build();
        assertThat(grpcSecurityInterceptorCount(routing), is(3L));

        GrpcServer server = startGrpcServer("default-security-unmatched-config-context", routing);
        try {
            Channel channel = InProcessChannelBuilder.forName(server.configuration().name())
                    .build();
            EchoServiceGrpc.EchoServiceBlockingStub userEcho =
                    EchoServiceGrpc.newBlockingStub(channel).withCallCredentials(userCreds);

            StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class, () ->
                    userEcho.echo(Echo.EchoRequest.newBuilder().setMessage("foo").build()));
            assertThat(thrown.getStatus().getCode(), is(Status.PERMISSION_DENIED.getCode()));
        } finally {
            server.shutdown();
        }
    }

    public static class ConfigAuthorizer implements AuthorizationProvider {
        @Override
        public CompletionStage<AuthorizationResponse> authorize(ProviderRequest context) {
            return CompletableFuture.completedFuture(AuthorizationResponse.permit());
        }

        @Override
        public boolean isUserInRole(Subject subject, String role) {
            CONFIG_AUTHORIZER_ROLE_CHECKS.incrementAndGet();
            return role.equals("config-role");
        }
    }

    private static GrpcServer startGrpcServer(String name, GrpcRouting routing) throws Exception {
        GrpcServerConfiguration serverConfig = GrpcServerConfiguration.builder()
                .name(name)
                .port(0)
                .build();
        return GrpcServer.create(serverConfig, routing)
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
    }

    private static Config secureServicesConfig() {
        return Config.builder()
                .sources(ConfigSources.classpath("secure-services.conf"))
                .build();
    }

    private static Config namedAuthorizerConfig() {
        return Config.builder()
                .sources(ConfigSources.classpath("named-authorizer-secure-services.conf"))
                .build();
    }

    private static Config unmatchedConfig() {
        return Config.builder()
                .sources(ConfigSources.create(Map.of("security.grpc-server.services.0.name", "OtherService")))
                .build();
    }

    private static long grpcSecurityInterceptorCount(GrpcRouting routing) {
        return routing.interceptors()
                .stream()
                .filter(GrpcSecurity.class::isInstance)
                .count();
    }

    private static GrpcRouting withDefaultConfig(Supplier<GrpcRouting> routing) {
        String originalMetaConfig = System.getProperty(META_CONFIG_PROPERTY);
        try {
            System.setProperty(META_CONFIG_PROPERTY, "grpc-secure-services-meta.properties");
            return routing.get();
        } finally {
            if (originalMetaConfig == null) {
                System.clearProperty(META_CONFIG_PROPERTY);
            } else {
                System.setProperty(META_CONFIG_PROPERTY, originalMetaConfig);
            }
        }
    }

    private Strings.StringMessage toMessage(String text) {
        return Strings.StringMessage.newBuilder().setText(text).build();
    }
}
