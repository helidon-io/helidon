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

package io.helidon.webclient.http3;

import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3RequestHeadersTest {
    private static final HeaderName AUTHORITY = HeaderNames.create(":authority");

    @Test
    void ordinaryHeadersNeedNoCopy() {
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        headers.set(HeaderNames.HOST, "ordinary.example");

        assertThat(Http3RequestHeaders.normalize(headers), sameInstance(headers));
    }

    @Test
    void authorityOverridesHostInIndependentCopyAndPreservesFlags() {
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        headers.set(HeaderNames.HOST, "ordinary.example");
        headers.set(HeaderValues.create(AUTHORITY, true, true, "authority.example:8443"));
        headers.set(HeaderNames.ACCEPT, "text/plain");

        ClientRequestHeaders normalized = Http3RequestHeaders.normalize(headers);

        assertThat(normalized, not(sameInstance(headers)));
        assertThat(normalized.contains(AUTHORITY), is(false));
        assertThat(normalized.get(HeaderNames.HOST).get(), is("authority.example:8443"));
        assertThat(normalized.get(HeaderNames.HOST).changing(), is(true));
        assertThat(normalized.get(HeaderNames.HOST).sensitive(), is(true));
        assertThat(normalized.get(HeaderNames.ACCEPT).get(), is("text/plain"));
        normalized.set(HeaderNames.ACCEPT, "application/json");
        headers.set(AUTHORITY, "changed.example");
        assertThat(headers.get(HeaderNames.HOST).get(), is("ordinary.example"));
        assertThat(headers.get(HeaderNames.ACCEPT).get(), is("text/plain"));
        assertThat(normalized.get(HeaderNames.HOST).get(), is("authority.example:8443"));
    }

    @Test
    void rejectsMultipleAuthorityValuesWithoutChangingInput() {
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        headers.set(HeaderNames.HOST, "ordinary.example");
        headers.set(AUTHORITY, "first.example", "second.example");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                       () -> Http3RequestHeaders.normalize(headers));

        assertThat(failure.getMessage(), is("Request :authority must contain exactly one value"));
        assertThat(headers.get(HeaderNames.HOST).get(), is("ordinary.example"));
        assertThat(headers.get(AUTHORITY).valueCount(), is(2));
    }
}
