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
package io.helidon.webserver.tests.jsonrpc;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.jsonrpc.core.JsonRpcError;
import io.helidon.webclient.jsonrpc.JsonRpcClient;
import io.helidon.webclient.jsonrpc.JsonRpcClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.jsonrpc.JsonRpcHandlers;
import io.helidon.webserver.jsonrpc.JsonRpcRequest;
import io.helidon.webserver.jsonrpc.JsonRpcResponse;
import io.helidon.webserver.jsonrpc.JsonRpcRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class JsonRpcExceptionTest {

    private static final String MESSAGE1 = "message1";
    private static final String MESSAGE2 = "message2";
    private static final AtomicReference<CountDownLatch> LATCH = new AtomicReference<>();

    private final JsonRpcClient client;

    JsonRpcExceptionTest(JsonRpcClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        JsonRpcHandlers jsonRpcHandlers = JsonRpcHandlers.builder()

                // these ping methods all throw exceptions
                .method("ping1", JsonRpcExceptionTest::ping1)
                .method("ping2", JsonRpcExceptionTest::ping2)
                .method("ping3", JsonRpcExceptionTest::ping3)

                // these mappers apply to all methods and must be registered in order
                .exception(CustomException1.class, (req, res, t) -> {
                    // verify message and update latch
                    assertThat(t.getMessage(), is(MESSAGE1));
                    LATCH.get().countDown();

                    // map to a new JSON-RPC error
                    return Optional.of(JsonRpcError.create(-10001, t.getMessage()));
                })
                .exception(CustomException2.class, (req, res, t) -> {
                    // verify message and update latch
                    assertThat(t.getMessage(), is(MESSAGE2));
                    LATCH.get().countDown();

                    // map to a new JSON-RPC error
                    return Optional.of(JsonRpcError.create(-10002, t.getMessage()));
                })
                .exception(Throwable.class, (req, res, t) -> {      // catch all exception handler
                    // updates latch
                    LATCH.get().countDown();

                    // do not return an error, sends internal error
                    return Optional.empty();
                })
                .build();
        JsonRpcRouting jsonRpcRouting = JsonRpcRouting.builder()
                .register("/rpc", jsonRpcHandlers)
                .build();
        builder.register("/", jsonRpcRouting);
    }

    @BeforeEach
    void beforeEach() {
        LATCH.set(new CountDownLatch(1));
    }

    @Test
    void testPing1() throws InterruptedException {
        try (JsonRpcClientResponse res = client.rpcMethod("ping1")
                .rpcId(1)
                .path("/rpc")
                .submit()) {
            assertThat(res.rpcId().isPresent(), is(true));
            assertThat(res.rpcId().get().toString(), is("1"));
            assertThat(res.error().isPresent(), is(true));
            assertThat(res.error().get().code(), is(-10001));
            assertThat(res.error().get().message(), is(MESSAGE1));
        }
        assertThat(LATCH.get().await(10, TimeUnit.SECONDS), is(true));
    }

    @Test
    void testPing2() throws InterruptedException {
        try (JsonRpcClientResponse res = client.rpcMethod("ping2")
                .rpcId(1)
                .path("/rpc")
                .submit()) {
            assertThat(res.rpcId().isPresent(), is(true));
            assertThat(res.rpcId().get().toString(), is("1"));
            assertThat(res.error().isPresent(), is(true));
            assertThat(res.error().get().code(), is(-10002));
            assertThat(res.error().get().message(), is(MESSAGE2));
        }
        assertThat(LATCH.get().await(10, TimeUnit.SECONDS), is(true));
    }

    @Test
    void testPing3() throws InterruptedException {
        try (JsonRpcClientResponse res = client.rpcMethod("ping3")
                .rpcId(1)
                .path("/rpc")
                .submit()) {
            assertThat(res.rpcId().isPresent(), is(true));
            assertThat(res.rpcId().get().toString(), is("1"));
            assertThat(res.error().isPresent(), is(true));
            assertThat(res.error().get().code(), is(JsonRpcError.INTERNAL_ERROR));
        }
        assertThat(LATCH.get().await(10, TimeUnit.SECONDS), is(true));
    }

    static void ping1(JsonRpcRequest jsonRpcRequest, JsonRpcResponse response) {
        throw new CustomException1();
    }

    private static class CustomException1 extends RuntimeException {
        private CustomException1() {
            super(MESSAGE1);
        }
    }
    static void ping2(JsonRpcRequest jsonRpcRequest, JsonRpcResponse response) {
        throw new CustomException2();
    }

    private static class CustomException2 extends RuntimeException {
        private CustomException2() {
            super(MESSAGE2);
        }
    }

    static void ping3(JsonRpcRequest jsonRpcRequest, JsonRpcResponse response) {
        throw new RuntimeException("Oops");
    }
}
