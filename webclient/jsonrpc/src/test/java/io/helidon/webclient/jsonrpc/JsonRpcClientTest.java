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
package io.helidon.webclient.jsonrpc;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.json.JsonException;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.webclient.api.SniConfig;
import io.helidon.webclient.api.SniMode;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
class JsonRpcClientTest {
    private final URI serverUri;

    JsonRpcClientTest(URI serverUri) {
        this.serverUri = serverUri;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.post("/rpc", (req, res) -> {
            JsonObject request = req.content().as(JsonObject.class);

            assertThat(request.value("id").orElseThrow().asNumber().intValue(), is(1));
            assertThat(request.stringValue("method").orElseThrow(), is("start"));
            assertThat(request.objectValue("params")
                               .flatMap(it -> it.value("when"))
                               .orElseThrow(),
                       is(JsonString.create("NOW")));

            res.header(HeaderValues.CONTENT_TYPE_JSON);
            res.send("""
                    {"jsonrpc":"2.0","id":1,"result":{"status":"RUNNING"}}
                    """.getBytes(StandardCharsets.UTF_8));
        });
        rules.post("/malformed", (req, res) -> {
            res.header(HeaderValues.CONTENT_TYPE_JSON);
            res.send("""
                    {"jsonrpc":"2.0","id":1,"error":
                    """.getBytes(StandardCharsets.UTF_8));
        });
    }

    @Test
    void testCreate() {
        JsonRpcClient client = JsonRpcClient.create();
        assertThat(client, notNullValue());
    }

    @Test
    void testCreateFromWebClient() {
        WebClient webClient = WebClient.create();
        JsonRpcClient client = webClient.client(JsonRpcClient.PROTOCOL);
        assertThat(client, notNullValue());
    }

    @Test
    void testRequestCreation() {
        JsonRpcClient client = JsonRpcClient.builder()
                .baseUri("https://example.com")
                .build();
        assertThat(client, notNullValue());

        JsonRpcClientRequest req = client.rpcMethod("start")
                .path("/machine")
                .rpcId(1)
                .param("foo", JsonString.create("bar"));
        assertThat(req, notNullValue());

        JsonObject request = req.asJsonObject();
        assertThat(request.value("id").orElseThrow().asNumber().intValue(), is(1));
        assertThat(request.stringValue("method").orElseThrow(), is("start"));
        assertThat(request.objectValue("params")
                           .flatMap(it -> it.value("foo"))
                           .orElseThrow(),
                   is(JsonString.create("bar")));
    }

    @Test
    void testRequestLevelSniDelegatesToHttp1Request() {
        AtomicReference<SniConfig> configuredSni = new AtomicReference<>();
        Http1ClientRequest http1Request = http1Request(configuredSni);
        Http1Client http1Client = http1Client(http1Request);
        JsonRpcClientRequest request = new JsonRpcClientRequestImpl(http1Client, "start");
        SniConfig sni = SniConfig.builder()
                .mode(SniMode.EXPLICIT)
                .host("service.example")
                .build();

        assertThat(request.sni(sni), sameInstance(request));
        assertThat(configuredSni.get(), sameInstance(sni));
    }

    @Test
    void testSubmitWithHelidonJsonMedia() {
        JsonRpcClient jsonRpcClient = JsonRpcClient.builder()
                .baseUri(serverUri)
                .build();

        try (JsonRpcClientResponse res = jsonRpcClient.rpcMethod("start")
                .path("/rpc")
                .rpcId(1)
                .param("when", "NOW")
                .submit()) {
            assertThat(res.status(), is(Status.OK_200));
            assertThat(res.rpcId().map(value -> value.asNumber().intValue()), is(Optional.of(1)));
            assertThat(res.result().orElseThrow().getString("status"), is("RUNNING"));
        }
    }

    @Test
    void testMalformedResponseJsonPropagatesFromError() {
        JsonRpcClient jsonRpcClient = JsonRpcClient.builder()
                .baseUri(serverUri)
                .build();

        try (JsonRpcClientResponse res = jsonRpcClient.rpcMethod("start")
                .path("/malformed")
                .rpcId(1)
                .submit()) {
            assertThat(res.status(), is(Status.OK_200));
            assertThrows(JsonException.class, res::error);
        }
    }

    private static Http1Client http1Client(Http1ClientRequest request) {
        return (Http1Client) Proxy.newProxyInstance(JsonRpcClientTest.class.getClassLoader(),
                                                    new Class<?>[] {Http1Client.class},
                                                    (proxy, method, args) -> {
                                                        if (method.getName().equals("post")) {
                                                            return request;
                                                        }
                                                        return defaultValue(method.getReturnType());
                                                    });
    }

    private static Http1ClientRequest http1Request(AtomicReference<SniConfig> configuredSni) {
        return (Http1ClientRequest) Proxy.newProxyInstance(JsonRpcClientTest.class.getClassLoader(),
                                                           new Class<?>[] {Http1ClientRequest.class},
                                                           (proxy, method, args) -> {
                                                               if (method.getName().equals("sni")) {
                                                                   configuredSni.set((SniConfig) args[0]);
                                                                   return proxy;
                                                               }
                                                               return defaultValue(method.getReturnType());
                                                           });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == void.class) {
            return null;
        }
        return 0;
    }
}
