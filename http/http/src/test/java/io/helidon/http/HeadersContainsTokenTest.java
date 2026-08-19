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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class HeadersContainsTokenTest {

    @Test
    void returnsFalseWhenHeaderIsMissing() {
        Headers headers = WritableHeaders.create();

        assertThat(headers.containsToken(HeaderValues.CONNECTION_CLOSE), is(false));
        assertThat(headers.containsToken(HeaderValues.create(HeaderNames.CONNECTION, "")), is(false));
    }

    @Test
    void requiresCompleteTokenMatch() {
        Headers headers = WritableHeaders.create()
                .add(HeaderNames.CONNECTION, "disclose, closed");

        assertThat(headers.containsToken(HeaderValues.CONNECTION_CLOSE), is(false));
    }

    @Test
    void ignoresCaseAndStringTrimWhitespace() {
        Headers headers = WritableHeaders.create()
                .add(HeaderNames.CONNECTION, "\u001f ClOsE \u0000");

        assertThat(headers.containsToken(HeaderValues.CONNECTION_CLOSE), is(true));
    }

    @Test
    void searchesRepeatedAndCommaSeparatedValues() {
        Headers headers = WritableHeaders.create()
                .add(HeaderNames.CONNECTION, "keep-alive, upgrade")
                .add(HeaderNames.CONNECTION, "close");
        Header expected = HeaderValues.create(HeaderNames.CONNECTION,
                                              "upgrade, close",
                                              "keep-alive");

        assertThat(headers.containsToken(expected), is(true));
        assertThat(headers.containsToken(HeaderValues.create(HeaderNames.CONNECTION,
                                                             "upgrade, missing")), is(false));
    }

    @Test
    void preservesQuotedCommasInTokens() {
        Headers headers = WritableHeaders.create()
                .add(HeaderNames.CONNECTION, "foo, \"bar,baz\", qux");

        assertThat(headers.containsToken(HeaderValues.create(HeaderNames.CONNECTION, "\"bar,baz\"")), is(true));
        assertThat(headers.containsToken(HeaderValues.create(HeaderNames.CONNECTION, "bar,baz")), is(false));
    }

    @Test
    void preservesUnmatchedQuotedRemainder() {
        Headers headers = WritableHeaders.create()
                .add(HeaderNames.CONNECTION, "foo, \"bar,baz,qux");

        assertThat(headers.containsToken(HeaderValues.create(HeaderNames.CONNECTION, "\"bar,baz,qux")), is(true));
        assertThat(headers.containsToken(HeaderValues.create(HeaderNames.CONNECTION, "qux")), is(false));
    }

    @Test
    void ignoresEmptyExpectedTokensWhenHeaderExists() {
        Headers headers = WritableHeaders.create()
                .add(HeaderNames.CONNECTION, "close");

        assertThat(headers.containsToken(HeaderValues.create(HeaderNames.CONNECTION, ", ,\t")), is(true));
    }

    @Test
    void usesUnicodeCaseInsensitiveComparison() {
        HeaderName name = HeaderNames.create("Custom-Token");
        Headers headers = WritableHeaders.create()
                .add(name, "\u212A");

        assertThat(headers.containsToken(HeaderValues.create(name, "k")), is(true));
    }
}
