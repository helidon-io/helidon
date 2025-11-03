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

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.grpc.uploads.UploadServiceGrpc;
import io.helidon.webserver.grpc.uploads.Uploads;
import io.helidon.webserver.grpc.uploads.Uploads.Ack;
import io.helidon.webserver.grpc.uploads.Uploads.Data;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class UploadServiceTest extends BaseServiceTest {

    private static final byte[] DATA_50K = new byte[50 * 1024];
    private static final byte[] DATA_250K = new byte[250 * 1024];
    private static final byte[] DATA_500K = new byte[500 * 1024];

    private static final byte[] DATA_2100K = new byte[2100 * 1024];     // over default limit

    static {
        Arrays.fill(DATA_50K,  (byte) 'A');
        Arrays.fill(DATA_250K, (byte) 'B');
        Arrays.fill(DATA_500K, (byte) 'C');
    }

    private UploadServiceGrpc.UploadServiceStub stub;

    UploadServiceTest(WebServer server) {
        super(server);
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

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(GrpcRouting.builder().service(new UploadService()));
    }

    @Test
    void testUpload() throws Throwable {
        // setup upload call
        CountDownLatch latch = new CountDownLatch(3);
        StreamObserver<Data> request = stub.upload(new StreamObserver<>() {
            @Override
            public void onNext(Ack value) {
                if (value.getOk()) {
                    latch.countDown();
                }
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
            }
        });

        // upload data
        Stream.of(DATA_50K, DATA_250K, DATA_500K)
                .map(b -> Uploads.Data.newBuilder()
                        .setPayload(ByteString.copyFrom(b))
                        .build())
                .forEach(request::onNext);

        // verify upload complete
        assertThat(latch.await(10, TimeUnit.SECONDS), is(true));
    }

    @Test
    void testFailedLargeUpload() throws Throwable {
        // setup bad upload call
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StreamObserver<Data>> requestRef = new AtomicReference<>();
        StreamObserver<Data> request = stub.upload(new StreamObserver<>() {
            @Override
            public void onNext(Ack value) {
            }

            @Override
            public void onError(Throwable t) {
                latch.countDown();
                Objects.requireNonNull(requestRef.get());
                requestRef.get().onError(t);
            }

            @Override
            public void onCompleted() {
            }
        });
        requestRef.set(request);

        // upload data with size over default limit in GrpcConfig
        Stream.of(DATA_2100K)
                .map(b -> Uploads.Data.newBuilder()
                        .setPayload(ByteString.copyFrom(b))
                        .build())
                .forEach(request::onNext);

        // verify upload failed
        assertThat(latch.await(10, TimeUnit.SECONDS), is(true));
    }
}
