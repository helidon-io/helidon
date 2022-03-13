/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.webserver.cors;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static io.helidon.webserver.cors.CorsSupportHelper.compareOrigins;

public class CompareOriginsTest {

    @Test
    void compareOriginSimpleTest() {
        assertThat(compareOrigins("http://localhost", "http://localhost"), is(true));
        assertThat(compareOrigins("http://localhost/", "http://localhost/"), is(true));
        assertThat(compareOrigins("http://localhost/foo", "http://localhost/foo"), is(true));
        assertThat(compareOrigins("http://localhost/foo", "http://localhost/bar"), is(true));
        assertThat(compareOrigins("http://localhost:8080", "http://localhost:8080"), is(true));
        assertThat(compareOrigins("http://localhost:8080/", "http://localhost:8080/"), is(true));
        assertThat(compareOrigins("http://localhost:8080/foo", "http://localhost:8080/foo"), is(true));
        assertThat(compareOrigins("http://localhost:8080/foo", "http://localhost:8080/bar"), is(true));
    }

    @Test
    void compareOriginComplexTest() {
        assertThat(compareOrigins("http://localhost", "http://localhost:80/foo"), is(true));
        assertThat(compareOrigins("http://localhost/", "http://localhost:80/bar"), is(true));
        assertThat(compareOrigins("https://localhost", "https://localhost:443/foo/bar/baz"), is(true));
        assertThat(compareOrigins("http://localhost/", "http://localhost:80/foo/bar/baz"), is(true));
        assertThat(compareOrigins("http://localhost:80/foo", "http://localhost"), is(true));
        assertThat(compareOrigins("http://localhost:80/bar", "http://localhost/"), is(true));
        assertThat(compareOrigins("https://localhost:443/foo/bar/baz", "https://localhost"), is(true));
        assertThat(compareOrigins("http://localhost:80/foo/bar/baz", "http://localhost/"), is(true));
    }

    @Test
    void compareOriginsPortsTest() {
        assertThat(compareOrigins("http://localhost:80", "http://localhost"), is(true));
        assertThat(compareOrigins("http://localhost", "http://localhost:80"), is(true));
        assertThat(compareOrigins("https://localhost:443", "https://localhost"), is(true));
        assertThat(compareOrigins("https://localhost", "https://localhost:443"), is(true));
        assertThat(compareOrigins("http://localhost:80/", "http://localhost/"), is(true));
        assertThat(compareOrigins("http://localhost/", "http://localhost:80/"), is(true));
        assertThat(compareOrigins("https://localhost:443/", "https://localhost/"), is(true));
        assertThat(compareOrigins("https://localhost/", "https://localhost:443/"), is(true));
    }

    @Test
    void compareOriginsNegativeTest() {
        assertThat(compareOrigins("http://localhost:8080", "http://localhost"), is(false));
        assertThat(compareOrigins("http://localhost", "http://localhost:8080"), is(false));
        assertThat(compareOrigins("https://localhost:443", "http://localhost"), is(false));
        assertThat(compareOrigins("http://localhost", "https://localhost:443"), is(false));
        assertThat(compareOrigins("http://localhost", "http://remotehost/"), is(false));
        assertThat(compareOrigins("http://localhost/", "http://remotehost/"), is(false));
        assertThat(compareOrigins("https://localhost:443/", "https://remotehost/"), is(false));
        assertThat(compareOrigins("https://localhost/", "https://remotehost:443/"), is(false));
        assertThat(compareOrigins("http://localhost", "https://localhost"), is(false));
        assertThat(compareOrigins("http://localhost/", "http://localhost:443/"), is(false));
        assertThat(compareOrigins("https://localhost/", "https://localhost:80/"), is(false));
    }

    @Test
    void compareOriginsMalformedTest() {
        assertThat(compareOrigins("foo", "foo"), is(false));
        assertThat(compareOrigins("http://", "http://"), is(false));
        assertThat(compareOrigins("http://localhost", "http://"), is(false));
        assertThat(compareOrigins("http://localhost", "pttp://"), is(false));
        assertThat(compareOrigins("", ""), is(false));
    }

}
