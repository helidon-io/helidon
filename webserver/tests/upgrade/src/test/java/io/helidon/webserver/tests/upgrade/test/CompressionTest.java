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

package io.helidon.integration.webserver.upgrade.test;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests support for compression in the webserver.
 */
@ServerTest
public class CompressionTest {
    private static final Header CONTENT_ENCODING_GZIP = HeaderValues.create(HeaderNames.CONTENT_ENCODING, "gzip");
    private static final Header CONTENT_ENCODING_DEFLATE = HeaderValues.create(HeaderNames.CONTENT_ENCODING, "deflate");

    private final Http1Client webClient;

    CompressionTest(Http1Client client) {
        this.webClient = client;
    }

    @SetUpRoute
    public static void routing(HttpRules rules) {
        rules.get("/compressed", (req, res) -> res.send("It works!"));
    }

    /**
     * Test that the entity is decompressed using the correct algorithm.
     */
    @Test
    public void testGzip() {
        Http1ClientRequest request = webClient.get();
        request.header(HeaderValues.create(HeaderNames.ACCEPT_ENCODING, "gzip"));
        try (Http1ClientResponse response = request.path("/compressed").request()) {
            assertThat(response.entity().as(String.class), equalTo("It works!"));
            assertThat(response.headers(), hasHeader(CONTENT_ENCODING_GZIP));
        }
    }

    /**
     * Test that the entity is decompressed using the correct algorithm.
     */
    @Test
    public void testDeflateContent() {
        Http1ClientRequest builder = webClient.get();
        builder.header(HeaderValues.create(HeaderNames.ACCEPT_ENCODING, "deflate"));
        try (Http1ClientResponse response = builder.path("/compressed").request()) {
            assertThat(response.entity().as(String.class), equalTo("It works!"));
            assertThat(response.headers(), hasHeader(CONTENT_ENCODING_DEFLATE));
        }
    }
}
