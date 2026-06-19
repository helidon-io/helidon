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

package io.helidon.declarative.tests.grpc;

import java.util.List;

import io.helidon.declarative.tests.grpc.DeclarativeGrpcProto.GreetingReply;
import io.helidon.declarative.tests.grpc.DeclarativeGrpcProto.GreetingRequest;
import io.helidon.grpc.api.Grpc;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;

import io.grpc.stub.StreamObserver;

final class ClientConfigGreetingClients {
    static final String SERVICE_NAME = "io.helidon.declarative.tests.grpc.GreetingService";
    static final String CONFIG_KEY = "declarative.grpc.clients.configured";
    static final String SERVER_URI = "http://localhost:${test.server.port}";
    static final String INVALID_URI = "http://localhost:1";
    static final String NAMED_CLIENT = "declarative-grpc-client";
    static final String BROKEN_CLIENT = "declarative-grpc-broken-client";
    static final String MISSING_CLIENT = "declarative-grpc-missing-client";

    private ClientConfigGreetingClients() {
    }
}

@GrpcClient.Endpoint(value = ClientConfigGreetingClients.SERVER_URI,
                     configKey = ClientConfigGreetingClients.CONFIG_KEY,
                     clientName = ClientConfigGreetingClients.BROKEN_CLIENT)
@Grpc.GrpcService(ClientConfigGreetingClients.SERVICE_NAME)
interface ConfiguredGreetingClient {
    @Grpc.Unary("Greet")
    GreetingReply greet(GreetingRequest request);
}

@GrpcClient.Endpoint(value = ClientConfigGreetingClients.INVALID_URI,
                     clientName = ClientConfigGreetingClients.NAMED_CLIENT)
@Grpc.GrpcService(ClientConfigGreetingClients.SERVICE_NAME)
interface NamedRegistryGreetingClient {
    @Grpc.Unary("Greet")
    GreetingReply greet(GreetingRequest request);
}

@GrpcClient.Endpoint(ClientConfigGreetingClients.INVALID_URI)
@Grpc.GrpcService(ClientConfigGreetingClients.SERVICE_NAME)
interface DefaultRegistryGreetingClient {
    @Grpc.Unary("Greet")
    GreetingReply greet(GreetingRequest request);
}

@Service.Singleton
class TestGrpcClientFactory implements Service.ServicesFactory<GrpcClient> {
    private final WebServer defaultServer;
    private final WebServer namedServer;

    @Service.Inject
    TestGrpcClientFactory() {
        this.defaultServer = server("Default registry").start();
        this.namedServer = server("Named registry").start();
    }

    @Override
    public List<Service.QualifiedInstance<GrpcClient>> services() {
        return List.of(Service.QualifiedInstance.create(client(uri(defaultServer))),
                       Service.QualifiedInstance.create(client(uri(namedServer)),
                                                        Qualifier.createNamed(
                                                                ClientConfigGreetingClients.NAMED_CLIENT)),
                       Service.QualifiedInstance.create(client(ClientConfigGreetingClients.INVALID_URI),
                                                        Qualifier.createNamed(
                                                                ClientConfigGreetingClients.BROKEN_CLIENT)));
    }

    @Service.PreDestroy
    void shutdown() {
        namedServer.stop();
        defaultServer.stop();
    }

    private static GrpcClient client(String uri) {
        return GrpcClient.builder()
                .baseUri(uri)
                .tls(it -> it.enabled(false))
                .build();
    }

    private static WebServer server(String prefix) {
        return WebServer.builder()
                .addRouting(GrpcRouting.builder()
                                    .unary(DeclarativeGrpcProto.getDescriptor(),
                                           ClientConfigGreetingClients.SERVICE_NAME,
                                           "Greet",
                                           (GreetingRequest request, StreamObserver<GreetingReply> observer) -> {
                                               observer.onNext(GreetingReply.newBuilder()
                                                               .setMessage(prefix + ": " + request.getName())
                                                               .build());
                                               observer.onCompleted();
                                           }))
                .build();
    }

    private static String uri(WebServer server) {
        return "http://localhost:" + server.port();
    }
}
