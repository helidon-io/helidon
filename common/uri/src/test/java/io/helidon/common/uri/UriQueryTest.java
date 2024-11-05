/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

import java.net.URI;
import java.net.URLEncoder;

import io.helidon.common.mapper.OptionalValue;

import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UriQueryTest {
    @Test
    void sanityParse() {
        UriQuery uriQuery = UriQuery.create(URI.create("http://foo/bar?a=b&c=d&a=e"));

        assertThat(uriQuery.all("a"), hasItems("b", "e"));
        assertThat(uriQuery.all("c"), hasItems("d"));
        assertThat(uriQuery.contains("z"), is(false));
    }

    @Test
    void testEncoded() {
        UriQuery uriQuery = UriQuery.create("a=" + URLEncoder.encode("1&b=2", US_ASCII));
        assertThat(uriQuery.get("a"), is("1&b=2"));
    }

    @Test
    void testEncodedWithinBrackets() {
        UriQuery uriQuery = UriQuery.create("msg=[Hello%20World]");
        assertThat(uriQuery.get("msg"), is("[Hello World]"));
    }

    @Test
    void testEncodedOtherChars() {
        UriQuery uriQuery = UriQuery.create("a=b%26c=d&e=f&e=g&h=x%63%23e%3c");
        assertThat("The query string must remain encoded otherwise no-one could tell whether a '&' was really a '&' or '%26'",
                   uriQuery.rawValue(),
                   is("a=b%26c=d&e=f&e=g&h=x%63%23e%3c"));

        assertThat(uriQuery.all("e"), hasItems("f", "g"));
        assertThat(uriQuery.get("h"), is("xc#e<"));
        assertThat(uriQuery.get("a"), is("b&c=d"));
    }

    @Test
    void testEmptyQueryString() {
        UriQuery uriQuery = UriQuery.create("");
        assertThat("Empty check with empty string", uriQuery.isEmpty(), is(true));
    }

    @Test
    void testNullQueryFails() {
        assertThrows(NullPointerException.class, () -> UriQuery.create((String) null));

    }

    @Test
    void testNullUriFails() {
        assertThrows(NullPointerException.class, () -> UriQuery.create((URI) null));
    }

    @Test
    void issue8710() {
        UriQuery uriQuery = UriQuery.create(URI.create("http://foo/bar?a&b=c"));
        OptionalValue<String> optional = uriQuery.first("a");
        assertThat(optional.isEmpty(), is(true));

        assertThat(uriQuery.all("a"), hasItems());
        assertThat(uriQuery.all("b"), hasItems("c"));
        assertThat(uriQuery.getRaw("a"), is(""));
    }

    @Test
    void testFromQueryString() {
        UriQueryWriteable query = UriQueryWriteable.create();
        query.fromQueryString("p1=v1&p2=v2&p3=%2F%2Fv3%2F%2F&p4=a%20b%20c");
        assertThat(query.get("p1"), is("v1"));
        assertThat(query.get("p2"), is("v2"));
        assertThat(query.get("p3"), is("//v3//"));
        // make sure the encoded value is correct
        assertThat(query.getRaw("p3"), is("%2F%2Fv3%2F%2F"));
        assertThat(query.get("p4"), is("a b c"));
        assertThat(query.getRaw("p4"), is("a%20b%20c"));
    }
}