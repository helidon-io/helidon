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
package io.helidon.webserver.jsonrpc;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.json.JsonNumber;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class JsonRpcMediaDependencyTest {
    private final Http1Client client;

    JsonRpcMediaDependencyTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        JsonRpcRouting jsonRpcRouting = JsonRpcRouting.builder()
                .register("/calculator", "add", (req, res) -> {
                    int left = req.params().get(0).asNumber().intValue();
                    int right = req.params().get(1).asNumber().intValue();

                    res.result(JsonNumber.create(left + right))
                            .send();
                })
                .build();

        routing.register("/rpc", jsonRpcRouting);
    }

    @Test
    void serverModuleProvidesJsonMediaSupport() {
        try (Http1ClientResponse response = client.post("/rpc/calculator")
                .contentType(MediaTypes.APPLICATION_JSON)
                .submit("""
                        {"jsonrpc":"2.0","method":"add","params":[20,25],"id":1}
                        """)) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers().first(HeaderNames.CONTENT_TYPE).orElseThrow(),
                       is(MediaTypes.APPLICATION_JSON_VALUE));
            assertThat(response.entity().as(String.class), containsString("\"result\":45"));
        }
    }
}
