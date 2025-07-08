/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.grpc.GrpcReflectionFeature;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import io.grpc.reflection.v1alpha.ExtensionRequest;
import io.grpc.reflection.v1alpha.FileDescriptorResponse;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionResponse;
import io.grpc.reflection.v1alpha.ServiceResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

/**
 * Reflection service test for version v1alpha.
 *
 * @see io.helidon.webserver.tests.grpc.ReflectionServiceTest
 */
@ServerTest
class ReflectionServiceV1AlphaTest extends BaseServiceTest {

    private ServerReflectionGrpc.ServerReflectionStub stub;

    ReflectionServiceV1AlphaTest(WebServer server) {
        super(server);
    }

    @SetUpServer
    static void setupServer(WebServerConfig.Builder builder) {
        builder.addFeature(GrpcReflectionFeature.builder().enabled(true).build());
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(GrpcRouting.builder().service(new StringService()));
    }

    @BeforeEach
    void beforeEach() {
        super.beforeEach();
        stub = ServerReflectionGrpc.newStub(channel);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        super.afterEach();
        stub = null;
    }

    @Test
    void testList() throws InterruptedException {
        TestObserver<ServerReflectionResponse> res = new TestObserver<>(1);
        StreamObserver<ServerReflectionRequest> req = stub.serverReflectionInfo(res);
        req.onNext(ServerReflectionRequest.newBuilder()
                           .setListServices("*")
                           .build());
        req.onCompleted();
        res.await(5, TimeUnit.SECONDS);
        List<ServerReflectionResponse> responses = res.getResponses();
        assertThat(responses.size(), is(1));
        ServerReflectionResponse response = responses.getFirst();
        List<ServiceResponse> serviceResponses = response.getListServicesResponse().getServiceList();
        Set<String> names = serviceResponses.stream()
                .map(ServiceResponse::getName)
                .collect(Collectors.toSet());
        assertThat(names, hasItems("StringService",
                                   "grpc.reflection.v1.ServerReflection",
                                   "grpc.reflection.v1alpha.ServerReflection"));
    }

    /**
     * Tests find symbol for service, method and type.
     *
     * @param symbol the symbol to look for
     * @throws InterruptedException if the waiting time expires
     */
    @ParameterizedTest
    @CsvSource({"StringService", "StringService.Upper", "StringMessage"})
    void testFindSymbol(String symbol) throws InterruptedException {
        TestObserver<ServerReflectionResponse> res = new TestObserver<>(1);
        StreamObserver<ServerReflectionRequest> req = stub.serverReflectionInfo(res);
        req.onNext(ServerReflectionRequest.newBuilder()
                           .setFileContainingSymbol(symbol)
                           .build());
        req.onCompleted();
        res.await(5, TimeUnit.SECONDS);
        List<ServerReflectionResponse> responses = res.getResponses();
        assertThat(responses.size(), is(1));
        ServerReflectionResponse response = responses.getFirst();
        FileDescriptorResponse fileResponse = response.getFileDescriptorResponse();
        assertThat(fileResponse.getFileDescriptorProtoCount(), is(1));
        assertThat(fileResponse.getFileDescriptorProto(0).size(), greaterThan(1));
    }

    @Test
    void testFile() throws InterruptedException {
        TestObserver<ServerReflectionResponse> res = new TestObserver<>(1);
        StreamObserver<ServerReflectionRequest> req = stub.serverReflectionInfo(res);
        req.onNext(ServerReflectionRequest.newBuilder()
                           .setFileByFilename("strings.proto")
                           .build());
        req.onCompleted();
        res.await(500, TimeUnit.SECONDS);
        List<ServerReflectionResponse> responses = res.getResponses();
        assertThat(responses.size(), is(1));
        ServerReflectionResponse response = responses.getFirst();
        FileDescriptorResponse fileResponse = response.getFileDescriptorResponse();
        assertThat(fileResponse.getFileDescriptorProtoCount(), is(1));
        assertThat(fileResponse.getFileDescriptorProto(0).size(), greaterThan(1));
    }

    @Test
    void testExtension() throws InterruptedException {
        TestObserver<ServerReflectionResponse> res = new TestObserver<>(1);
        StreamObserver<ServerReflectionRequest> req = stub.serverReflectionInfo(res);
        req.onNext(ServerReflectionRequest.newBuilder()
                           .setFileContainingExtension(
                                   ExtensionRequest.newBuilder()
                                           .setContainingType("StringMessage")
                                           .setExtensionNumber(100)
                                           .build())
                           .build());
        req.onCompleted();
        res.await(500, TimeUnit.SECONDS);
        List<ServerReflectionResponse> responses = res.getResponses();
        assertThat(responses.size(), is(1));
        ServerReflectionResponse response = responses.getFirst();
        FileDescriptorResponse fileResponse = response.getFileDescriptorResponse();
        assertThat(fileResponse.getFileDescriptorProtoCount(), is(1));
        assertThat(fileResponse.getFileDescriptorProto(0).size(), greaterThan(1));
    }
}
