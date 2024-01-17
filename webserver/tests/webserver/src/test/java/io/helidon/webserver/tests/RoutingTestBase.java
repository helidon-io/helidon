/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests;

import java.util.function.Function;
import java.util.stream.Stream;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

// Use by both RoutingTest and RulesTest to share the same test methods
abstract class RoutingTestBase {
    private static final Header MULTI_HANDLER = HeaderValues.createCached(
            HeaderNames.create("X-Multi-Handler"), "true");
    static Http1Client client;
    // Functions that will be used to execute http webclient shortcut methods
    private static Function<String, Http1ClientRequest> get = x -> client.get(x);
    private static Function<String, Http1ClientRequest> post = x -> client.post(x);
    private static Function<String, Http1ClientRequest> put = x -> client.put(x);
    private static Function<String, Http1ClientRequest> delete = x -> client.delete(x);
    private static Function<String, Http1ClientRequest> head = x -> client.head(x);
    private static Function<String, Http1ClientRequest> options = x -> client.options(x);
    private static Function<String, Http1ClientRequest> trace = x -> client.trace(x);
    private static Function<String, Http1ClientRequest> patch = x -> client.patch(x);

    // Add header to indicate that this is a multi handler routing
    static void multiHandler(ServerRequest req, ServerResponse res) {
        res.headers().set(MULTI_HANDLER);
        res.next();
    }

    @Test
    void testRouteWithSpace() {
        try (Http1ClientResponse response = client.get("/my path").request()) {

            assertThat(response.status(), is(Status.OK_200));

            String message = response.as(String.class);
            assertThat(message, is("done"));
        }
    }

    @Test
    void testRouteWithUtf8() {
        try (Http1ClientResponse response = client.get("/českáCesta").request()) {

            assertThat(response.status(), is(Status.OK_200));

            String message = response.as(String.class);
            assertThat(message, is("done"));
        }
    }

    @ParameterizedTest
    @MethodSource("basic")
    void testHttpShortcutMethods(Function<String, Http1ClientRequest> request, String path, String responseMessage) {
        try (Http1ClientResponse response = request.apply(path).request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(responseMessage));
        }
    }

    @ParameterizedTest
    @MethodSource("withoutPathPattern")
    void testHttpShortcutMethodsWithoutPathPattern(Function<String, Http1ClientRequest> request,
                                                   String path,
                                                   String responseMessage) {
        try (Http1ClientResponse response = request.apply(path).request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(responseMessage));
        }
    }

    @ParameterizedTest
    @MethodSource("withPathMatcher")
    void testHttpShortcutMethodsWithPathMatcher(Function<String, Http1ClientRequest> request,
                                                String path,
                                                String responseMessage) {
        try (Http1ClientResponse response = request.apply(path).request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(responseMessage));
        }
    }

    @ParameterizedTest
    @MethodSource("withMultiHandlers")
    void testHttpShortcutMethodsWithMultiHandlers(Function<String, Http1ClientRequest> request,
                                                  String path,
                                                  String responseMessage) {
        try (Http1ClientResponse response = request.apply(path).request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(MULTI_HANDLER));
            assertThat(response.as(String.class), is(responseMessage));
        }
    }

    private static Stream<Arguments> basic() {
        return Stream.of(
                arguments(get, "/get", "get"),
                arguments(post, "/post", "post"),
                arguments(put, "/put", "put"),
                arguments(delete, "/delete", "delete"),
                arguments(head, "/head", "head"),
                arguments(options, "/options", "options"),
                arguments(trace, "/trace", "trace"),
                arguments(patch, "/patch", "patch"),
                arguments(delete, "/any", "any"),
                arguments(post, "/any", "any"),
                arguments(get, "/any", "any")
        );
    }

    private static Stream<Arguments> withoutPathPattern() {
        return Stream.of(
                arguments(get, "/get_catchall", "get_catchall"),
                arguments(post, "/post_catchall", "post_catchall"),
                arguments(put, "/put_catchall", "put_catchall"),
                arguments(delete, "/delete_catchall", "delete_catchall"),
                arguments(head, "/head_catchall", "head_catchall"),
                arguments(options, "/options_catchall", "options_catchall"),
                arguments(trace, "/trace_catchall", "trace_catchall"),
                arguments(patch, "/patch_catchall", "patch_catchall")
        );
    }

    private static Stream<Arguments> withPathMatcher() {
        return Stream.of(
                arguments(get, "/wildcard_any", "wildcard_test1"),
                arguments(post, "/wildcard/any", "wildcard_test2")
        );
    }

    private static Stream<Arguments> withMultiHandlers() {
        return Stream.of(
                arguments(get, "/get_multi", "get_multi"),
                arguments(post, "/post_multi", "post_multi"),
                arguments(put, "/put_multi", "put_multi"),
                arguments(delete, "/delete_multi", "delete_multi"),
                arguments(head, "/head_multi", "head_multi"),
                arguments(options, "/options_multi", "options_multi"),
                arguments(trace, "/trace_multi", "trace_multi"),
                arguments(patch, "/patch_multi", "patch_multi")
        );
    }
}
