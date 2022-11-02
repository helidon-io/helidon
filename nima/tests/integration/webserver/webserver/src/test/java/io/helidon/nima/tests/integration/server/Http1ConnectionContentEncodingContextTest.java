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

package io.helidon.nima.tests.integration.server;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.nima.http.encoding.ContentDecoder;
import io.helidon.nima.http.encoding.ContentEncoder;
import io.helidon.nima.http.encoding.ContentEncodingContext;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http1.Http1ConnectionProvider;
import io.helidon.nima.webserver.spi.ServerConnectionProvider;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@ServerTest
class Http1ConnectionContentEncodingContextTest {

    private static final CustomizedEncodingContext encodingContext = new CustomizedEncodingContext();

    private final Http1Client client;

    Http1ConnectionContentEncodingContextTest(Http1Client socketHttpClient) {
        this.client = socketHttpClient;
    }

    @SetUpServer
    static void server(WebServer.Builder server) {
        ServerConnectionProvider http1 = Http1ConnectionProvider.builder()
                .contentEncodingContext(encodingContext)
                .build();

        server.addConnectionProvider(http1);
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/hello", (req, res) ->  {
            res.send("hello nima");
        });
    }

    @Test
    void testCustomizeContentEncodingContext() {
        try (Http1ClientResponse response = client.method(Http.Method.GET).uri("/hello")
                .header(Http.Header.create(Http.Header.CONTENT_ENCODING, "identity"))
                .submit("hello")) {
            assertThat(response.entity().as(String.class), equalTo("hello nima"));
            assertThat(encodingContext.CONTENT_ENCODING_CNT, greaterThan(0));
        }
    }


    private static class CustomizedEncodingContext implements ContentEncodingContext {
        private static final String CONTENT_ENCODING = "identity";
        private static int CONTENT_ENCODING_CNT = 0;

        ContentEncodingContext contentEncodingContext = ContentEncodingContext.create();

        @Override
        public boolean contentEncodingEnabled() {
            return contentEncodingContext.contentEncodingEnabled();
        }

        @Override
        public boolean contentDecodingEnabled() {
            return contentEncodingContext.contentDecodingEnabled();
        }

        @Override
        public boolean contentEncodingSupported(String encodingId) {
            return contentEncodingContext.contentEncodingSupported(encodingId);
        }

        @Override
        public boolean contentDecodingSupported(String encodingId) {
            return contentEncodingContext.contentDecodingSupported(encodingId);
        }

        @Override
        public ContentEncoder encoder(String encodingId) throws NoSuchElementException {
            return contentEncodingContext.encoder(encodingId);
        }

        @Override
        public ContentDecoder decoder(String encodingId) throws NoSuchElementException {
            assertThat(encodingId, is(CONTENT_ENCODING));
            CONTENT_ENCODING_CNT++;
            return contentEncodingContext.decoder(encodingId);
        }

        @Override
        public ContentEncoder encoder(Headers headers) {
            return contentEncodingContext.encoder(headers);
        }

    }
}
