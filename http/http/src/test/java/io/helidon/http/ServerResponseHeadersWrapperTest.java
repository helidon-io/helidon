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

import java.util.List;

import org.junit.jupiter.api.Test;

import static io.helidon.http.HeaderNames.SET_COOKIE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ServerResponseHeadersWrapperTest {

    private static final HeaderName A = HeaderNames.create("A");
    private static final HeaderName B = HeaderNames.create("B");

    @Test
    void testSet() {
        ServerResponseHeaders headers = ServerResponseHeaders.create();
        assertThat(headers.size(), is(0));
        ServerResponseHeadersWrapper wrapper = ServerResponseHeadersWrapper.create(headers);
        wrapper.set(A, "A");
        assertThat(wrapper.size(), is(1));
        assertThat(wrapper.get(A).get(), is("A"));
        assertThat(wrapper.rollback().size(), is(0));
    }

    @Test
    void testSetExisting() {
        ServerResponseHeaders headers = ServerResponseHeaders.create();
        headers.set(A, "A");
        assertThat(headers.size(), is(1));
        ServerResponseHeadersWrapper wrapper = ServerResponseHeadersWrapper.create(headers);
        wrapper.set(A, "AA");
        wrapper.set(B, "B");
        assertThat(wrapper.size(), is(2));
        assertThat(wrapper.get(A).get(), is("AA"));
        assertThat(wrapper.get(B).get(), is("B"));
        ServerResponseHeaders rollback = wrapper.rollback();
        assertThat(rollback.size(), is(1));
        assertThat(rollback.get(A).get(), is("A"));
    }

    @Test
    void testAdd() {
        ServerResponseHeaders headers = ServerResponseHeaders.create();
        assertThat(headers.size(), is(0));
        ServerResponseHeadersWrapper wrapper = ServerResponseHeadersWrapper.create(headers);
        wrapper.add(A, "A");
        assertThat(wrapper.size(), is(1));
        assertThat(wrapper.get(A).get(), is("A"));
        assertThat(wrapper.rollback().size(), is(0));
    }

    @Test
    void testAddExisting() {
        ServerResponseHeaders headers = ServerResponseHeaders.create();
        headers.set(A, "A");
        assertThat(headers.size(), is(1));
        ServerResponseHeadersWrapper wrapper = ServerResponseHeadersWrapper.create(headers);
        wrapper.add(A, "AA");
        wrapper.add(B, "B");
        assertThat(wrapper.size(), is(2));
        assertThat(wrapper.get(A).allValues(), is(List.of("A", "AA")));
        assertThat(wrapper.get(B).get(), is("B"));
        ServerResponseHeaders rollback = wrapper.rollback();
        assertThat(rollback.size(), is(1));
        assertThat(rollback.get(A).allValues(), is(List.of("A")));
    }

    @Test
    void testRemove() {
        ServerResponseHeaders headers = ServerResponseHeaders.create();
        headers.set(A, "A");
        assertThat(headers.size(), is(1));
        ServerResponseHeadersWrapper wrapper = ServerResponseHeadersWrapper.create(headers);
        wrapper.remove(A);
        wrapper.set(B, "B");
        assertThat(wrapper.size(), is(1));
        assertThat(wrapper.get(B).get(), is("B"));
        ServerResponseHeaders rollback = wrapper.rollback();
        assertThat(rollback.size(), is(1));
        assertThat(rollback.get(A).get(), is("A"));
    }

    @Test
    void testClear() {
        ServerResponseHeaders headers = ServerResponseHeaders.create();
        headers.set(A, "A");
        assertThat(headers.size(), is(1));
        ServerResponseHeadersWrapper wrapper = ServerResponseHeadersWrapper.create(headers);
        wrapper.clear();
        assertThat(wrapper.size(), is(0));
        ServerResponseHeaders rollback = wrapper.rollback();
        assertThat(rollback.size(), is(1));
        assertThat(rollback.get(A).get(), is("A"));
    }

    @Test
    void testMixed() {
        ServerResponseHeaders headers = ServerResponseHeaders.create();
        headers.set(A, "A");
        headers.add(B, "B");
        assertThat(headers.size(), is(2));
        ServerResponseHeadersWrapper wrapper = ServerResponseHeadersWrapper.create(headers);
        wrapper.add(A, "AA");
        assertThat(wrapper.size(), is(2));
        wrapper.clear();
        assertThat(wrapper.size(), is(0));
        wrapper.add(A, "A");
        wrapper.add(A, "AA");
        wrapper.setIfAbsent(HeaderValues.create(B, "B"));
        assertThat(wrapper.size(), is(2));
        assertThat(wrapper.get(A).allValues(), is(List.of("A", "AA")));
        assertThat(wrapper.get(B).get(), is("B"));
        ServerResponseHeaders rollback = wrapper.rollback();
        assertThat(rollback.size(), is(2));
        assertThat(rollback.get(A).get(), is("A"));
        assertThat(rollback.get(B).get(), is("B"));
    }

    @Test
    void testCookies() {
        ServerResponseHeaders headers = ServerResponseHeaders.create();
        headers.addCookie("C", "C");
        assertThat(headers.size(), is(1));
        ServerResponseHeadersWrapper wrapper = ServerResponseHeadersWrapper.create(headers);
        wrapper.addCookie("D", "D");
        assertThat(wrapper.size(), is(1));      // just Set-Cookie
        wrapper.clearCookie("C");
        assertThat(wrapper.size(), is(1));      // just Set-Cookie
        ServerResponseHeaders rollback = wrapper.rollback();
        assertThat(rollback.size(), is(1));
        assertThat(rollback.get(SET_COOKIE).get(), is("C=C"));
    }
}
