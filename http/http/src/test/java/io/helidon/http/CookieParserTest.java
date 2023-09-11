/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import io.helidon.common.parameters.Parameters;

import org.junit.jupiter.api.Test;

import static io.helidon.http.HeaderNames.COOKIE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

class CookieParserTest {
    @Test
    public void rfc2965() throws Exception {
        String header = "$version=1; foo=bar; $Domain=google.com, aaa=bbb, c=cool; $Domain=google.com; $Path=\"/foo\"";
        Parameters p = CookieParser.parse(HeaderValues.create(COOKIE, header));
        assertThat(p, notNullValue());
        assertThat(p.all("foo"), contains("bar"));
        assertThat(p.all("aaa"), contains("bbb"));
        assertThat(p.all("c"), contains("cool"));
        assertThat(p.contains("$Domain"), is(false));
        assertThat(p.contains("$Path"), is(false));
        assertThat(p.contains("$Version"), is(false));
    }

    @Test
    public void unquote() throws Exception {
        Parameters p = CookieParser.parse(HeaderValues.create(COOKIE, "foo=\"bar\"; aaa=bbb; c=\"what_the_hell\"; aaa=\"ccc\""));
        assertThat(p, notNullValue());
        assertThat(p.all("foo"), contains("bar"));
        assertThat(p.all("aaa"), contains("bbb", "ccc"));
    }

    @Test
    void testEmpty() {
        Parameters empty = CookieParser.empty();
        assertThat(empty.isEmpty(), is(true));
    }

    @Test
    void testMultiValueSingleHeader() {
        Parameters cookies = CookieParser.parse(HeaderValues.create(COOKIE, "foo=bar; aaa=bbb; c=here; aaa=ccc"));
        assertThat(cookies, notNullValue());
        assertThat(cookies.all("foo"), contains("bar"));
        assertThat(cookies.all("aaa"), contains("bbb", "ccc"));
        assertThat(cookies.all("c"), contains("here"));
    }

    @Test
    void testMultiValueMultiHeader() {
        Parameters cookies = CookieParser.parse(HeaderValues.create(COOKIE, "foo=bar; aaa=bbb; c=here", "aaa=ccc"));
        assertThat(cookies, notNullValue());
        assertThat(cookies.all("foo"), contains("bar"));
        assertThat(cookies.all("aaa"), contains("bbb", "ccc"));
        assertThat(cookies.all("c"), contains("here"));
    }
}