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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests {@link HashRequestHeaders.CookieParser}.
 */
public class CookieParserTest {

    @Test
    public void emptyAndNull() throws Exception {
        Parameters p = HashRequestHeaders.CookieParser.parse(null);
        assertNotNull(p);
        assertTrue(p.toMap().isEmpty());
        p = HashRequestHeaders.CookieParser.parse("");
        assertNotNull(p);
        assertTrue(p.toMap().isEmpty());
    }

    @Test
    public void basicMultiValue() throws Exception {
        Parameters p = HashRequestHeaders.CookieParser.parse("foo=bar; aaa=bbb; c=what_the_hell; aaa=ccc");
        assertNotNull(p);
        assertThat(p.all("foo"), contains("bar"));
        assertThat(p.all("aaa"), contains("bbb", "ccc"));
        assertThat(p.all("c"), contains("what_the_hell"));
    }

    @Test
    public void rfc2965() throws Exception {
        String header = "$version=1; foo=bar; $Domain=google.com, aaa=bbb, c=cool; $Domain=google.com; $Path=\"/foo\"";
        Parameters p = HashRequestHeaders.CookieParser.parse(header);
        assertNotNull(p);
        assertThat(p.all("foo"), contains("bar"));
        assertThat(p.all("aaa"), contains("bbb"));
        assertThat(p.all("c"), contains("cool"));
        assertFalse(p.first("$Domain").isPresent());
        assertFalse(p.first("$Path").isPresent());
        assertFalse(p.first("$Version").isPresent());
    }

    @Test
    public void unquote() throws Exception {
        Parameters p = HashRequestHeaders.CookieParser.parse("foo=\"bar\"; aaa=bbb; c=\"what_the_hell\"; aaa=\"ccc\"");
        assertNotNull(p);
        assertThat(p.all("foo"), contains("bar"));
        assertThat(p.all("aaa"), contains("bbb", "ccc"));
    }
}
