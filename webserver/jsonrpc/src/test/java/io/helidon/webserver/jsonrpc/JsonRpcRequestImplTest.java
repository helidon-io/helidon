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

import java.lang.reflect.Proxy;
import java.util.Optional;

import io.helidon.json.JsonObject;
import io.helidon.webserver.http.ServerRequest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JsonRpcRequestImplTest {
    @Test
    void delegatesSniHosts() {
        JsonRpcRequestImpl request = new JsonRpcRequestImpl(delegate(),
                                                            JsonObject.builder()
                                                                    .set("jsonrpc", "2.0")
                                                                    .set("method", "test")
                                                                    .build());

        assertThat(request.sniRequestedHost(), is(Optional.of("api.example.com")));
        assertThat(request.sniMatchedHost(), is(Optional.of("*.example.com")));
    }

    private static ServerRequest delegate() {
        return (ServerRequest) Proxy.newProxyInstance(JsonRpcRequestImplTest.class.getClassLoader(),
                                                      new Class<?>[] {ServerRequest.class},
                                                      (proxy, method, args) -> switch (method.getName()) {
                                                          case "sniRequestedHost" -> Optional.of("api.example.com");
                                                          case "sniMatchedHost" -> Optional.of("*.example.com");
                                                          case "toString" -> "test-server-request";
                                                          default -> throw new AssertionError(method.getName());
                                                      });
    }
}
