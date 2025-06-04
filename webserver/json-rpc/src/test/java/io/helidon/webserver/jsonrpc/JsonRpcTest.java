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

import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static io.helidon.common.media.type.MediaTypes.APPLICATION_JSON;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class JsonRpcTest extends JsonRpcBaseTest {

    JsonRpcTest(Http1Client client) {
        super(client);
    }

    @Test
    void testStart() {
        try (var res = client().post("/machine")
                .contentType(APPLICATION_JSON)
                .submit(MACHINE_START)) {
            assertThat(res.status(), is(Status.OK_200));
            JsonObject json = res.as(JsonObject.class).getJsonObject("result");
            assertThat(json.getString("status"), is("RUNNING"));
        }
    }

    @Test
    void testStop() {
        try (var res = client().post("/machine")
                .contentType(APPLICATION_JSON)
                .submit(MACHINE_STOP)) {
            assertThat(res.status(), is(Status.OK_200));
            JsonObject json = res.as(JsonObject.class).getJsonObject("result");
            assertThat(json.getString("status"), is("STOPPED"));
        }
    }

    @Test
    void testAddArray() {
        try (var res = client().post("/calculator")
                .contentType(APPLICATION_JSON)
                .submit(CALCULATOR_ADD_ARRAY)) {
            assertThat(res.status(), is(Status.OK_200));
            int sum = res.as(JsonObject.class).getInt("result");
            assertThat(sum, is(45));
        }
    }

    @Test
    void testAddObject() {
        try (var res = client().post("/calculator")
                .contentType(APPLICATION_JSON)
                .submit(CALCULATOR_ADD_OBJECT)) {
            assertThat(res.status(), is(Status.OK_200));
            int sum = res.as(JsonObject.class).getInt("result");
            assertThat(sum, is(45));
        }
    }
}
