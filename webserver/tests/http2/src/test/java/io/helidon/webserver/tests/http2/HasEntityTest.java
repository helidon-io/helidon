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

package io.helidon.webserver.tests.http2;

import java.util.List;
import java.util.stream.Stream;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class HasEntityTest {

    private final Http2Client client;

    HasEntityTest(Http2Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router
                .any("/simple", (req, res) -> {
                    assertThat(req.prologue().protocolVersion(), is("2.0"));
                    res.send(String.valueOf(req.content().hasEntity()));
                })
                .any("/consumed", (req, res) -> {
                    assertThat(req.prologue().protocolVersion(), is("2.0"));
                    req.content().inputStream().readAllBytes();
                    res.send(String.valueOf(req.content().hasEntity()));
                })
                .any("/partially-consumed", (req, res) -> {
                    assertThat(req.prologue().protocolVersion(), is("2.0"));
                    req.content().inputStream().read(new byte[3]);
                    res.send(String.valueOf(req.content().hasEntity()));
                });
    }

    static Stream<Arguments> methodsAndPaths() {
        List<String> methods = List.of("GET", "POST", "PUT", "DELETE");
        List<String> paths = List.of("/simple", "/consumed", "/partially-consumed");
        return methods.stream()
                .flatMap(m -> paths.stream().map(p -> Arguments.of(m, p)));
    }

    @ParameterizedTest
    @MethodSource("methodsAndPaths")
    void hasEntity_withoutBody(String method, String path) {
        assertHasEntity(method, path, false, false);
    }

    @ParameterizedTest
    @MethodSource("methodsAndPaths")
    void hasEntity_withBody(String method, String path) {
        assertHasEntity(method, path, true, true);
    }

    private void assertHasEntity(String method, String path, boolean withEntity, boolean expected) {
        try (var response = client.method(Method.create(method))
                .uri(path)
                .submit(withEntity ? "test" : BufferData.EMPTY_BYTES)) {
            assertThat(response.status(), is(Status.OK_200));
            String entity = response.entity().as(String.class);
            assertThat(entity, is(String.valueOf(expected)));
        }
    }
}
