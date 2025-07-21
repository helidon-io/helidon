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

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.jsonrpc.JsonRpcClient;
import io.helidon.webserver.testing.junit5.ServerTest;

import jakarta.json.Json;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class JsonRpcTest extends JsonRpcBaseTest {

    JsonRpcTest(Http1Client client, JsonRpcClient jsonRpcClient) {
        super(client, jsonRpcClient);
    }

    @Test
    void testStart() {
        try (var res = jsonRpcClient().rpcMethod("start")
                .rpcId(1)
                .param("when","NOW")
                .param("duration", "PT0S")
                .path("/rpc/machine")
                .submit()) {
            assertThat(res.status(), is(Status.OK_200));
            assertThat(res.rpcId(), is(Optional.of(Json.createValue(1))));
            assertThat(res.result().isPresent(), is(true));
            StartStopResult result = res.result().get().as(StartStopResult.class);
            assertThat(result.status(), is("RUNNING"));
        }
    }

    @Test
    void testStop() {
        try (var res = jsonRpcClient().rpcMethod("stop")
                .rpcId(2)
                .param("when","NOW")
                .path("/rpc/machine")
                .submit()) {
            assertThat(res.status(), is(Status.OK_200));
            assertThat(res.rpcId(), is(Optional.of(Json.createValue(2))));
            assertThat(res.result().isPresent(), is(true));
            StartStopResult result = res.result().get().as(StartStopResult.class);
            assertThat(result.status(), is("STOPPED"));
        }
    }

    @Test
    void testAddArray() {
        try (var res = jsonRpcClient().rpcMethod("add")
                .rpcId(1)
                .addParam(20)
                .addParam(25)
                .path("/rpc/calculator")
                .submit()) {
            assertThat(res.status(), is(Status.OK_200));
            assertThat(res.rpcId(), is(Optional.of(Json.createValue(1))));
            assertThat(res.result().isPresent(), is(true));
            JsonValue result = res.result().get().asJsonValue();
            assertThat(result, is(Json.createValue(45)));
        }
    }

    @Test
    void testNotification() {
        try (var res = jsonRpcClient().rpcMethod("ping")
                .path("/rpc/notifier")
                .submit()) {
            assertThat(res.status(), is(Status.OK_200));
            assertThat(res.result().isEmpty(), is(true));
        }
    }
}
