/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.media.type.MediaTypes;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

// Use by both RoutingTest and RulesTest to share the same test methods
abstract class RoutingTestBase {
    private static final Header MULTI_HANDLER = HeaderValues.createCached(
            HeaderNames.create("X-Multi-Handler"), "true");
    private static final String HEAD_ROUTE_HEADER = "X-Head-Route";
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

    static void sendHead(ServerResponse res, String responseMessage) {
        res.headers().set(HeaderValues.createCached(HeaderNames.create(HEAD_ROUTE_HEADER), responseMessage));
        res.send(responseMessage);
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

    @Test
    void testRouteWithAcceptJson() {
        var response = client.get("/header_based")
                .accept(MediaTypes.APPLICATION_JSON)
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("header_based_JSON"));
    }

    @Test
    void testRouteWithAcceptText() {
        var response = client.get("/header_based")
                .accept(MediaTypes.TEXT_PLAIN)
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is("header_based_TEXT"));
    }

    @ParameterizedTest
    @MethodSource("basic")
    void testHttpShortcutMethods(Function<String, Http1ClientRequest> request,
                                 String path,
                                 String responseMessage,
                                 boolean hasResponseEntity) {
        try (Http1ClientResponse response = request.apply(path).request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertResponseEntity(response, responseMessage, hasResponseEntity);
        }
    }

    @ParameterizedTest
    @MethodSource("withoutPathPattern")
    void testHttpShortcutMethodsWithoutPathPattern(Function<String, Http1ClientRequest> request,
                                                   String path,
                                                   String responseMessage,
                                                   boolean hasResponseEntity) {
        try (Http1ClientResponse response = request.apply(path).request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertResponseEntity(response, responseMessage, hasResponseEntity);
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
                                                  String responseMessage,
                                                  boolean hasResponseEntity) {
        try (Http1ClientResponse response = request.apply(path).request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers(), hasHeader(MULTI_HANDLER));
            assertResponseEntity(response, responseMessage, hasResponseEntity);
        }
    }

    private static void assertResponseEntity(Http1ClientResponse response,
                                             String responseMessage,
                                             boolean hasResponseEntity) {
        if (hasResponseEntity) {
            assertThat(response.as(String.class), is(responseMessage));
        } else {
            assertThat(response.headers(),
                       hasHeader(HeaderValues.createCached(HeaderNames.create(HEAD_ROUTE_HEADER), responseMessage)));
            assertThrows(IllegalStateException.class, () -> response.as(String.class));
        }
    }

    private static Stream<Arguments> basic() {
        return Stream.of(
                arguments(get, "/get", "get", true),
                arguments(post, "/post", "post", true),
                arguments(put, "/put", "put", true),
                arguments(delete, "/delete", "delete", true),
                arguments(head, "/head", "head", false),
                arguments(options, "/options", "options", true),
                arguments(trace, "/trace", "trace", true),
                arguments(patch, "/patch", "patch", true),
                arguments(delete, "/any", "any", true),
                arguments(post, "/any", "any", true),
                arguments(get, "/any", "any", true)
        );
    }

    private static Stream<Arguments> withoutPathPattern() {
        return Stream.of(
                arguments(get, "/get_catchall", "get_catchall", true),
                arguments(post, "/post_catchall", "post_catchall", true),
                arguments(put, "/put_catchall", "put_catchall", true),
                arguments(delete, "/delete_catchall", "delete_catchall", true),
                arguments(head, "/head_catchall", "head_catchall", false),
                arguments(options, "/options_catchall", "options_catchall", true),
                arguments(trace, "/trace_catchall", "trace_catchall", true),
                arguments(patch, "/patch_catchall", "patch_catchall", true)
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
                arguments(get, "/get_multi", "get_multi", true),
                arguments(post, "/post_multi", "post_multi", true),
                arguments(put, "/put_multi", "put_multi", true),
                arguments(delete, "/delete_multi", "delete_multi", true),
                arguments(head, "/head_multi", "head_multi", false),
                arguments(options, "/options_multi", "options_multi", true),
                arguments(trace, "/trace_multi", "trace_multi", true),
                arguments(patch, "/patch_multi", "patch_multi", true)
        );
    }
}
