/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.webclient.grpc;

import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;

import io.grpc.Metadata;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

class GrpcBaseClientCallTest {

    @Test
    void testHeadersAndMetadata() {
        Metadata metadata = new Metadata();
        Metadata.Key<String> key = Metadata.Key.of("cookie", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(key, "sugar");
        WritableHeaders<?> headers = GrpcBaseClientCall.setupHeaders(metadata, "localhost", "foo");
        assertThat(headers.size(), greaterThan(2));
        assertThat(headers.get(Http2Headers.AUTHORITY_NAME).get(), is("localhost"));
        assertThat(headers.get(Http2Headers.METHOD_NAME).get(), is("POST"));
        assertThat(headers.get(Http2Headers.PATH_NAME).get(), is("/foo"));
        assertThat(headers.get(Http2Headers.SCHEME_NAME).get(), is("http"));
        assertThat(headers.get(HeaderNames.COOKIE).get(), is("sugar"));
    }
}
