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
package io.helidon.nima.tests.integration.server;

import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.ServerConfig;
import io.helidon.nima.webserver.http1.Http1ConnectionSelector;
import io.helidon.nima.webserver.spi.ServerConnectionSelector;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Verify that server responds with status 200 - OK when:
 * <ul>
 *     <li>Content encoding is completely disabled using custom context which does not contain even
 *         default "dentity" encoder</li>
 *     <li>Request contains Content-Encoding header and also something to trigger EntityStyle.NONE
 *         replacement, e.g it's a POST request with Content-Length &gt; 0</li>
 *     <li>Request headers validation is disabled</li>
 * </ul>
 */
@ServerTest
public class ContentEncodingDisabledNoValidationTest extends ContentEncodingDisabledAbstract {

    ContentEncodingDisabledNoValidationTest(Http1Client client) {
        super(client);
    }

    @SetUpServer
    static void server(ServerConfig.Builder server) {
        ServerConnectionSelector http1 = Http1ConnectionSelector.builder()
                .config(http1Config -> http1Config
                        // Headers validation is disabled
                        .validateHeaders(false))
                .build();

        server.addConnectionSelector(http1)
                // Content encoding needs to be completely disabled
                .contentEncoding(emptyEncodingContext());
    }

    @Test
    void testContentEncodingHeader() {
        try (Http1ClientResponse response = client().method(Http.Method.POST)
                .header(Http.Header.CONTENT_ENCODING, "data")
                .submit("any")) {
            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.as(String.class), is("response"));
        }
    }

}
