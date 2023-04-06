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

package io.helidon.nima.webclient;

import java.net.URI;

import io.helidon.common.uri.UriQueryWriteable;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class UriHelperTest {
    @Test
    void testDefaults() {
        UriQueryWriteable query = UriQueryWriteable.create();
        UriHelper helper = UriHelper.create(URI.create("http://localhost"), query);

        assertThat(helper.authority(), is("localhost"));
        assertThat(helper.host(), is("localhost"));
        assertThat(helper.path(), is(""));
        assertThat(helper.port(), is(80));
        assertThat(helper.scheme(), is("http"));
    }

    @Test
    void testDefaultsHttps() {
        UriQueryWriteable query = UriQueryWriteable.create();
        UriHelper helper = UriHelper.create(URI.create("https://localhost"), query);

        assertThat(helper.authority(), is("localhost"));
        assertThat(helper.host(), is("localhost"));
        assertThat(helper.path(), is(""));
        assertThat(helper.port(), is(443));
        assertThat(helper.scheme(), is("https"));
    }

    @Test
    void testNonDefaults() {
        UriQueryWriteable query = UriQueryWriteable.create();
        UriHelper helper = UriHelper.create(URI.create("http://localhost:8080/loom/quick"), query);

        assertThat(helper.authority(), is("localhost:8080"));
        assertThat(helper.host(), is("localhost"));
        assertThat(helper.path(), is("/loom/quick"));
        assertThat(helper.port(), is(8080));
        assertThat(helper.scheme(), is("http"));
    }

    @Test
    void testResolvePath() {
        UriQueryWriteable query = UriQueryWriteable.create();
        UriHelper helper = UriHelper.create(URI.create("http://localhost:8080/loom"), query);
        helper.resolve(URI.create("/quick"), query);
        assertThat(helper.path(), is("/loom/quick"));
    }

    @Test
    void testResolveSchemeAndAuthority() {
        UriQueryWriteable query = UriQueryWriteable.create();
        UriHelper helper = UriHelper.create(URI.create("http://localhost:8080/loom/quick"), query);
        helper.resolve(URI.create("https://www.example.com:80"), query);
        assertThat(helper.authority(), is("www.example.com:80"));
        assertThat(helper.host(), is("www.example.com"));
        assertThat(helper.path(), is(""));
        assertThat(helper.port(), is(80));
        assertThat(helper.scheme(), is("https"));
    }

    @Test
    void testResolveAll() {
        UriQueryWriteable query = UriQueryWriteable.create();
        UriHelper helper = UriHelper.create(URI.create("http://localhost:8080/loom/quick"), query);
        helper.resolve(URI.create("https://www.example.com:80/"), query);
        assertThat(helper.authority(), is("www.example.com:80"));
        assertThat(helper.host(), is("www.example.com"));
        assertThat(helper.path(), is("/"));
        assertThat(helper.port(), is(80));
        assertThat(helper.scheme(), is("https"));
    }
}