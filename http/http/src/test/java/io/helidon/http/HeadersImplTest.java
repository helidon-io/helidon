/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

class HeadersImplTest {

    @Test
    void testValuesCase() {
        Header foo1 = HeaderValues.create("foo", "bar", "baz");
        Header foo2 = HeaderValues.create("foo", "bar", "baz");
        assertThat(foo1.equals(foo2), is(true));
    }

    @Test
    void testValuesOrderAndCase() {
        Header foo1 = HeaderValues.create("foo", "BaZ", "bar");
        Header foo2 = HeaderValues.create("foo", "BAR", "baz");
        assertThat(foo1.equals(foo2), is(true));
    }

    @Test
    void testBadLength() {
        Header foo1 = HeaderValues.create("foo", "bar");
        Header foo2 = HeaderValues.create("foo", "BAR", "baz");
        assertThat(foo1.equals(foo2), is(false));
    }

    @Test
    void testBadName() {
        Header foo1 = HeaderValues.create("foo", "bar");
        Header foo2 = HeaderValues.create("fuu", "BAR", "baz");
        assertThat(foo1.equals(foo2), is(false));
    }

    @Test
    void testBadValues() {
        Header foo1 = HeaderValues.create("foo", "BaZ", "bar");
        Header foo2 = HeaderValues.create("foo", "www", "baz");
        assertThat(foo1.equals(foo2), is(false));
    }

    @Test
    void testHeadersContains() {
        WritableHeaders<?> headers = WritableHeaders.create();
        Header foo = HeaderValues.create("foo", "bar");
        headers.set(foo);
        assertThat(headers.contains(foo), is(true));
        assertThat(headers.contains(HeaderValues.create("Foo", "Bar")), is(true));
    }

    @Test
    void testHeadersContainsMany() {
        WritableHeaders<?> headers = WritableHeaders.create();
        Header foo1 = HeaderValues.create("foo", "bar", "baz", "bat");
        headers.set(foo1);
        assertThat(headers.contains(foo1), is(true));
        Header foo2 = HeaderValues.create("Foo", "BAZ", "Bar", "BaT");
        assertThat(headers.contains(foo2), is(true));
    }
}
