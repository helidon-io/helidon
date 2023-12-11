/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.accesslog;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.accesslog.AccessLogFeature;
import io.helidon.webserver.accesslog.HostLogEntry;
import io.helidon.webserver.accesslog.RequestLineLogEntry;
import io.helidon.webserver.accesslog.StatusLogEntry;
import io.helidon.webserver.accesslog.TimestampLogEntry;
import io.helidon.webserver.accesslog.UserLogEntry;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.MatcherWithRetry.assertThatWithRetry;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class AccessLogTest {
    private static final AtomicReference<MemoryLogHandler> LOG_HANDLER = new AtomicReference<>();

    private final Http1Client client;
    private final SocketHttpClient socketClient;

    AccessLogTest(Http1Client client, SocketHttpClient socketClient) {
        this.client = client;
        this.socketClient = socketClient;
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        // we cannot use the "time taken" entry, as that is changing between invocations
        server.addFeature(AccessLogFeature.builder()
                                  .clock(Clock.fixed(Instant.parse("2007-12-03T10:15:30.00Z"), ZoneId.of("UTC")))
                                  .addEntry(HostLogEntry.create())
                                  .addEntry(UserLogEntry.create())
                                  .addEntry(TimestampLogEntry.create())
                                  .addEntry(RequestLineLogEntry.create())
                                  .addEntry(StatusLogEntry.create())
                                  //.add(SizeLogEntry.create()) - size changes depending on date (1 or 2 characters for day)
                                  .build());
    }
    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.get("/access", (req, res) -> res.send("Hello World!"));
    }

    // IMPORTANT - DO NOT ADD ADDITIONAL TESTS TO THIS CLASS
    // no need for try with resources when we get as an actual type
    @SuppressWarnings("resource")
    @Test
    void testRequestsAndValidateAccessLog() {
        Http1ClientResponse response = client.get("/access").request();
        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity().as(String.class), is("Hello World!"));

        response = client.get("/wrong").request();
        assertThat(response.status(), is(Status.NOT_FOUND_404));

        String socketResponse = socketClient.sendAndReceive(Method.GET,
                                                            "/access",
                                                            null,
                                                            List.of("Content-Length: 47a"));
        assertThat(socketResponse, startsWith("HTTP/1.1 " + Status.BAD_REQUEST_400.text()));

        // Use retry since no happens-before relationship between log entry and assertion
        assertThatWithRetry("Check log entry for /access exist",
                () -> LOG_HANDLER.get().logAsString(),
                containsString("127.0.0.1 - [03/Dec/2007:10:15:30 +0000] \"GET /access HTTP/1.1\" 200"));
        assertThatWithRetry("Check entry for /wrong exists",
                () -> LOG_HANDLER.get().logAsString(),
                containsString("127.0.0.1 - [03/Dec/2007:10:15:30 +0000] \"GET /wrong HTTP/1.1\" 404"));
    }

    public static class MemoryLogHandler extends StreamHandler {

        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        public MemoryLogHandler() throws UnsupportedEncodingException {
            setOutputStream(outputStream);
            setEncoding(StandardCharsets.UTF_8.name());
            LOG_HANDLER.set(this);      // store reference
        }

        @Override
        public synchronized void publish(LogRecord record) {
            super.publish(record);
            flush();        // forces flush on writer
        }

        public String logAsString() {
            return outputStream.toString(StandardCharsets.UTF_8);
        }
    }
}
