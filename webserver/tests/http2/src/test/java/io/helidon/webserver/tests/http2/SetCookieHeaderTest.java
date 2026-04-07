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

package io.helidon.webserver.tests.http2;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HeaderNames;
import io.helidon.http.SetCookie;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webserver.testing.junit5.http2.Http2TestClient;
import io.helidon.webserver.testing.junit5.http2.Http2TestConnection;

import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.GET;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class SetCookieHeaderTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.route(Http2Route.route(GET, "/cookies", (req, res) -> {
            res.headers().addCookie(SetCookie.builder("first-cookie", "value,1")
                                             .path("/")
                                             .build());
            res.headers().addCookie(SetCookie.builder("second-cookie", "value,2")
                                             .path("/")
                                             .build());
            res.send("ok");
        }));
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        server.addProtocol(Http2Config.builder().build());
    }

    @Test
    void testMultiValuedCookie(Http2TestClient client) {
        Http2TestConnection connection = client.createConnection();
        connection.request(1, GET, "/cookies", WritableHeaders.create(), BufferData.create(new byte[0]));

        connection.assertSettings(TIMEOUT);
        connection.assertWindowsUpdate(0, TIMEOUT);
        connection.assertSettings(TIMEOUT);

        Http2Headers headers = connection.assertHeaders(1, TIMEOUT);
        assertThat(headers.status(), is(Status.OK_200));
        assertThat(headers.httpHeaders().contains(HeaderNames.SET_COOKIE), is(true));

        List<String> cookieValues = headers.httpHeaders().get(HeaderNames.SET_COOKIE).allValues();
        assertThat(cookieValues.size(), is(2));

        SetCookie firstCookie = SetCookie.parse(cookieValues.get(0));
        SetCookie secondCookie = SetCookie.parse(cookieValues.get(1));

        assertThat(firstCookie.name(), is("first-cookie"));
        assertThat(firstCookie.value(), is("value,1"));
        assertThat(firstCookie.path(), is(Optional.of("/")));
        assertThat(secondCookie.name(), is("second-cookie"));
        assertThat(secondCookie.value(), is("value,2"));
        assertThat(secondCookie.path(), is(Optional.of("/")));
    }
}
