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

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class HttpPrologueTest {
    @Test
    void testPrologueWithAll() {
        HttpPrologue prologue = HttpPrologue.create("HTTP/1.1",
                                                    "HTTP",
                                                    "1.1",
                                                    Method.GET,
                                                    "/admin;a=b/list;c=d;e=f?first=second#fragment",
                                                    true);

        assertThat(prologue.rawProtocol(), is("HTTP/1.1"));
        assertThat(prologue.protocol(), is("HTTP"));
        assertThat(prologue.protocolVersion(), is("1.1"));
        assertThat(prologue.method(), is(Method.GET));
        assertThat(prologue.uriPath().rawPathNoParams(), is("/admin/list"));
        assertThat(prologue.query().rawValue(), is("first=second"));
        assertThat(prologue.fragment().hasValue(), is(true));
        assertThat(prologue.fragment().value(), is("fragment"));
    }

    @Test
    void testPrologueEncodedPath() {
        String path = "/one/two?a=b%26c=d&e=f&e=g&h=x%63%23e%3c#a%20frag%23ment";

        HttpPrologue prologue = HttpPrologue.create("HTTP/1.1",
                                                    "HTTP",
                                                    "1.1",
                                                    Method.GET,
                                                    path,
                                                    true);

        assertThat(prologue.rawProtocol(), is("HTTP/1.1"));
        assertThat(prologue.protocol(), is("HTTP"));
        assertThat(prologue.protocolVersion(), is("1.1"));
        assertThat(prologue.method(), is(Method.GET));
        assertThat(prologue.uriPath().rawPathNoParams(), is("/one/two"));
        assertThat(prologue.query().rawValue(), is("a=b%26c=d&e=f&e=g&h=x%63%23e%3c"));
        assertThat(prologue.fragment().hasValue(), is(true));
        assertThat(prologue.fragment().value(), is("a frag#ment"));
    }
}