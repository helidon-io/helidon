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

package io.helidon.grpc.examples.security.outbound;

import java.util.Optional;
import java.util.logging.LogManager;

import io.helidon.common.context.Context;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.grpc.core.GrpcHelper;
import io.helidon.grpc.examples.common.Greet;
import io.helidon.grpc.examples.common.StringService;
import io.helidon.grpc.examples.common.StringServiceGrpc;
import io.helidon.grpc.examples.common.Strings;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import io.helidon.grpc.server.GrpcService;
import io.helidon.grpc.server.ServiceDescriptor;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.integration.grpc.GrpcClientSecurity;
import io.helidon.security.integration.grpc.GrpcSecurity;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;

import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.StreamObserver;

import static io.helidon.grpc.core.ResponseHelper.complete;

/**
 * An example server that configures services with outbound security.
 */
public class SecureServer {

    private static GrpcServer grpcServer;

    private static WebServer webServer;

    private SecureServer() {
    }

    /**
     * Program entry point.
     *
     * @param args the program command line arguments
     * @throws Exception if there is a program error
     */
    public static void main(String[] args) throws Exception {
        LogManager.getLogManager().readConfiguration(
                SecureServer.class.getResourceAsStream("/logging.properties"));

        Config config = Config.create();

        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.create(config.get("http-basic-auth")))
                .build();

