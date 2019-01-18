/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import io.helidon.common.http.Parameters;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;


/**
 * Tests {@link HashRequestHeaders.CookieParser}.
 */
public class CookieParserTest {

    @Test
    public void emptyAndNull() throws Exception {
        Parameters p = HashRequestHeaders.CookieParser.parse(null);
        assertThat(p, notNullValue());
        assertThat(p.toMap().isEmpty(), is(true));
        p = HashRequestHeaders.CookieParser.parse("");
        assertThat(p, notNullValue());
        assertThat(p.toMap().isEmpty(), is(true));
    }

    @Test
    public void basicMultiValue() throws Exception {
        Parameters p = HashRequestHeaders.CookieParser.parse("foo=bar; aaa=bbb; c=what_the_hell; aaa=ccc");
        assertThat(p, notNullValue());
        assertThat(p.all("foo"), contains("bar"));
        assertThat(p.all("aaa"), contains("bbb", "ccc"));
        assertThat(p.all("c"), contains("what_the_hell"));
    }

    @Test
    public void rfc2965() throws Exception {
        String header = "$version=1; foo=bar; $Domain=google.com, aaa=bbb, c=cool; $Domain=google.com; $Path=\"/foo\"";
        Parameters p = HashRequestHeaders.CookieParser.parse(header);
        assertThat(p, notNullValue());
        assertThat(p.all("foo"), contains("bar"));
        assertThat(p.all("aaa"), contains("bbb"));
        assertThat(p.all("c"), contains("cool"));
        assertThat(p.first("$Domain").isPresent(), is(false));
        assertThat(p.first("$Path").isPresent(), is(false));
        assertThat(p.first("$Version").isPresent(), is(false));
    }

    @Test
    public void unquote() throws Exception {
        Parameters p = HashRequestHeaders.CookieParser.parse("foo=\"bar\"; aaa=bbb; c=\"what_the_hell\"; aaa=\"ccc\"");
        assertThat(p, notNullValue());
        assertThat(p.all("foo"), contains("bar"));
        assertThat(p.all("aaa"), contains("bbb", "ccc"));
    }
}
