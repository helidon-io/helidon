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

package io.helidon.microprofile.grpc.tests;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.grpc.reflection.v1.FileDescriptorResponse;
import io.grpc.reflection.v1.ServerReflectionGrpc;
import io.grpc.reflection.v1.ServerReflectionRequest;
import io.grpc.reflection.v1.ServerReflectionResponse;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

class ReflectionServiceTest extends BaseServiceTest {

    ServerReflectionGrpc.ServerReflectionStub stub;

    @Inject
    public ReflectionServiceTest(WebTarget webTarget) {
        super(webTarget);
        stub = ServerReflectionGrpc.newStub(grpcClient().channel());
    }

    @ParameterizedTest
    @CsvSource({"EchoService", "EchoService.Echo"})
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
}
