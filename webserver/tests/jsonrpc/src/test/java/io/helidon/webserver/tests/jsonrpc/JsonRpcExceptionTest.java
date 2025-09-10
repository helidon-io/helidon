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

import jakarta.json.Json;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class JsonRpcExceptionTest {

    private static final String MESSAGE1 = "message1";
    private static final String MESSAGE2 = "message2";
    private static final CountDownLatch LATCH = new CountDownLatch(1);

    private final JsonRpcClient client;

    JsonRpcExceptionTest(JsonRpcClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        JsonRpcHandlers jsonRpcHandlers = JsonRpcHandlers.builder()
                .method("ping1", JsonRpcExceptionTest::ping1)
                .exception(CustomException1.class, (req, res, t) -> {
                    // verify message and update latch
                    assertThat(t.getMessage(), is(MESSAGE1));
                    LATCH.countDown();

                    // mapp to a new JSON-RPC error
                    return Optional.of(JsonRpcError.create(-10000, t.getMessage()));
                })
                .method("ping2", JsonRpcExceptionTest::ping2)
                .exception(CustomException2.class, (req, res, t) -> {
                    // verify message and update latch
                    assertThat(t.getMessage(), is(MESSAGE2));
                    LATCH.countDown();

                    // update response and do not return an error!
                    res.result(Json.createValue(MESSAGE2));
                    return Optional.empty();
                })
                .build();
        JsonRpcRouting jsonRpcRouting = JsonRpcRouting.builder()
                .register("/rpc", jsonRpcHandlers)
                .build();
        builder.register("/", jsonRpcRouting);
    }

    @Test
    void testCustomException1() throws InterruptedException {
        try (JsonRpcClientResponse res = client.rpcMethod("ping1")
                .path("/rpc")
                .submit()) {
            assertThat(res.error().isPresent(), is(true));
            assertThat(res.error().get().code(), is(-10000));
            assertThat(res.error().get().message(), is(MESSAGE1));
        }
        assertThat(LATCH.await(10, TimeUnit.SECONDS), is(true));
    }

    @Test
    void testCustomException2() throws InterruptedException {
        try (JsonRpcClientResponse res = client.rpcMethod("ping2")
                .path("/rpc")
                .submit()) {
            assertThat(res.error().isPresent(), is(false));
            assertThat(res.result().isPresent(), is(true));
            assertThat(res.result().get().asJsonValue(), is(Json.createValue(MESSAGE2)));
        }
        assertThat(LATCH.await(10, TimeUnit.SECONDS), is(true));
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
}
