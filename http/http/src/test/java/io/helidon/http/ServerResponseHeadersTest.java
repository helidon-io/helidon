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

class ServerResponseHeadersTest {

    @Test
    void testSizeAfterValuesRemoval1() {
        ServerResponseHeaders headers = ServerResponseHeaders.create();
        headers.add(HeaderNames.SET_COOKIE, "A");
        assertThat(headers.size(), is(1));
        headers.add(HeaderNames.SET_COOKIE, "B");
        assertThat(headers.size(), is(1));
        headers.remove(HeaderNames.SET_COOKIE);
        assertThat(headers.size(), is(0));
    }

    @Test
    void testSizeAfterValuesRemoval2() {
        ServerResponseHeaders headers = ServerResponseHeaders.create();
        headers.set(HeaderNames.SET_COOKIE, "A", "B");
        assertThat(headers.size(), is(1));
        headers.remove(HeaderNames.SET_COOKIE);
        assertThat(headers.size(), is(0));
    }
}
