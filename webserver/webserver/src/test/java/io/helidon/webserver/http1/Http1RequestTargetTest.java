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

package io.helidon.webserver.http1;

import java.nio.charset.StandardCharsets;

import io.helidon.common.buffers.DataReader;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpPrologue;
import io.helidon.http.RequestException;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http1RequestTargetTest {
    @Test
    void requestAuthorityRejectsHttpSchemeWithoutAuthority() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.set(HeaderNames.HOST, "api.example.com");

        RequestException exception = assertThrows(RequestException.class,
                                                  () -> Http1RequestTarget.requestAuthority(prologue("http:/private"),
                                                                                            headers));

        assertThat(exception.status(), is(Status.BAD_REQUEST_400));
    }

    @Test
    void requestAuthorityDecodesAbsoluteFormAuthority() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.set(HeaderNames.HOST, "api.example.com");

        String authority = Http1RequestTarget.requestAuthority(prologue("https://%61pi.example.com/"), headers);

        assertThat(authority, is("api.example.com"));
    }

    @Test
    void requestAuthorityPreservesEncodedStructuralHostData() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.set(HeaderNames.HOST, "api.example.com");

        String authority = Http1RequestTarget.requestAuthority(prologue("https://api.example.com%3A443/"), headers);

        assertThat(authority, is("api.example.com%3A443"));
    }

    @Test
    void absoluteTargetRetainsRawFormAndExposesDecodedPath() {
        HttpPrologue prologue = prologue("https://api.example.com/private;v=1?key=value");
        Http1RequestTarget requestTarget = (Http1RequestTarget) prologue.uriPath();

        assertThat(requestTarget.rawPath(), is("https://api.example.com/private;v=1"));
        assertThat(requestTarget.rawPathNoParams(), is("https://api.example.com/private"));
        assertThat(requestTarget.path(), is("/private"));
        assertThat(requestTarget.scheme(), is("https"));
        assertThat(requestTarget.authority(), is("api.example.com"));
        assertThat(prologue.query().rawValue(), is("key=value"));
    }

    @Test
    void absoluteTargetRetainsEmptyQueryDelimiter() {
        HttpPrologue prologue = prologue("https://api.example.com/private?");

        assertThat(prologue.hasQuery(), is(true));
        assertThat(prologue.query().rawValue(), is(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"custom:bad|target", "custom:bad%target"})
    void malformedSchemeSpecificPartIsRejected(String requestTarget) {
        RequestException exception = assertThrows(RequestException.class, () -> prologue(requestTarget));

        assertThat(exception.status(), is(Status.BAD_REQUEST_400));
    }

    @Test
    void malformedNonHttpUserInfoIsRejected() {
        HttpPrologue prologue = prologue("custom://user%ZZ@example.test/private");
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.set(HeaderNames.HOST, "example.test");

        RequestException exception = assertThrows(RequestException.class,
                                                  () -> Http1RequestTarget.requestAuthority(prologue, headers));

        assertThat(exception.status(), is(Status.BAD_REQUEST_400));
    }

    private static HttpPrologue prologue(String requestTarget) {
        DataReader reader = DataReader.create(() -> ("GET " + requestTarget + " HTTP/1.1\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        return new Http1Prologue(reader, 256, true).readPrologue();
    }
}
