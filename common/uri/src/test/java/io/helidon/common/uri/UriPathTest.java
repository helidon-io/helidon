/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class UriPathTest {
    @Test
    void testEncodePercent() {
        // the path contains string %20, not a space!
        UriPath path = UriPath.createFromDecoded("/my%20path");
        assertThat(path.path(), is("/my%20path"));
        assertThat(path.rawPath(), is("/my%2520path"));
    }

    @Test
    void testEncodeSpace() {
        // the path contains string %20, not a space!
        UriPath path = UriPath.createFromDecoded("/my path");
        assertThat(path.path(), is("/my path"));
        assertThat(path.rawPath(), is("/my%20path"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "//foo/bar",
            "//foo//bar",
            "/foo//bar",
            "/foo/bar",
    })
    void testDoubleSlash(String rawPath) {
        UriPath path = UriPath.create(rawPath);
        assertThat(path.path(), is("/foo/bar"));
    }
}
