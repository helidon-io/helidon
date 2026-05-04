/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Status;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValue;
import io.helidon.jsonrpc.core.JsonRpcError;
import io.helidon.jsonrpc.core.JsonRpcResult;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.jsonrpc.JsonRpcClient;
import io.helidon.webclient.jsonrpc.JsonRpcClientBatchRequest;
import io.helidon.webserver.testing.junit5.ServerTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class JsonRpcBatchTest extends JsonRpcBaseTest {

    static final String JSON_RPC_BATCH = "[" + MACHINE_START + "," + MACHINE_STOP + "]";

    JsonRpcBatchTest(Http1Client client, JsonRpcClient jsonRpcClient) {
        super(client, jsonRpcClient);
    }

    @Test
    void testSimpleBatch() {
        JsonRpcClientBatchRequest batch = jsonRpcClient().batch("/rpc/machine");

        batch.rpcMethod("start")
                .rpcId(1)
                .param("when", "NOW")
                .param("duration", "PT0S")
                .addToBatch()
                .rpcMethod("stop")
                .rpcId(2)
                .param("when", "NOW")
                .addToBatch();

        try (var res = batch.submit()) {
            assertThat(res.status(), is(Status.OK_200));
            assertThat(res.size(), is(2));
            Optional<JsonRpcResult> result0 = res.get(0).result();
            assertThat(result0.isPresent(), is(true));
            assertThat(result0.get().asJsonObject().stringValue("status").orElseThrow(), is("RUNNING"));
            Optional<JsonRpcResult> result1 = res.get(1).result();
            assertThat(result1.isPresent(), is(true));
            assertThat(result1.get().asJsonObject().stringValue("status").orElseThrow(), is("STOPPED"));
        }
    }

    @Test
    void testEmptyBatch() {
        JsonRpcClientBatchRequest batch = jsonRpcClient().batch("/rpc/machine");
        try (var res = batch.submit()) {
            assertThat(res.status(), is(Status.OK_200));
            JsonObject object = res.entity().as(JsonObject.class);      // not array
            JsonObject error = object.objectValue("error").orElseThrow();
            assertThat(error.intValue("code").orElseThrow(), is(JsonRpcError.INVALID_REQUEST));
        }
    }

    @Test
    void testParseError() {
        try (var res = client().post("/rpc/machine")
                .contentType(MediaTypes.APPLICATION_JSON)
                .submit(JSON_RPC_BATCH.replace("[", "("))) {
            assertThat(res.status(), is(Status.OK_200));
            JsonObject object = res.entity().as(JsonObject.class);      // not array
            JsonObject error = object.objectValue("error").orElseThrow();
            assertThat(error.intValue("code").orElseThrow(), is(JsonRpcError.PARSE_ERROR));
        }
    }

    @Test
    void testMethodError() {
        try (var res = client().post("/rpc/machine")
                .contentType(MediaTypes.APPLICATION_JSON)
                .submit(JSON_RPC_BATCH.replace("stop", "foo"))) {
            assertThat(res.status(), is(Status.OK_200));
            JsonArray array = res.entity().as(JsonArray.class);
            assertThat(array.values().size(), is(2));
            JsonObject object0 = array.get(0).map(JsonValue::asObject).flatMap(it -> it.objectValue("result")).orElseThrow();
            assertThat(object0.stringValue("status").orElseThrow(), is("RUNNING"));
            JsonObject error = array.get(1).map(JsonValue::asObject).flatMap(it -> it.objectValue("error")).orElseThrow();
            assertThat(error.intValue("code").orElseThrow(), is(JsonRpcError.METHOD_NOT_FOUND));
        }
    }

    @Test
    void testBadBatch() {
        try (var res = client().post("/rpc/machine")
                .contentType(MediaTypes.APPLICATION_JSON)
                .submit("[1, 2, 3]")) {
            assertThat(res.status(), is(Status.OK_200));
            JsonArray array = res.entity().as(JsonArray.class);
            assertThat(array.values().size(), is(3));
            for (int i = 0; i < array.values().size(); i++) {
                JsonObject error = array.get(i).map(JsonValue::asObject).flatMap(it -> it.objectValue("error")).orElseThrow();
                assertThat(error.intValue("code").orElseThrow(), is(JsonRpcError.INVALID_REQUEST));
            }
        }
    }
}
