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

package io.helidon.webserver.http;

import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.helidon.common.uri.UriPath;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;
import io.helidon.http.Status;
import io.helidon.webserver.WebServer;

import jdk.jfr.Contextual;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.StackTrace;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

class HttpServerRequestEventTest {
    private static final String EVENT_NAME = "io.helidon.HttpServerRequest";

    @TempDir
    Path directory;

    @Test
    void recordsStatusTemplatesErrorsAndReroutesWithoutRequestValues() throws Exception {
        var identities = new ConcurrentHashMap<String, Identity>();
        var server = WebServer.builder().port(0).routing(rules -> rules
                .addFilter((chain, request, response) -> {
                    identities.put(request.prologue().uriPath().path(), Identity.of(request));
                    if (request.prologue().uriPath().path().equals("/filter-rejection")) {
                        response.status(Status.SERVICE_UNAVAILABLE_503).send();
                        return;
                    }
                    chain.proceed();
                })
                .get("/items/{id}", (request, response) -> response.send("ok"))
                .get("/failure", (request, response) -> {
                    throw new IllegalArgumentException("secret-exception-message");
                })
                .get("/handled", (request, response) -> {
                    throw new UnsupportedOperationException("secret-handled-message");
                })
                .error(UnsupportedOperationException.class, (request, response, exception) ->
                        response.status(Status.BAD_REQUEST_400).send())
                .get("/reroute", (request, response) -> response.reroute("/items/secret-reroute"))
                .get("/reroute-missing", (request, response) -> response.reroute("/secret-missing"))
                .register("/parent/{tenant}", parent -> parent
                        .registerLocator("/located/{kind}", request -> Optional.of(child -> child
                                .get("/{id}", (req, res) -> res.send("located")))))
                .get("/custom/{id}", (request, response) -> response.next())
                .route(Method.GET, new UntrustedMatcher("/custom/secret-custom"), (request, response) -> response.send())
                .register("/untrusted/{tenant}", parent -> parent.route(Method.GET,
                        new UntrustedMatcher("/secret-child"),
                        (request, response) -> response.send(request.matchingPattern().orElseThrow())))
                .route(new HttpRoute() {
                    @Override
                    public PathMatchers.MatchResult accepts(HttpPrologue prologue) {
                        return PathMatchers.exact("/no-matcher/secret-no-matcher").match(prologue.uriPath());
                    }

                    @Override
                    public Handler handler() {
                        return (request, response) -> response.send();
                    }
                })
                .get("/" + "long".repeat(70), (request, response) -> response.send()))
                .build().start();
        try (var recording = new Recording()) {
            recording.start();
            assertThat(exchange(server, "/items/secret-value?credential=secret-query"), containsString("200 OK"));
            assertThat(exchange(server, "/failure"), containsString("500 Internal Server Error"));
            assertThat(exchange(server, "/handled"), containsString("400 Bad Request"));
            assertThat(exchange(server, "/secret-unmatched"), containsString("404 Not Found"));
            assertThat(exchange(server, "/reroute"), containsString("200 OK"));
            assertThat(exchange(server, "/reroute-missing"), containsString("404 Not Found"));
            assertThat(exchange(server, "/parent/secret-tenant/located/secret-kind/secret-id"), containsString("200 OK"));
            assertThat(exchange(server, "/custom/secret-custom"), containsString("200 OK"));
            assertThat(exchange(server, "/untrusted/secret-tenant/secret-child"),
                       containsString("/untrusted/{tenant}/secret-child"));
            assertThat(exchange(server, "/filter-rejection"), containsString("503 Service Unavailable"));
            assertThat(exchange(server, "/no-matcher/secret-no-matcher"), containsString("200 OK"));
            assertThat(exchange(server, "/" + "long".repeat(70)), containsString("200 OK"));
            List<RecordedEvent> events = events(recording, "requests.jfr");
            assertThat(events, hasSize(12));
            assertRequest(events, identities.get("/items/secret-value"), "/items/{id}", 200, "SUCCESS");
            assertRequest(events, identities.get("/failure"), "/failure", 500, "SERVER_ERROR");
            assertRequest(events, identities.get("/handled"), "/handled", 400, "CLIENT_ERROR");
            assertRequest(events, identities.get("/secret-unmatched"), null, 404, "CLIENT_ERROR");
            assertRequest(events, identities.get("/reroute"), "/items/{id}", 200, "SUCCESS");
            assertRequest(events, identities.get("/reroute-missing"), null, 404, "CLIENT_ERROR");
            assertRequest(events, identities.get("/parent/secret-tenant/located/secret-kind/secret-id"),
                          "/parent/{tenant}/located/{kind}/{id}", 200, "SUCCESS");
            assertRequest(events, identities.get("/custom/secret-custom"), null, 200, "SUCCESS");
            assertRequest(events, identities.get("/untrusted/secret-tenant/secret-child"), null, 200, "SUCCESS");
            assertRequest(events, identities.get("/filter-rejection"), null, 503, "SERVER_ERROR");
            assertRequest(events, identities.get("/no-matcher/secret-no-matcher"), null, 200, "SUCCESS");
            assertRequest(events, identities.get("/" + "long".repeat(70)), null, 200, "SUCCESS");
            for (RecordedEvent event : events) {
                assertThat(event.getStackTrace(), nullValue());
                String template = event.getString("routeTemplate");
                assertThat(template == null || !template.contains("secret"), is(true));
            }
        } finally {
            server.stop();
        }
    }

