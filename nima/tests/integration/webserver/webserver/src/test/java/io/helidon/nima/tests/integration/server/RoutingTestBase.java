/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.server;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webclient.http1.Http1ClientRequest;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

// Use by both RoutingTest and RulesTest to share the same test methods
class RoutingTestBase {
    private static final Http.HeaderValue MULTI_HANDLER = Http.Header.createCached(
            Http.Header.create("X-Multi-Handler"), "true");
    Http1Client client;

    // Add header to indicate that this is a multi handler routing
    static void multiHandler(ServerRequest req, ServerResponse res) {
        res.headers().set(MULTI_HANDLER);
        res.next();
    }

    @Test
    void testRouteWithSpace() {
        try (Http1ClientResponse response = client.get("/my path").request()) {

            assertThat(response.status(), is(Http.Status.OK_200));

            String message = response.as(String.class);
            assertThat(message, is("done"));
        }
    }

    @Test
    void testRouteWithUtf8() {
        try (Http1ClientResponse response = client.get("/českáCesta").request()) {

            assertThat(response.status(), is(Http.Status.OK_200));

            String message = response.as(String.class);
            assertThat(message, is("done"));
        }
    }

    @Test
    void testHttpShortcutMethods() {
        List<Map<String, Http1ClientRequest>> requests = Arrays.asList(Map.of("get", client.get("/get")),
                                                                       Map.of("post", client.post("/post")),
                                                                       Map.of("put", client.put("/put")),
                                                                       Map.of("delete", client.delete("/delete")),
                                                                       Map.of("head", client.head("/head")),
                                                                       Map.of("options", client.options("/options")),
                                                                       Map.of("trace", client.trace("/trace")),
                                                                       Map.of("patch", client.patch("/patch")),
                                                                       Map.of("any", client.delete("/any")),
                                                                       Map.of("any", client.post("/any")),
                                                                       Map.of("any", client.delete("/any")));

        validateHttpShortCutMethods(requests);
    }

    @Test
    void testHttpShortcutMethodsWithoutPathPattern() {
        List<Map<String, Http1ClientRequest>> requests = Arrays.asList(Map.of("get_catchall", client.get("/get_catchall")),
                                                                       Map.of("post_catchall", client.post("/post_catchall")),
                                                                       Map.of("put_catchall", client.put("/put_catchall")),
                                                                       Map.of("delete_catchall",
                                                                              client.delete("/delete_catchall")),
                                                                       Map.of("head_catchall", client.head("/head_catchall")),
                                                                       Map.of("options_catchall",
                                                                              client.options("/options_catchall")),
                                                                       Map.of("trace_catchall", client.trace("/trace_catchall")),
                                                                       Map.of("patch_catchall", client.patch("/patch_catchall")));

        validateHttpShortCutMethods(requests);
    }

    @Test
    void testHttpShortcutMethodsWithPatchMatcher() {
        List<Map<String, Http1ClientRequest>> requests = Arrays.asList(Map.of("wildcard_test1", client.get("/wildcard_any")),
                                                                       Map.of("wildcard_test2", client.post("/wildcard/any")));
        validateHttpShortCutMethods(requests);
    }

    @Test
    void testHttpShortcutMethodsWithMultiHandlers() {
        List<Map<String, Http1ClientRequest>> requests = Arrays.asList(Map.of("get_multi", client.get("/get_multi")),
                                                                       Map.of("post_multi", client.post("/post_multi")),
                                                                       Map.of("put_multi", client.put("/put_multi")),
                                                                       Map.of("delete_multi",
                                                                              client.delete("/delete_multi")),
                                                                       Map.of("head_multi", client.head("/head_multi")),
                                                                       Map.of("options_multi",
                                                                              client.options("/options_multi")),
                                                                       Map.of("trace_multi", client.trace("/trace_multi")),
                                                                       Map.of("patch_multi", client.patch("/patch_multi")));

        validateHttpShortCutMethods(requests, true);
    }

    private static void validateHttpShortCutMethods(List<Map<String, Http1ClientRequest>> requests) {
        validateHttpShortCutMethods(requests, false);
    }

    // multiHandlerTest set to true adds extra test to validate that a header was added in the first handler
    private static void validateHttpShortCutMethods(List<Map<String, Http1ClientRequest>> requests, boolean multiHandlerTest) {
        for (Map<String, Http1ClientRequest> request : requests) {
            Map.Entry<String, Http1ClientRequest> entry = request.entrySet().iterator().next();

            try (Http1ClientResponse response = entry.getValue().request()) {

                assertThat(response.status(), is(Http.Status.OK_200));
                if (multiHandlerTest) {
                    assertThat(response.headers(), hasHeader(MULTI_HANDLER));
                }

                String message = response.as(String.class);
                assertThat(message, is(entry.getKey()));
            }
        }
    }
}
