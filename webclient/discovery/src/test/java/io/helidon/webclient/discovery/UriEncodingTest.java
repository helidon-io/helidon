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
package io.helidon.webclient.discovery;

import java.net.URI;

import io.helidon.common.uri.UriEncoding;
import io.helidon.webclient.api.ClientUri;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class UriEncodingTest {

    private UriEncodingTest() {
        super();
    }

    @Test
    void testDecode() {
        ClientUri c = ClientUri.create();
        assertThat(c.scheme(), is("http")); // !
        assertThat(c.host(), is("localhost")); // !
        assertThat(c.port(), is(80)); // !
        c.path("A B");
        assertThat(c.path().rawPath(), is("A%20B"));
        assertThat(c.path().path(), is("A B"));
        assertThat(UriEncoding.decodeUri("A+B"), is("A B")); // !
        assertThat(URI.create("A+B").getPath(), is("A+B"));
        assertThat(URI.create("A%20B").getPath(), is("A B"));
    }

}
