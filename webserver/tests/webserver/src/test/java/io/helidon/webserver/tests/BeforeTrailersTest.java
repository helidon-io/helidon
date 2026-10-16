/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class BeforeTrailersTest {
    private static final HeaderName HELIDON = HeaderNames.create("helidon");

    private final WebClient client;

    BeforeTrailersTest(WebClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.addFilter((chain, req, res) -> {
                    if (req.path().path().equals("/trailers") || req.path().path().equals("/emptyTrailers")) {
                        res.header(HeaderNames.TRAILER, "helidon");
                        res.beforeTrailers(trailers -> {
                            trailers.add(HELIDON, "rocks");
                        });
                    } else if (req.path().path().equals("/invalidTrailers")) {
                        res.header(HeaderNames.TRAILER, "helidon");
                        res.beforeTrailers(trailers -> trailers.add(HELIDON, "unencodable\u0100"));
                    } else if (req.path().path().equals("/failedTrailers")) {
                        res.header(HeaderNames.TRAILER, "helidon");
                        res.beforeTrailers(ignored1 -> {
                            throw new IllegalStateException("Trailer callback failed");
                        });
                    }
                    chain.proceed();
                })
                .get("/emptyTrailers", (ignored2, res) -> res.send(""))
                .any((req, res) -> res.send("hello"));
    }

    @Test
    void testNoBeforeTrailers() {
        try (HttpClientResponse res = client.get("/noTrailers").request()) {
            assertThat(res.status().code(), is(200));
            assertThat(res.trailers().contains(HELIDON), is(false));
        }
    }

    @Test
    void testBeforeTrailers() {
        try (HttpClientResponse res = client.get("/trailers").request()) {
            assertThat(res.status().code(), is(200));
            assertThat(res.entity().as(String.class), is("hello"));     // need to read entity
            Header trailer = res.trailers().get(HELIDON);
            assertThat(trailer.get(), is("rocks"));
        }
    }

    @Test
    void testEmptyResponseTrailers() {
        try (HttpClientResponse res = client.get("/emptyTrailers").request()) {
            assertThat(res.status(), is(Status.OK_200));
            assertThat(res.entity().as(String.class), is(""));
            assertThat(res.trailers().get(HELIDON).get(), is("rocks"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/invalidTrailers", "/failedTrailers"})
    void testTrailerFailureRecoversBeforeCommit(String path) {
        try (HttpClientResponse res = client.get(path).request()) {
            assertThat(res.status(), is(Status.INTERNAL_SERVER_ERROR_500));
            assertThat(res.headers().contains(HeaderNames.TRAILER), is(false));
            assertThat(res.entity().as(String.class), is("Internal Server Error"));
            assertThat(res.trailers().contains(HELIDON), is(false));
        }
    }
}
