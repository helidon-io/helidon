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
 */

package io.helidon.cors;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;

class CompareOriginsTest {

    @Test
    void compareOriginSimpleTest() {
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost", "http://localhost"), is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost/", "http://localhost/"), is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost/foo", "http://localhost/foo"), is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost/foo", "http://localhost/bar"), is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost:8080", "http://localhost:8080"), is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost:8080/", "http://localhost:8080/"), is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost:8080/foo", "http://localhost:8080/foo"),
                                 is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost:8080/foo", "http://localhost:8080/bar"),
                                 is(true));
    }

    @Test
    void compareOriginComplexTest() {
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost", "http://localhost:80/foo"), is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost/", "http://localhost:80/bar"), is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("https://localhost", "https://localhost:443/foo/bar/baz"),
                                 is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost/", "http://localhost:80/foo/bar/baz"),
                                 is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost:80/foo", "http://localhost"), is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost:80/bar", "http://localhost/"), is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("https://localhost:443/foo/bar/baz", "https://localhost"),
                                 is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost:80/foo/bar/baz", "http://localhost/"),
                                 is(true));
    }

    @Test
    void compareOriginsPortsTest() {
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost:80", "http://localhost"), is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost", "http://localhost:80"), is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("https://localhost:443", "https://localhost"), is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("https://localhost", "https://localhost:443"), is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost:80/", "http://localhost/"), is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost/", "http://localhost:80/"), is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("https://localhost:443/", "https://localhost/"), is(true));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("https://localhost/", "https://localhost:443/"), is(true));
    }

    @Test
    void compareOriginsNegativeTest() {
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost:8080", "http://localhost"), is(false));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost", "http://localhost:8080"), is(false));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("https://localhost:443", "http://localhost"), is(false));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost", "https://localhost:443"), is(false));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost", "http://remotehost/"), is(false));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost/", "http://remotehost/"), is(false));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("https://localhost:443/", "https://remotehost/"), is(false));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("https://localhost/", "https://remotehost:443/"), is(false));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost", "https://localhost"), is(false));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost/", "http://localhost:443/"), is(false));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("https://localhost/", "https://localhost:80/"), is(false));
    }

    @Test
    void compareOriginsMalformedTest() {
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("foo", "foo"), is(false));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://", "http://"), is(false));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost", "http://"), is(false));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("http://localhost", "pttp://"), is(false));
        MatcherAssert.assertThat(CorsSupportHelper.compareOrigins("", ""), is(false));
    }
}