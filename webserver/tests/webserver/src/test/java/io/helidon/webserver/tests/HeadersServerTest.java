/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.InputStream;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static io.helidon.http.Method.GET;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
class HeadersServerTest {

    private static final String DATA = "Helidon!!!".repeat(10);
    private static final Header TEST_TRAILER_HEADER = HeaderValues.create("test-trailer", "trailer-value");
    private static final HeaderName CLIENT_PORT_HEADER_NAME = HeaderNames.create("client-port");
    private static final Header BAD_CONTENT_TYPE = HeaderValues.create(HeaderNames.CONTENT_TYPE, "bullseye");
    private static final Header GOOD_CONTENT_TYPE = HeaderValues.create(HeaderNames.CONTENT_TYPE,
                                                                       MediaTypes.APPLICATION_XML.text());

    private int clientPort = -1;

    @SetUpRoute
    static void router(HttpRouting.Builder router) {
        router.error(IllegalStateException.class, (req, res, t) -> res.status(500).send(t.getMessage()));
        router.any((req, res) -> res
                .header(CLIENT_PORT_HEADER_NAME, String.valueOf(req.remotePeer().port()))
                .next());
        router.route(GET, "/trailers",
                     (req, res) -> {
                         res.header(HeaderNames.TRAILER, TEST_TRAILER_HEADER.name());
                         try (var os = res.outputStream()) {
                             os.write(DATA.getBytes());
                             os.write(DATA.getBytes());
                             os.write(DATA.getBytes());
                             res.trailers().add(TEST_TRAILER_HEADER);
                         }
                     }
        );
        router.route(GET, "/stream-result",
                     (req, res) -> {
                         res.header(HeaderNames.TRAILER, TEST_TRAILER_HEADER.name());
                         try (var os = res.outputStream()) {
                             os.write(DATA.getBytes());
                             os.write(DATA.getBytes());
                             os.write(DATA.getBytes());
                             res.streamResult("Kaboom!");
                         }
                     }
        );
        router.route(GET, "/trailers-forced",
                     (req, res) -> {
                         res.header(HeaderNames.TRAILER, TEST_TRAILER_HEADER.name());
                         res.trailers().add(TEST_TRAILER_HEADER);
                         res.send(DATA.repeat(3));
                     }
        );
        router.route(GET, "/trailers-no-trailers",
                     (req, res) -> {
                         res.trailers().add(TEST_TRAILER_HEADER);
                         res.send(DATA);
                     }
        );
        router.route(GET, "/stream-with-trailers-and-length",
                     (req, res) -> {
                         res.header(HeaderNames.TRAILER, TEST_TRAILER_HEADER.name());
                         res.header(HeaderNames.CONTENT_LENGTH, String.valueOf(DATA.length()));     // must switch to chunked
                         try (var os = res.outputStream()) {
                             os.write(DATA.getBytes());
                         }
                         res.trailers().add(TEST_TRAILER_HEADER);
                     }
        );
        router.route(GET, "/content/type",
                     (req, res) -> {
                        res.send(req.headers().contentType().map(MediaType::text).orElse("none"));
                     });
    }

    @Test
    void testGoodContentType(WebClient webClient) {
        // sanity check
        var response = webClient.get("/content/type")
                .header(GOOD_CONTENT_TYPE)
                .request(String.class);

        assertThat(response.status(), is(Status.OK_200));
        assertThat(response.entity(), is(MediaTypes.APPLICATION_XML.text()));
    }

    @Test
    void testBadContentType(WebClient webClient) {
        var response = webClient.get("/content/type")
                .header(BAD_CONTENT_TYPE)
                .request(String.class);

        assertThat(response.status(), is(Status.BAD_REQUEST_400));
    }

    @Test
    void trailersTE(WebClient client) throws IOException {
        ClientResponseTyped<InputStream> res = client
                .get("/trailers")
                .header(HeaderValues.create("TE", "trailers"))
                .request(InputStream.class);
        try (var ins = res.entity()) {
            assertThat(ins.readAllBytes(), is(DATA.repeat(3).getBytes()));
        }
        assertThat(res.trailers(), hasHeader(TEST_TRAILER_HEADER));
        checkCachedConnection(res.headers());
    }

    @Test
    void trailers(WebClient client) {
        ClientResponseTyped<String> res = client
                .get("/trailers")
                .request(String.class);
        assertThat(res.entity(), is(DATA.repeat(3)));
        assertThat(res.trailers(), hasHeader(TEST_TRAILER_HEADER));
        checkCachedConnection(res.headers());
    }

    @Test
    void trailersForced(WebClient client) {
        ClientResponseTyped<String> res = client
                .get("/trailers-forced")
                .request(String.class);
        assertThat(res.entity(), is(DATA.repeat(3)));
        assertThat(res.trailers(), hasHeader(TEST_TRAILER_HEADER));
        checkCachedConnection(res.headers());
    }

    @Test
    void streamResult(WebClient client) throws IOException {
        ClientResponseTyped<InputStream> res = client
                .get("/stream-result")
                .header(HeaderValues.TE_TRAILERS)
                .request(InputStream.class);
        try (var ins = res.entity()) {
            assertThat(ins.readAllBytes(), is(DATA.repeat(3).getBytes()));
        }
        assertThat(res.trailers(), hasHeader(HeaderValues.create("Stream-Result", "Kaboom!")));
        checkCachedConnection(res.headers());
    }

    @Test
    void trailersNoTrailers(WebClient client) {
        ClientResponseTyped<String> res = client
                .get("/trailers-no-trailers")
                .request(String.class);

        assertThat(res.status(), CoreMatchers.is(Status.INTERNAL_SERVER_ERROR_500));
        assertThat(res.entity(), CoreMatchers.is(
                "Trailers are supported only when request came with 'TE: trailers' header or "
                        + "response headers have trailer names definition 'Trailer: <trailer-name>'"));
    }

    @Test
    void streamWithTrailersAndLength(WebClient client) throws IOException {
        ClientResponseTyped<InputStream> res = client
                .get("/stream-with-trailers-and-length")
                .header(HeaderValues.TE_TRAILERS)
                .request(InputStream.class);
        assertThat(res.headers(), hasHeader(HeaderValues.TRANSFER_ENCODING_CHUNKED));       // trailers need chunked
        try (var ins = res.entity()) {
            assertThat(ins.readAllBytes(), is(DATA.getBytes()));
        }
        assertThat(res.trailers(), hasHeader(TEST_TRAILER_HEADER));
    }

    private void checkCachedConnection(ClientResponseHeaders h) {
        if (clientPort == -1) {
            clientPort = h.get(CLIENT_PORT_HEADER_NAME).asInt().get();
        }
    }
}
