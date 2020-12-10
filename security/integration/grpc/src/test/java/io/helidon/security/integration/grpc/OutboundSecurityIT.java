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

import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import io.helidon.common.LogConfig;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.grpc.core.GrpcHelper;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import io.helidon.grpc.server.ServiceDescriptor;
import io.helidon.grpc.server.test.Echo;
import io.helidon.grpc.server.test.EchoServiceGrpc;
import io.helidon.security.Security;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.WebServer;

import io.grpc.Channel;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import services.SecuredOutboundEchoService;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class OutboundSecurityIT {

    private static WebServer webServer;

    private static GrpcServer grpcServer;

    private static TestCallCredentials adminCreds = new TestCallCredentials("Ted", "secret");

    private static EchoServiceGrpc.EchoServiceBlockingStub adminEchoStub;

    private static EchoServiceGrpc.EchoServiceBlockingStub noCredsEchoStub;

    private static String webServerURL;

    private static Client client;

    // ----- test lifecycle methods -----------------------------------------

    @BeforeAll
    public static void startServers() throws Exception {
        LogConfig.configureRuntime();

        Config config = Config.create();

        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.create(config.get("http-basic-auth")))
                .build();


        // secured web server's Routing
        Routing webRouting = Routing.builder()
                .register(WebSecurity.create(security).securityDefaults(WebSecurity.authenticate()))
                .get("/test", WebSecurity.rolesAllowed("admin"), OutboundSecurityIT::echoWebRequest)
                .get("/propagate", WebSecurity.rolesAllowed("user"), OutboundSecurityIT::propagateCredentialsWebRequest)
                .get("/override", WebSecurity.rolesAllowed("user"), OutboundSecurityIT::overrideCredentialsWebRequest)
                .build();

        webServer = WebServer.create(webRouting)
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        webServerURL = "http://127.0.0.1:" + webServer.port();

        client = ClientBuilder.newBuilder().build()
                .register(HttpAuthenticationFeature.basicBuilder().build());

        ServiceDescriptor echoService = ServiceDescriptor.builder(new SecuredOutboundEchoService(webServerURL))
                .intercept(GrpcSecurity.rolesAllowed("admin"))
                .build();

        // Add the EchoService
        GrpcRouting grpcRouting = GrpcRouting.builder()
                                         .intercept(GrpcSecurity.create(security).securityDefaults(GrpcSecurity.authenticate()))
                                         .register(echoService)
                                         .build();

        // Run the server on port 0 so that it picks a free ephemeral port
        GrpcServerConfiguration serverConfig = GrpcServerConfiguration.builder().port(0).build();

        grpcServer = GrpcServer.create(serverConfig, grpcRouting)
                        .start()
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

        Channel channel = InProcessChannelBuilder.forName(grpcServer.configuration().name()).build();

        adminEchoStub = EchoServiceGrpc.newBlockingStub(channel).withCallCredentials(adminCreds);
        noCredsEchoStub = EchoServiceGrpc.newBlockingStub(channel);
    }

    @AfterAll
    public static void cleanup() {
        grpcServer.shutdown();
        webServer.shutdown();
    }

    // ----- test methods ---------------------------------------------------

    @Test
    public void shouldMakeSecureOutboundCallFromGrpcMethod() {
        Echo.EchoResponse response = adminEchoStub.echo(Echo.EchoRequest.newBuilder().setMessage("foo").build());
        assertThat(response.getMessage(), is("foo"));
    }

    @Test
    public void shouldPropagateCredentialsToOutboundCallFromWebMethod() {
        String message = "testing...";
        String response = client.target(webServerURL)
                .path("/propagate")
                .queryParam("message", message)
                .request()
                .property(HttpAuthenticationFeature.HTTP_AUTHENTICATION_BASIC_USERNAME, "Ted")
                .property(HttpAuthenticationFeature.HTTP_AUTHENTICATION_BASIC_PASSWORD, "secret")
                .get(String.class);

        assertThat(response, is(message));
    }

    @Test
    public void shouldPropagateInvalidCredentialsToOutboundCallFromWebMethod() {
        Response response = client.target(webServerURL)
                .path("/propagate")
                .queryParam("message", "testing...")
                .request()
                .property(HttpAuthenticationFeature.HTTP_AUTHENTICATION_BASIC_USERNAME, "Bob")
                .property(HttpAuthenticationFeature.HTTP_AUTHENTICATION_BASIC_PASSWORD, "password")
                .get();

        assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()));
    }

    @Test
    public void shouldOverrideCredentialsToOutboundCallFromWebMethod() {
        String message = "testing...";
        String response = client.target(webServerURL)
                .path("/override")
                .queryParam("message", message)
                .request()
                .property(HttpAuthenticationFeature.HTTP_AUTHENTICATION_BASIC_USERNAME, "Bob")
                .property(HttpAuthenticationFeature.HTTP_AUTHENTICATION_BASIC_PASSWORD, "password")
                .get(String.class);

        assertThat(response, is(message));
    }

    // ----- helper methods -------------------------------------------------

    private static void echoWebRequest(ServerRequest req, ServerResponse res) {
        String message = req.queryParams().first("message").orElse(null);
        if (message != null) {
            res.send(message);
        } else {
            res.status(Http.ResponseStatus.create(401, "missing message query parameter")).send();
        }
    }

    private static void propagateCredentialsWebRequest(ServerRequest req, ServerResponse res) {
        try {
            GrpcClientSecurity clientSecurity = GrpcClientSecurity.create(req);

            EchoServiceGrpc.EchoServiceBlockingStub stub = noCredsEchoStub.withCallCredentials(clientSecurity);

            String message = req.queryParams().first("message").orElse(null);
            Echo.EchoResponse echoResponse = stub.echo(Echo.EchoRequest.newBuilder().setMessage(message).build());
            res.send(echoResponse.getMessage());
        } catch (StatusRuntimeException e) {
            res.status(GrpcHelper.toHttpResponseStatus(e)).send();
        } catch (Throwable thrown) {
            res.status(Http.ResponseStatus.create(500, thrown.getMessage())).send();
        }
    }

    private static void overrideCredentialsWebRequest(ServerRequest req, ServerResponse res) {
        try {
            GrpcClientSecurity clientSecurity = GrpcClientSecurity.builder(req)
                    .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_USER, "Ted")
                    .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_PASSWORD, "secret")
                    .build();

            EchoServiceGrpc.EchoServiceBlockingStub stub = noCredsEchoStub.withCallCredentials(clientSecurity);

            String message = req.queryParams().first("message").orElse(null);
            Echo.EchoResponse echoResponse = stub.echo(Echo.EchoRequest.newBuilder().setMessage(message).build());
            res.send(echoResponse.getMessage());
        } catch (StatusRuntimeException e) {
            res.status(GrpcHelper.toHttpResponseStatus(e)).send();
        } catch (Throwable thrown) {
            res.status(Http.ResponseStatus.create(500, thrown.getMessage())).send();
        }
    }
}
