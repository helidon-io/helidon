/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.server.accesslog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.accesslog.AccessLogFilter;
import io.helidon.nima.webserver.accesslog.HostLogEntry;
import io.helidon.nima.webserver.accesslog.RequestLineLogEntry;
import io.helidon.nima.webserver.accesslog.StatusLogEntry;
import io.helidon.nima.webserver.accesslog.TimestampLogEntry;
import io.helidon.nima.webserver.accesslog.UserLogEntry;
import io.helidon.nima.webserver.http.HttpRouting;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

@ServerTest
class AccessLogTest {
    private static final Path ACCESS_LOG = Paths.get("access.log");
    private final Http1Client client;

    AccessLogTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        // we cannot use the "time taken" entry, as that is changing between invocations
        router.addFilter(AccessLogFilter.builder()
                                 .clock(Clock.fixed(Instant.parse("2007-12-03T10:15:30.00Z"), ZoneId.of("UTC")))
                                 .add(HostLogEntry.create())
                                 .add(UserLogEntry.create())
                                 .add(TimestampLogEntry.create())
                                 .add(RequestLineLogEntry.create())
                                 .add(StatusLogEntry.create())
                                 //.add(SizeLogEntry.create()) - size changes depending on date (1 or 2 characters for day)
                                 .build())
                .get("/access", (req, res) -> res.send("Hello World!"));
    }

    // IMPORTANT - DO NOT ADD ADDITIONAL TESTS TO THIS CLASS
    // no need for try with resources when we get as an actual type
    @SuppressWarnings("resource")
    @Test
    void testRequestsAndValidateAccessLog() throws IOException {
        Http1ClientResponse response = client.get("/access").request();
        assertThat(response.status(), is(Http.Status.OK_200));
        assertThat(response.entity().as(String.class), is("Hello World!"));

        response = client.get("/wrong").request();
        assertThat(response.status(), is(Http.Status.NOT_FOUND_404));

        response = client.get("/access")
                .header(Http.Header.create(Http.Header.CONTENT_LENGTH, "47a"))
                .request();

        assertThat(response.status(), is(Http.Status.BAD_REQUEST_400));

        List<String> lines = Files.readAllLines(ACCESS_LOG);
        assertThat(lines, contains("127.0.0.1 - [03/Dec/2007:10:15:30 +0000] \"GET /access HTTP/1.1\" 200",
                                   "127.0.0.1 - [03/Dec/2007:10:15:30 +0000] \"GET /wrong HTTP/1.1\" 404"));
    }
}
