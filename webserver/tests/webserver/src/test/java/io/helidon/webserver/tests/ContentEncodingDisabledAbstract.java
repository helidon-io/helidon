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

import java.util.NoSuchElementException;

import io.helidon.http.Headers;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.encoding.ContentEncodingContextConfig;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Common code for tests to verify server response when
 * <ul>
 *     <li>content encoding is completely disabled,</li>
 *     <li>request contains Content-Encoding header</li>
 *     <li>and additional data with value of Content-Length header &gt; 0</li>
 * </ul>
 */
abstract class ContentEncodingDisabledAbstract {

    private final Http1Client client;

    ContentEncodingDisabledAbstract(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.any(ContentEncodingDisabledAbstract::handleRequest);
    }

    private static void handleRequest(ServerRequest request, ServerResponse response) {
        response.send("response");
    }

    Http1Client client() {
        return client;
    }

    static EmptyEncodingContext emptyEncodingContext() {
        return new EmptyEncodingContext();
    }

    /**
     * Completely disable encoding. Even default "identity" encoding shall not be present.
     */
    static final class EmptyEncodingContext implements ContentEncodingContext {

        private EmptyEncodingContext() {
        }

        @Override
        public ContentEncodingContextConfig prototype() {
            return ContentEncodingContextConfig.create();
        }

        @Override
        public boolean contentEncodingEnabled() {
            return false;
        }

        @Override
        public boolean contentDecodingEnabled() {
            return false;
        }

        @Override
        public boolean contentEncodingSupported(String encodingId) {
            return false;
        }

        @Override
        public boolean contentDecodingSupported(String encodingId) {
            return false;
        }

        @Override
        public ContentEncoder encoder(String encodingId) throws NoSuchElementException {
            return null;
        }

        @Override
        public ContentDecoder decoder(String encodingId) throws NoSuchElementException {
            return null;
        }

        @Override
        public ContentEncoder encoder(Headers headers) {
            return null;
        }

    }

}