    @Test
    void simultaneousRequestsKeepTheirThreadAndFilterIntervals() throws Exception {
        var entered = new CountDownLatch(2);
        var identities = new ConcurrentHashMap<String, Identity>();
        var server = WebServer.builder().port(0).routing(rules -> rules
                .addFilter((chain, request, response) -> {
                    identities.put(request.prologue().uriPath().path(), Identity.of(request));
                    Marker.emit(request, "before-filter");
                    chain.proceed();
                    Marker.emit(request, "after-filter");
                })
                .get("/parallel/{id}", (request, response) -> {
                    entered.countDown();
                    assertThat("both requests entered routing", entered.await(10, TimeUnit.SECONDS), is(true));
                    Marker.emit(request, "handler");
                    response.send("ok");
                })).build().start();
        try (var recording = new Recording(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            recording.enable(Marker.class);
            recording.start();
            var first = executor.submit(() -> exchange(server, "/parallel/one"));
            var second = executor.submit(() -> exchange(server, "/parallel/two"));
            assertThat(first.get(15, TimeUnit.SECONDS), containsString("200 OK"));
            assertThat(second.get(15, TimeUnit.SECONDS), containsString("200 OK"));
            recording.stop();
            Path file = directory.resolve("parallel.jfr");
            recording.dump(file);
            var allEvents = RecordingFile.readAllEvents(file);
            var requests = allEvents.stream().filter(event -> EVENT_NAME.equals(event.getEventType().getName())).toList();
            var markers = allEvents.stream().filter(event -> "helidon.test.RequestMarker"
                    .equals(event.getEventType().getName())).toList();
            assertThat(requests, hasSize(2));
            assertThat(markers, hasSize(6));
            assertThat(identities.values().stream().map(Identity::threadId).distinct().count(), is(2L));
            for (Identity identity : identities.values()) {
                RecordedEvent request = find(requests, identity);
                assertThat(request.getThread().getJavaThreadId(), is(identity.threadId()));
                var ownMarkers = markers.stream()
                        .filter(marker -> marker.getThread().getJavaThreadId() == identity.threadId()).toList();
                assertThat(ownMarkers, hasSize(3));
                for (RecordedEvent marker : ownMarkers) {
                    assertThat(marker.getString("socketId"), is(identity.socketId()));
                    assertThat(marker.getInt("requestId"), is(identity.requestId()));
                    assertThat(marker.getStartTime().isBefore(request.getStartTime()), is(false));
                    assertThat(marker.getEndTime().isAfter(request.getEndTime()), is(false));
                }
            }
        } finally {
            server.stop();
        }
    }

    @Test
    void eventCanBeDisabledAndHasContextualDefaults() throws Exception {
        EventType type = EventType.getEventType(HttpServerRequestEvent.class);
        Map<String, String> defaults = type.getSettingDescriptors().stream()
                .collect(Collectors.toMap(setting -> setting.getName(), setting -> setting.getDefaultValue()));
        assertThat(defaults.get("enabled"), is("true"));
        assertThat(defaults.get("threshold"), is("0 ms"));
        assertThat(defaults.get("stackTrace"), is("false"));
        for (String field : List.of("serverSocketId", "socketId", "requestId", "requestName")) {
            assertThat(type.getField(field).getAnnotation(Contextual.class), notNullValue());
        }
        var server = WebServer.builder().port(0).routing(rules -> rules.get("/disabled", (req, res) -> res.send()))
                .build().start();
        try (var recording = new Recording()) {
            recording.disable(EVENT_NAME);
            recording.start();
            assertThat(exchange(server, "/disabled"), containsString("200 OK"));
            assertThat(events(recording, "disabled.jfr"), hasSize(0));
        } finally {
            server.stop();
        }
    }

    private static void assertRequest(List<RecordedEvent> events, Identity identity, String template, int status, String outcome) {
        RecordedEvent event = find(events, identity);
        assertThat(event.getString("routeTemplate"), is(template));
        assertThat(event.getString("requestName"), is("GET " + (template == null ? "<unknown>" : template)));
        assertThat(event.getString("method"), is("GET"));
        assertThat(event.getInt("statusCode"), is(status));
        assertThat(event.getString("outcome"), is(outcome));
        assertThat(event.getThread().getJavaThreadId(), is(identity.threadId()));
    }

    private static RecordedEvent find(List<RecordedEvent> events, Identity identity) {
        var matching = events.stream().filter(event -> identity.socketId().equals(event.getString("socketId"))
                && identity.serverSocketId().equals(event.getString("serverSocketId"))
                && identity.requestId() == event.getInt("requestId")).toList();
        assertThat("events for " + identity, matching, hasSize(1));
        return matching.getFirst();
    }

    private static String exchange(WebServer server, String target) throws Exception {
        try (var socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            socket.setSoTimeout(10_000);
            socket.getOutputStream().write(("GET " + target + " HTTP/1.1\r\nHost: localhost\r\n"
                                                   + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            // EOF follows the routing return and connection close, so the event producer has completed before dumping.
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
        }
    }

    private List<RecordedEvent> events(Recording recording, String filename) throws Exception {
        recording.stop();
        Path file = directory.resolve(filename);
        recording.dump(file);
        return RecordingFile.readAllEvents(file).stream()
                .filter(event -> EVENT_NAME.equals(event.getEventType().getName())).toList();
    }

    private record Identity(String serverSocketId, String socketId, int requestId, long threadId) {
        static Identity of(ServerRequest request) {
            return new Identity(request.serverSocketId(), request.socketId(), request.id(), Thread.currentThread().threadId());
        }
    }

    private static final class UntrustedMatcher implements PathMatcher {
        private final String actualPath;

        private UntrustedMatcher(String actualPath) {
            this.actualPath = actualPath;
        }

        @Override
        public PathMatchers.MatchResult match(UriPath path) {
            return PathMatchers.exact(actualPath).match(path);
        }

        @Override
        public PathMatchers.PrefixMatchResult prefixMatch(UriPath path) {
            return PathMatchers.exact(actualPath).prefixMatch(path);
        }

        @Override
        public Optional<String> matchingElement() {
            return Optional.of(actualPath);
        }
    }

    @Name("helidon.test.RequestMarker")
    @StackTrace(false)
    static final class Marker extends Event {
        private String socketId;
        private int requestId;
        private String phase;

        static void emit(ServerRequest request, String phase) {
            var marker = new Marker();
            marker.socketId = request.socketId();
            marker.requestId = request.id();
            marker.phase = phase;
            marker.commit();
        }
    }
}