        grpcServer = createGrpcServer(config.get("grpc"), security);
        webServer = createWebServer(config.get("webserver"), security);
    }

    /**
     * Create the gRPC server.
     */
    private static GrpcServer createGrpcServer(Config config, Security security) {

        GrpcRouting grpcRouting = GrpcRouting.builder()
                // Add the security interceptor with a default of allowing any authenticated user
                .intercept(GrpcSecurity.create(security).securityDefaults(GrpcSecurity.authenticate()))
                // add the StringService with required role "admin"
                .register(new StringService(), GrpcSecurity.rolesAllowed("admin"))
                // add the GreetService (picking up the default security of any authenticated user)
                .register(new GreetService())
                .build();

        GrpcServer grpcServer = GrpcServer.create(GrpcServerConfiguration.create(config), grpcRouting);

        grpcServer.start()
                .thenAccept(s -> {
                        System.out.println("gRPC server is UP! http://localhost:" + s.port());
                        s.whenShutdown().thenRun(() -> System.out.println("gRPC server is DOWN. Good bye!"));
                        })
                .exceptionally(t -> {
                        System.err.println("gRPC server startup failed: " + t.getMessage());
                        t.printStackTrace(System.err);
                        return null;
                        });

        return grpcServer;
    }

    /**
     * Create the web server.
     */
    private static WebServer createWebServer(Config config, Security security) {

        Routing routing = Routing.builder()
                                 .register(WebSecurity.create(security).securityDefaults(WebSecurity.authenticate()))
                                 .register(new RestService())
                                 .build();

        WebServer webServer = WebServer.create(ServerConfiguration.create(config), routing);

        webServer.start()
                .thenAccept(s -> {
                        System.out.println("Web server is UP! http://localhost:" + s.port());
                        s.whenShutdown().thenRun(() -> System.out.println("gRPC server is DOWN. Good bye!"));
                        })
                .exceptionally(t -> {
                        System.err.println("Web server startup failed: " + t.getMessage());
                        t.printStackTrace(System.err);
                        return null;
                        });

        return webServer;
    }

    /**
     * A gRPC greet service that uses outbound security to
     * access a ReST API.
     */
    public static class GreetService
            implements GrpcService {

        /**
         * The current greeting.
         */
        private String greeting = "hello";

        /**
         * The Helidon WebClient to use to make ReST calls.
         */
        private WebClient client;

        private GreetService() {
            client = WebClient.create();
        }

        @Override
        public void update(ServiceDescriptor.Rules rules) {
            rules.proto(Greet.getDescriptor())
                    .unary("Greet", this::greet)
                    .unary("SetGreeting", this::setGreeting);
        }

        /**
         * This method calls a secure ReST endpoint using the caller's credentials.
         *
         * @param request   the request
         * @param observer  the observer to send the response to
         */
        private void greet(Greet.GreetRequest request, StreamObserver<Greet.GreetResponse> observer) {
            // Obtain the greeting name from the request (default to "World".
            String name = Optional.ofNullable(request.getName()).orElse("World");

            // Obtain the security context from the current gRPC context
            SecurityContext securityContext = GrpcSecurity.SECURITY_CONTEXT.get();
            Context context = Context.builder().id("example").build();
            context.register(securityContext);

            // Use the current credentials call the "lower" ReST endpoint which will call
            // the "Lower" method on the secure gRPC StringService.
            client.get()
                    .uri("http://localhost:" + webServer.port())
                    .path("lower")
                    .queryParam("value", name)
                    .context(context)
                    .request()
                    .thenAccept(it -> handleResponse(it, observer))
                    .exceptionally(throwable -> {
                        observer.onError(throwable);
                        return null;
                    });
        }

        /**
         * This method calls a secure ReST endpoint overriding the caller's credentials and
         * using the admin user's credentials.
         *
         * @param request   the request
         * @param observer  the observer to send the response to
         */
        private void setGreeting(Greet.SetGreetingRequest request, StreamObserver<Greet.SetGreetingResponse> observer) {
            // Obtain the greeting name from the request (default to "hello".
            String name = Optional.ofNullable(request.getGreeting()).orElse("hello");

            // Obtain the security context from the current gRPC context
            SecurityContext securityContext = GrpcSecurity.SECURITY_CONTEXT.get();
            Context context = Context.builder().id("example").build();
            context.register(securityContext);

            // Use the admin user's credentials call the "upper" ReST endpoint which will call
            // the "Upper" method on the secure gRPC StringService.
            client.get().uri("http://127.0.0.1:" + webServer.port())
                    .path("upper")
                    .queryParam("value", name)
                    .context(context)
                    .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_USER, "Ted")
                    .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_PASSWORD, "secret")
                    .request()
                    .thenAccept(it -> handleResponse(it, observer))
                    .exceptionally(throwable -> {
                        observer.onError(throwable);
                        return null;
                    });
        }

        private void handleResponse(WebClientResponse response, StreamObserver observer) {
            if (response.status() == Http.Status.OK_200) {
                // Send the response to the caller of the current greeting and lower case name
                response.content()
                        .as(String.class)
                        .thenAccept(str -> complete(observer,
                                                    Greet.SetGreetingResponse.newBuilder().setGreeting(str).build()));
            } else {
                completeWithError(response, observer);
            }
        }

        private void completeWithError(WebClientResponse response, StreamObserver observer) {
            Http.ResponseStatus status = response.status();

            if (status == Http.Status.UNAUTHORIZED_401
                       || status == Http.Status.FORBIDDEN_403){
                observer.onError(Status.PERMISSION_DENIED.asRuntimeException());
            } else {
                response.content()
                        .as(String.class)
                        .thenAccept(str -> observer.onError(Status.INTERNAL.withDescription(str).asRuntimeException()));
            }
        }

        @Override
        public String name() {
            return "GreetService";
        }
    }

    /**
     * A ReST service that calls the gRPC StringService to mutate String values.
     */
    public static class RestService
            implements Service {

        private Channel channel;

        @Override
        public void update(Routing.Rules rules) {
            rules.get("/lower", WebSecurity.rolesAllowed("user"), this::lower)
                 .get("/upper", WebSecurity.rolesAllowed("user"), this::upper);
        }

        /**
         * Call the gRPC StringService Lower method overriding the caller's credentials and
         * using the admin user's credentials.
         *
         * @param req  the http request
         * @param res  the http response
         */
        private void lower(ServerRequest req, ServerResponse res) {
            try {
                // Create the gRPC client security credentials from the current request
                // overriding with the admin user's credentials
                GrpcClientSecurity clientSecurity = GrpcClientSecurity.builder(req)
                        .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_USER, "Ted")
                        .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_PASSWORD, "secret")
                        .build();

                StringServiceGrpc.StringServiceBlockingStub stub = StringServiceGrpc.newBlockingStub(ensureChannel())
                        .withCallCredentials(clientSecurity);

                String value = req.queryParams().first("value").orElse(null);
                Strings.StringMessage response = stub.lower(Strings.StringMessage.newBuilder().setText(value).build());

                res.status(200).send(response.getText());
            } catch (StatusRuntimeException e) {
                res.status(GrpcHelper.toHttpResponseStatus(e.getStatus())).send();
            }
        }

        /**
         * Call the gRPC StringService Upper method using the current caller's credentials.
         *
         * @param req  the http request
         * @param res  the http response
         */
        private void upper(ServerRequest req, ServerResponse res) {
            try {
                // Create the gRPC client security credentials from the current request
                GrpcClientSecurity clientSecurity = GrpcClientSecurity.create(req);

                StringServiceGrpc.StringServiceBlockingStub stub = StringServiceGrpc.newBlockingStub(ensureChannel())
                        .withCallCredentials(clientSecurity);

                String value = req.queryParams().first("value").orElse(null);
                Strings.StringMessage response = stub.upper(Strings.StringMessage.newBuilder().setText(value).build());

                res.status(200).send(response.getText());
            } catch (StatusRuntimeException e) {
                res.status(GrpcHelper.toHttpResponseStatus(e.getStatus())).send();
            }
        }

        private synchronized Channel ensureChannel() {
            if (channel == null) {
                channel = InProcessChannelBuilder.forName(grpcServer.configuration().name()).build();
            }
            return channel;
        }
    }
}
