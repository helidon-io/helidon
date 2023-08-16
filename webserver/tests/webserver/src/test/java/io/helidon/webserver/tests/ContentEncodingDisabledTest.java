/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import io.helidon.http.Http;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.spi.ServerConnectionSelector;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Verify that server responds with status 400 - Bad Request when:
 * <ul>
 *     <li>Content encoding is completely disabled using custom context which does not contain even
 *         default "dentity" encoder</li>
 *     <li>Request contains Content-Encoding header and also something to trigger EntityStyle.NONE
 *         replacement, e.g it's a POST request with Content-Length &gt; 0</li>
 *     <li>Request headers validation is enabled</li>
 * </ul>
 */
@ServerTest
class ContentEncodingDisabledTest extends ContentEncodingDisabledAbstract {

    ContentEncodingDisabledTest(Http1Client client) {
        super(client);
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        ServerConnectionSelector http1 = Http1ConnectionSelector.builder()
                // Headers validation is enabled by default
                .config(Http1Config.builder().build())
                .build();
        server.addConnectionSelector(http1)
                // Content encoding needs to be completely disabled
                .contentEncoding(emptyEncodingContext());
    }

    @Test
    void testContentEncodingHeader() {
        try (Http1ClientResponse response = client().method(Http.Method.POST)
                .header(Http.HeaderNames.CONTENT_ENCODING, "data")
                .submit("any")) {
            assertThat(response.status(), is(Http.Status.BAD_REQUEST_400));
            assertThat(response.as(String.class), is("Content-Encoding header present when content encoding is disabled"));
        }
    }

}
