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

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static io.helidon.common.media.type.MediaTypes.APPLICATION_JSON;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class JsonRpcBatchTest extends JsonRpcBaseTest {

    static final String JSON_RPC_BATCH = "[" + JSON_RPC_START + "," + JSON_RPC_STOP + "]";

    JsonRpcBatchTest(Http1Client client) {
        super(client);
    }
    
    @Test
    void testSimpleBatch() {
        try (var res = client().post("/jsonrpc")
                .contentType(APPLICATION_JSON)
                .submit(JSON_RPC_BATCH)) {
            assertThat(res.status(), is(Status.OK_200));
            JsonArray array = res.entity().as(JsonArray.class);
            assertThat(array.size(), is(2));
            JsonObject object0 = array.getJsonObject(0).getJsonObject("result");
            assertThat(object0.getString("status"), is("RUNNING"));
            JsonObject object1 = array.getJsonObject(1).getJsonObject("result");
            assertThat(object1.getString("status"), is("STOPPED"));
        }
    }
}
