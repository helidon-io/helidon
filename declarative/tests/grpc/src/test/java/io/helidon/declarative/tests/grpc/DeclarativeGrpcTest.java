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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.declarative.tests.grpc.DeclarativeGrpcProto.GreetingRequest;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.ServerTest;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;

@ServerTest
class DeclarativeGrpcTest {
    private static final String USERNAME = "tomas";
    private static final char[] PASSWORD = "changeit".toCharArray();

    private final ManagedChannel channel;
    private final GreetingServiceGrpc.GreetingServiceBlockingStub blockingStub;
    private final GreetingServiceGrpc.GreetingServiceStub asyncStub;

    DeclarativeGrpcTest(WebServer server) {
        this.channel = ManagedChannelBuilder.forAddress("localhost", server.port())
                .usePlaintext()
                .build();
        this.blockingStub = GreetingServiceGrpc.newBlockingStub(channel);
        this.asyncStub = GreetingServiceGrpc.newStub(channel);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        channel.shutdown();
        assertThat("gRPC channel terminated",
                   channel.awaitTermination(10, TimeUnit.SECONDS),
                   is(true));
    }

    @Test
    void testUnary() {
        var response = blockingStub.greet(request("Tomas"));

        assertThat(response.getMessage(), is("Hello Tomas"));
    }

    @Test
    void testSecureUnary() {
        var request = request("Tomas");

        try {
            var response = blockingStub.secureGreet(request);
            throw new AssertionError("Unauthenticated gRPC call reached application handler: "
                                             + response.getMessage());
        } catch (StatusRuntimeException e) {
            assertThat(e.getStatus().getCode(), is(Code.UNAUTHENTICATED));
        }

        String token = USERNAME + ":" + new String(PASSWORD);
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                    "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8)));
        var response = blockingStub
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                .secureGreet(request);

        assertThat(response.getMessage(), is("Hello Tomas"));
    }

    @Test
    void testServerStreaming() {
        var responses = blockingStub.split(request("Tomas,Helidon"));
        var replies = new ArrayList<DeclarativeGrpcProto.GreetingReply>();
        responses.forEachRemaining(replies::add);

        assertThat(replies,
                   contains(reply("Hello Tomas"), reply("Hello Helidon")));
    }

    @Test
    void testClientStreaming() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        var replies = new ArrayList<DeclarativeGrpcProto.GreetingReply>();
        StreamObserver<GreetingRequest> requests = asyncStub.join(new StreamObserver<>() {
            @Override
            public void onNext(DeclarativeGrpcProto.GreetingReply response) {
                replies.add(response);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                completed.countDown();
            }

            @Override
            public void onCompleted() {
                completed.countDown();
            }
        });

        requests.onNext(request("Tomas"));
        requests.onNext(request("Helidon"));
        requests.onCompleted();

        assertThat("client stream completed",
                   completed.await(10, TimeUnit.SECONDS),
                   is(true));
        assertThat(error.get(), nullValue());
        assertThat(replies, contains(reply("Hello Tomas, Helidon")));
    }

    private static GreetingRequest request(String name) {
        return GreetingRequest.newBuilder()
                .setName(name)
                .build();
    }

    private static DeclarativeGrpcProto.GreetingReply reply(String message) {
        return DeclarativeGrpcProto.GreetingReply.newBuilder()
                .setMessage(message)
                .build();
    }
}
