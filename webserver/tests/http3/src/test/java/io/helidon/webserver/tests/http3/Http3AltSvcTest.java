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

package io.helidon.webserver.tests.http3;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;

import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webserver.http.AltSvc;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Config;

import org.junit.jupiter.api.Test;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class Http3AltSvcTest {
    @Test
    void shouldAdvertiseAltSvcOnSuccessfulResponse() throws Exception {
        try (Http3TestSupport.TestEnvironment environment = environment()) {
            HttpClient client = environment.http1Client();
            int port = environment.port();
            HttpResponse<String> response = client.send(Http3TestSupport.http1Get(port, "/ok"), ofString());

            assertThat(response.statusCode(), is(200));
            assertThat(response.version(), is(HTTP_1_1));
            assertThat(response.headers().firstValue(HeaderNames.ALT_SVC.defaultCase()).orElse(null), is(expectedAltSvc(port)));
            assertThat(response.body(), is("ok"));
        }
    }

    @Test
    void shouldAdvertiseAltSvcOnRedirectFromErrorHandler() throws Exception {
        try (Http3TestSupport.TestEnvironment environment = environment()) {
            HttpClient client = environment.http1Client();
            int port = environment.port();
            HttpResponse<String> response = client.send(Http3TestSupport.http1Get(port, "/error-redirect"), ofString());

            assertThat(response.statusCode(), is(301));
            assertThat(response.version(), is(HTTP_1_1));
            assertThat(response.headers().firstValue(HeaderNames.LOCATION.defaultCase()).orElse(null), is("/ok"));
            assertThat(response.headers().firstValue(HeaderNames.ALT_SVC.defaultCase()).orElse(null), is(expectedAltSvc(port)));
        }
    }

    @Test
    void shouldNotAdvertiseAltSvcOnNotFound() throws Exception {
        try (Http3TestSupport.TestEnvironment environment = environment()) {
            HttpClient client = environment.http1Client();
            int port = environment.port();
            HttpResponse<String> response = client.send(Http3TestSupport.http1Get(port, "/missing"), ofString());

            assertThat(response.statusCode(), is(404));
            assertThat(response.version(), is(HTTP_1_1));
            assertThat(response.headers().firstValue(HeaderNames.ALT_SVC.defaultCase()).isPresent(), is(false));
        }
    }

    private static void routing(HttpRouting.Builder router) {
        router.error(RedirectException.class,
                     (_, res, _) -> res.status(Status.MOVED_PERMANENTLY_301)
                             .header(HeaderNames.LOCATION, "/ok")
                             .send())
                .get("/ok", (_, res) -> res.send("ok"))
                .get("/error-redirect", (_, _) -> {
                    throw new RedirectException();
                });
    }

    private static String expectedAltSvc(int port) {
        return "h3=\"" + ":" + port + "\"; ma=120; persist=1";
    }

    private static Http3TestSupport.TestEnvironment environment() throws Exception {
        return Http3TestSupport.sharedListener(Http1Config.builder()
                                                          .altSvc(AltSvc.builder()
                                                                          .maxAge(Duration.ofSeconds(120))
                                                                          .persist(true)
                                                                          .build())
                                                          .build(),
                                               Http3AltSvcTest::routing);
    }

    private static final class RedirectException extends RuntimeException {
    }
}
