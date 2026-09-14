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

package io.helidon.webclient.http1;

import java.net.URI;
import java.util.Map;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientUri;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RedirectionProcessorTest {
    private static final Header TEST_CONTENT_ENCODING = HeaderValues.createCached(HeaderNames.CONTENT_ENCODING,
                                                                                   "test-encoding");

    @Test
    void methodAndEntityPreservationUsesStatusCode() {
        assertThat(RedirectionProcessor.keepsMethodAndEntity(Status.TEMPORARY_REDIRECT_307), is(true));
        assertThat(RedirectionProcessor.keepsMethodAndEntity(Status.PERMANENT_REDIRECT_308), is(true));
        assertThat(RedirectionProcessor.keepsMethodAndEntity(Status.create(307, "Custom")), is(true));
        assertThat(RedirectionProcessor.keepsMethodAndEntity(Status.create(308, "Custom")), is(true));
        assertThat(RedirectionProcessor.keepsMethodAndEntity(Status.FOUND_302), is(false));
    }

    @Test
    void entityPreservingRedirectPreservesContentEncoding() {
        Http1ClientRequestImpl request = (Http1ClientRequestImpl) Http1Client.create()
                .put("http://localhost/source")
                .header(TEST_CONTENT_ENCODING);

        Http1ClientRequestImpl redirect = new Http1ClientRequestImpl(request,
                                                                     Method.PUT,
                                                                     ClientUri.create(URI.create("http://localhost/target")),
                                                                     Map.of(),
                                                                     true);

        assertThat(redirect.headers(), hasHeader(TEST_CONTENT_ENCODING));
    }
}
