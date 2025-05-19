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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.grpc.strings.StringServiceGrpc;
import io.helidon.webserver.grpc.strings.Strings;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class CancelCallTest extends BaseServiceTest {

    protected StringServiceGrpc.StringServiceStub stub;

    CancelCallTest(WebServer server) {
        super(server);
    }

    @BeforeEach
    void beforeEach() {
        super.beforeEach();
        stub = StringServiceGrpc.newStub(channel);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        super.afterEach();
        stub = null;
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(GrpcRouting.builder().service(new StringService()));
    }

    @Test
    void testClientStream() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);
        StreamObserver<Strings.StringMessage> requests = stub.join(new StreamObserver<>() {
            @Override
            public void onNext(Strings.StringMessage value) {
            }

            @Override
            public void onError(Throwable t) {
                latch.countDown();
            }

            @Override
            public void onCompleted() {
            }
        });
        requests.onNext(Strings.StringMessage.newBuilder().setText("helidon").build());
        requests.onError(new RuntimeException("cancel"));
        assertThat(latch.await(5, TimeUnit.SECONDS), is(true));
    }
}
