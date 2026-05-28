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

package io.helidon.webclient.http2;

import java.net.URI;

import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;
import io.helidon.webclient.api.ClientUri;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class Http2CallChainBaseTest {
    @Test
    void authorityOverridesHostBeforeKeying() {
        ClientRequestHeaders headers = emptyHeaders();
        headers.set(HeaderValues.create(HeaderNames.HOST, "host.example:443"));
        headers.set(HeaderValues.create(Http2Headers.AUTHORITY_NAME, "authority.example:9443"));

        Http2CallChainBase.alignHostHeader(uri(), headers);

        assertThat(headers.first(HeaderNames.HOST).orElseThrow(), is("authority.example:9443"));
    }

    @Test
    void hostIsPreservedWhenAuthorityIsAbsent() {
        ClientRequestHeaders headers = emptyHeaders();
        headers.set(HeaderValues.create(HeaderNames.HOST, "host.example:443"));

        Http2CallChainBase.alignHostHeader(uri(), headers);

        assertThat(headers.first(HeaderNames.HOST).orElseThrow(), is("host.example:443"));
    }

    @Test
    void uriAuthorityIsUsedWhenHeadersAreAbsent() {
        ClientRequestHeaders headers = emptyHeaders();

        Http2CallChainBase.alignHostHeader(uri(), headers);

        assertThat(headers.first(HeaderNames.HOST).orElseThrow(), is("service.example:8443"));
    }

    private static ClientRequestHeaders emptyHeaders() {
        return ClientRequestHeaders.create(WritableHeaders.create());
    }

    private static ClientUri uri() {
        return ClientUri.create(URI.create("https://service.example:8443/path"));
    }
}
