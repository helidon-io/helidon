/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import java.util.concurrent.TimeUnit;

import io.helidon.common.LogConfig;
import io.helidon.config.Config;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import io.helidon.grpc.server.ServiceDescriptor;
import io.helidon.grpc.server.test.Echo;
import io.helidon.grpc.server.test.EchoServiceGrpc;
import io.helidon.grpc.server.test.StringServiceGrpc;
import io.helidon.grpc.server.test.Strings.StringMessage;
import io.helidon.security.Security;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;

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


public class ServiceAndMethodLevelSecurityIT {

    private static GrpcServer grpcServer;

    private static TestCallCredentials adminCreds = new TestCallCredentials("Ted", "secret");

    private static TestCallCredentials userCreds = new TestCallCredentials("Bob", "password");

    private static EchoServiceGrpc.EchoServiceBlockingStub adminEchoStub;

    private static EchoServiceGrpc.EchoServiceBlockingStub userEchoStub;

    private static StringServiceGrpc.StringServiceBlockingStub adminStringStub;

    private static StringServiceGrpc.StringServiceBlockingStub userStringStub;

    private static StringServiceGrpc.StringServiceBlockingStub noCredsEchoStub;

    @BeforeAll
    public static void startServer() throws Exception {
        LogConfig.configureRuntime();

        Config config = Config.create();

        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.create(config.get("http-basic-auth")))
                .build();


        ServiceDescriptor echoService = ServiceDescriptor.builder(new EchoService())
                .intercept(GrpcSecurity.rolesAllowed("admin"))
                .build();

        ServiceDescriptor stringService = ServiceDescriptor.builder(new StringService())
                .intercept("Upper", GrpcSecurity.rolesAllowed("admin"))
                .intercept("Split", GrpcSecurity.rolesAllowed("admin"))
                .build();

        // Add the EchoService
        GrpcRouting routing = GrpcRouting.builder()
                                         .intercept(GrpcSecurity.create(security).securityDefaults(GrpcSecurity.authenticate()))
                                         .register(echoService)
                                         .register(stringService)
                                         .build();

        // Run the server on port 0 so that it picks a free ephemeral port
        GrpcServerConfiguration serverConfig = GrpcServerConfiguration.builder().port(0).build();

        grpcServer = GrpcServer.create(serverConfig, routing)
                        .start()
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

        Channel channel = InProcessChannelBuilder.forName(grpcServer.configuration().name())
                .build();

        adminEchoStub = EchoServiceGrpc.newBlockingStub(channel).withCallCredentials(adminCreds);
        userEchoStub = EchoServiceGrpc.newBlockingStub(channel).withCallCredentials(userCreds);
        adminStringStub = StringServiceGrpc.newBlockingStub(channel).withCallCredentials(adminCreds);
        userStringStub = StringServiceGrpc.newBlockingStub(channel).withCallCredentials(userCreds);
        noCredsEchoStub = StringServiceGrpc.newBlockingStub(channel);
    }

    @AfterAll
    public static void cleanup() {
        grpcServer.shutdown();
    }

    @Test
    public void shouldBeSecuredWithGlobalSettingsAllowAccess() {
        StringMessage message = userStringStub.lower(toMessage("ABCD"));
        assertThat(message.getText(), is("abcd"));
    }

    @Test
    public void shouldBeSecuredWithGlobalSettingsDenyAccess() {
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class, () ->
                noCredsEchoStub.lower(toMessage("FOO")));

        assertThat(thrown.getStatus().getCode(), is(Status.PERMISSION_DENIED.getCode()));
    }

    @Test
    public void shouldBeSecuredWithServiceSettingsAllowAccess() {
        Echo.EchoResponse response = adminEchoStub.echo(Echo.EchoRequest.newBuilder().setMessage("foo").build());
        assertThat(response.getMessage(), is("foo"));
    }

    @Test
    public void shouldBeSecuredWithServiceSettingsDenyAccess() {
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class, () ->
                userEchoStub.echo(Echo.EchoRequest.newBuilder().setMessage("foo").build()));

        assertThat(thrown.getStatus().getCode(), is(Status.PERMISSION_DENIED.getCode()));
    }

    @Test
    public void shouldBeSecuredWithMethodSettingsAllowAccess() {
        StringMessage message = adminStringStub.upper(toMessage("abcd"));
        assertThat(message.getText(), is("ABCD"));
    }

    @Test
    public void shouldBeSecuredWithMethodSettingsDenyAccess() {
        // StringService.split is a server streaming call so the proto generated code will
        // return an Iterator even though the actual call fails with PERMISSION_DENIED
        Iterator<StringMessage> it = userStringStub.split(toMessage("a b c d"));

        // It is not until accessing methods on the Iterator that we get the exception
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class, it::hasNext);

        assertThat(thrown.getStatus().getCode(), is(Status.PERMISSION_DENIED.getCode()));
    }

    private StringMessage toMessage(String text) {
        return StringMessage.newBuilder().setText(text).build();
    }
}
