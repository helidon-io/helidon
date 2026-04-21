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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.grpc.GrpcConfig;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.grpc.uploads.UploadServiceGrpc;
import io.helidon.webserver.grpc.uploads.Uploads.Ack;
import io.helidon.webserver.grpc.uploads.Uploads.Data;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Verifies that an oversized inbound message causes the server to close the call
 * with gRPC status {@code RESOURCE_EXHAUSTED}, matching grpc-java's behavior in
 * {@code MessageDeframer.processHeader()}:
 * https://github.com/grpc/grpc-java/blob/v1.73.0/core/src/main/java/io/grpc/internal/MessageDeframer.java#L388-L393
 */
@ServerTest
class GrpcMaxMessageSizeErrorTest extends BaseServiceTest {

    private static final int LIMIT = 256 * 1024;

    // One byte over the configured limit
    private static final byte[] DATA_OVER_LIMIT = new byte[LIMIT + 1];

    private UploadServiceGrpc.UploadServiceStub stub;

    GrpcMaxMessageSizeErrorTest(WebServer server) {
        super(server);
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder serverBuilder) {
        serverBuilder.addProtocol(GrpcConfig.builder().maxReadBufferSize(LIMIT).build());
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(GrpcRouting.builder().service(new UploadService()));
    }

    @BeforeEach
    void beforeEach() {
        super.beforeEach();
        stub = UploadServiceGrpc.newStub(channel);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        super.afterEach();
        stub = null;
    }

    @Test
    void oversizedMessageReturnsResourceExhausted() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        StreamObserver<Data> request = stub.upload(new StreamObserver<>() {
            @Override
            public void onNext(Ack value) {
            }

            @Override
            public void onError(Throwable t) {
                errorRef.set(t);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
            }
        });

        request.onNext(Data.newBuilder()
                .setPayload(ByteString.copyFrom(DATA_OVER_LIMIT))
                .build());

        assertThat("Server did not respond within timeout", latch.await(10, TimeUnit.SECONDS), is(true));
        assertThat("Expected an error response", errorRef.get(), is(notNullValue()));

        Status status = Status.fromThrowable(errorRef.get());
        assertThat(status.getCode(), is(Status.Code.RESOURCE_EXHAUSTED));
    }
}
