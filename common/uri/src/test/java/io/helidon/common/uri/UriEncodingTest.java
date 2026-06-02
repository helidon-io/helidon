/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
package io.helidon.common.uri;

import org.junit.jupiter.api.Test;

import static io.helidon.common.uri.UriEncoding.decodeUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

class UriEncodingTest {

    @Test
    void testSpaceDecoding() {
        assertThat(decodeUri("%20hello%20world%20"), is(" hello world "));
        assertThat(decodeUri("[%20]hello[%20]world[%20]"), is("[%20]hello[%20]world[%20]"));
        assertThat(decodeUri("+hello+world+"), is(" hello world "));
        assertThat(decodeUri("[+]hello[+]world[+]"), is("[+]hello[+]world[+]"));
    }

    @Test
    void testIPv6Literal() {
        assertThat(decodeUri("http://[fe80::1%lo0]:8080"), is("http://[fe80::1%lo0]:8080"));
    }

    @Test
    void malformedHexEscapesRemainLiteral() {
        assertAll(
                () -> assertThat(decodeUri("%2G"), is("%2G")),
                () -> assertThat(decodeUri("%G2"), is("%G2")),
                () -> assertThat(decodeUri("%+1"), is("%+1")),
                () -> assertThat(decodeUri("%G%41"), is("%GA")),
                () -> assertThat(decodeUri("%4%41"), is("%4A")),
                () -> assertThat(decodeUri("%%41"), is("%A")),
                () -> assertThat(decodeUri("%41%2G%42"), is("A%2GB")),
                () -> assertThat(decodeUri("prefix%G2suffix"), is("prefix%G2suffix")));
    }

    @Test
    void incompletePercentEscapesRemainLiteral() {
        assertAll(
                () -> assertThat(decodeUri("%"), is("%")),
                () -> assertThat(decodeUri("%4"), is("%4")),
                () -> assertThat(decodeUri("%41%"), is("A%")),
                () -> assertThat(decodeUri("prefix%4suffix"), is("prefix%4suffix")));
    }
}
