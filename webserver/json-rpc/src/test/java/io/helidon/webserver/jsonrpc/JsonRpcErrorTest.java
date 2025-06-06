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
package io.helidon.webserver.jsonrpc;

import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.testing.junit5.ServerTest;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;

import static io.helidon.common.media.type.MediaTypes.APPLICATION_JSON;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class JsonRpcErrorTest extends JsonRpcBaseTest {

    static final String JSON_RPC_START_BAD_JSON = """
            {"jsonrpc": "2.0",
                "method":
                "params": { "when" : "NOW", "duration" : "PT0S" },
                "id": 1}
            """;

    JsonRpcErrorTest(Http1Client client) {
        super(client);
    }

    @Test
    void testBadJson() {
        try (var res = client().post("/machine")
                .contentType(APPLICATION_JSON)
                .submit("Not an object or array")) {
            assertThat(res.status(), is(Status.OK_200));
            JsonObject object = res.entity().as(JsonObject.class);
            assertThat(object.get("id"), is(JsonValue.NULL));
            JsonObject error = object.getJsonObject("error");
            assertThat(error.getInt("code"), is(JsonRpcError.PARSE_ERROR));
        }
    }

    @Test
    void testBadJsonRequest() {
        try (var res = client().post("/machine")
                .contentType(APPLICATION_JSON)
                .submit(JSON_RPC_START_BAD_JSON)) {
            assertThat(res.status(), is(Status.OK_200));
            JsonObject object = res.entity().as(JsonObject.class);
            assertThat(object.get("id"), is(JsonValue.NULL));
            JsonObject error = object.getJsonObject("error");
            assertThat(error.getInt("code"), is(JsonRpcError.PARSE_ERROR));
        }
    }

    @Test
    void testInvalidVersion() {
        try (var res = client().post("/machine")
                .contentType(APPLICATION_JSON)
                .submit(MACHINE_START.replace("2.0", "5.0"))) {
            assertThat(res.status(), is(Status.OK_200));
            JsonObject object = res.entity().as(JsonObject.class);
            assertThat(object.get("id"), is(Json.createValue(1)));
            JsonObject error = object.getJsonObject("error");
            assertThat(error.getInt("code"), is(JsonRpcError.INVALID_REQUEST));
        }
    }

    @Test
    void testInvalidMethod() {
        try (var res = client().post("/machine")
                .contentType(APPLICATION_JSON)
                .submit(MACHINE_START.replace("start", "badMethod"))) {
            assertThat(res.status(), is(Status.OK_200));
            JsonObject object = res.entity().as(JsonObject.class);
            assertThat(object.get("id"), is(Json.createValue(1)));
            JsonObject error = object.getJsonObject("error");
            assertThat(error.getInt("code"), is(JsonRpcError.METHOD_NOT_FOUND));
        }
    }

    @Test
    void testInvalidMethodWithErrorHandler() {
        try (var res = client().post("/machine")
                .contentType(APPLICATION_JSON)
                .submit(MACHINE_START.replace("start", "expected"))) {
            assertThat(res.status(), is(Status.OK_200));
            assertThat(res.entity().hasEntity(), is(false));        // error handled
        }
    }

    @Test
    void testStartError() {
        try (var res = client().post("/machine")
                .contentType(APPLICATION_JSON)
                .submit(MACHINE_START.replace("NOW", "LATER"))) {
            assertThat(res.status().code(), is(200));
            JsonObject object = res.entity().as(JsonObject.class);
            assertThat(object.get("id"), is(Json.createValue(1)));
            JsonObject error = object.getJsonObject("error");
            assertThat(error.getInt("code"), is(JsonRpcError.INVALID_PARAMS));
            assertThat(error.getJsonObject("data").getString("reason"), is("Bad param"));
        }
    }

    @Test
    void testStopError() {
        try (var res = client().post("/machine")
                .contentType(APPLICATION_JSON)
                .submit(MACHINE_STOP.replace("NOW", "LATER"))) {
            assertThat(res.status().code(), is(200));
            JsonObject object = res.entity().as(JsonObject.class);
            assertThat(object.get("id"), is(Json.createValue(2)));
            JsonObject error = object.getJsonObject("error");
            assertThat(error.getInt("code"), is(JsonRpcError.INVALID_PARAMS));
            assertThat(error.getJsonObject("data").getString("reason"), is("Bad param"));
        }
    }
}
