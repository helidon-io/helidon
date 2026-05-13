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

package io.helidon.http;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HeaderLogFormatterTest {
    @Test
    void testDefaultSafeHeaders() {
        Headers headers = WritableHeaders.create()
                .add(HeaderNames.AUTHORIZATION, "Bearer secret-token")
                .add(HeaderNames.COOKIE, "session=secret-cookie")
                .add(HeaderNames.create(":authority"), "example.test")
                .add(HeaderNames.CONTENT_TYPE, "text/plain")
                .add(HeaderNames.CONTENT_LENGTH, "12");

        String formatted = LogFormatter.create(HttpLogConfig.create())
                .format(headers);

        assertThat(formatted, containsString("Authorization:"));
        assertThat(formatted, containsString("Cookie:"));
        assertThat(formatted, containsString(":authority: <redacted>"));
        assertThat(formatted, containsString("Content-Type: text/plain"));
        assertThat(formatted, containsString("Content-Length: 12"));
        assertThat(formatted, not(containsString("Bearer secret-token")));
        assertThat(formatted, not(containsString("session=secret-cookie")));
        assertThat(formatted, not(containsString("example.test")));
    }

    @Test
    void testCustomSafeHeaders() {
        Headers headers = WritableHeaders.create()
                .add(HeaderNames.AUTHORIZATION, "Bearer secret-token")
                .add(HeaderNames.create("X-Api-Key"), "secret-key")
                .add(HeaderNames.CONTENT_TYPE, "text/plain");

        String formatted = LogFormatter.create(HttpLogConfig.builder()
                                                       .safeHeaders(Set.of(HeaderNames.AUTHORIZATION))
                                                       .buildPrototype())
                .format(headers);

        assertThat(formatted, containsString("Authorization: <redacted>"));
        assertThat(formatted, containsString("X-Api-Key: <redacted>"));
        assertThat(formatted, containsString("Content-Type:"));
        assertThat(formatted, not(containsString("Bearer secret-token")));
        assertThat(formatted, not(containsString("secret-key")));
        assertThat(formatted, not(containsString("Content-Type: text/plain")));
    }

    @Test
    void testEscapesConfiguredSafeHeaderValues() {
        Headers headers = WritableHeaders.create()
                .add(HeaderNames.create("X-Safe"), "first\r\nForged: value\u0001");

        String formatted = LogFormatter.create(HttpLogConfig.builder()
                                                       .safeHeaders(Set.of(HeaderNames.create("x-safe")))
                                                       .buildPrototype())
                .format(headers);

        assertThat(formatted, containsString("X-Safe: first\\r\\nForged: value\\u0001"));
        assertThat(formatted, not(containsString("\r")));
        assertThat(formatted, not(containsString("\nForged:")));
    }

    @Test
    void testFormatAllEscapesButDoesNotRedact() {
        Headers headers = WritableHeaders.create()
                .add(HeaderNames.AUTHORIZATION, "Bearer secret-token")
                .add(HeaderNames.create("X-Safe"), "first\r\nForged: value");

        String formatted = LogFormatter.create(HttpLogConfig.create())
                .formatAll(headers);

        assertThat(formatted, containsString("Authorization: Bearer secret-token"));
        assertThat(formatted, containsString("X-Safe: first\\r\\nForged: value"));
        assertThat(formatted, not(containsString("<redacted>")));
        assertThat(formatted, not(containsString("\r")));
        assertThat(formatted, not(containsString("\nForged:")));
    }

    @Test
    void testDefaultSafeHeadersReturnsMutableCopy() {
        Set<HeaderName> defaults = LogFormatter.defaultSafeHeaderNames();

        defaults.clear();

        assertThat(LogFormatter.defaultSafeHeaderNames(), hasItem(HeaderNames.HOST));
        assertThat(LogFormatter.defaultSafeHeaderNames(), not(hasItem(HeaderNames.create(":authority"))));
    }

    @Test
    void testPublicMethodsRejectNull() {
        HttpLogConfig config = null;

        assertThrows(NullPointerException.class, () -> LogFormatter.create(config));
        assertThrows(NullPointerException.class, () -> LogFormatter.escape(null));
        assertThrows(NullPointerException.class, () -> LogFormatter.create(HttpLogConfig.create()).formatAll(null));
    }
}
