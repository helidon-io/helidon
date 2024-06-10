/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.api;

import java.net.URI;

import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQueryWriteable;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ClientUriTest {
    @Test
    void testDefaults() {
        ClientUri helper = ClientUri.create(URI.create("http://localhost"));

        assertThat(helper.authority(), is("localhost:80"));
        assertThat(helper.host(), is("localhost"));
        assertThat(helper.path(), is(UriPath.root()));
        assertThat(helper.port(), is(80));
        assertThat(helper.scheme(), is("http"));
    }

    @Test
    void testDefaultsHttps() {
        ClientUri helper = ClientUri.create(URI.create("https://localhost"));

        assertThat(helper.authority(), is("localhost:443"));
        assertThat(helper.host(), is("localhost"));
        assertThat(helper.path(), is(UriPath.root()));
        assertThat(helper.port(), is(443));
        assertThat(helper.scheme(), is("https"));
    }

    @Test
    void testNonDefaults() {
        ClientUri helper = ClientUri.create(URI.create("http://localhost:8080/loom/quick"));

        assertThat(helper.authority(), is("localhost:8080"));
        assertThat(helper.host(), is("localhost"));
        assertThat(helper.path(), is(UriPath.create("/loom/quick")));
        assertThat(helper.port(), is(8080));
        assertThat(helper.scheme(), is("http"));
    }

    @Test
    void testQueryParams() {
        UriQueryWriteable query = UriQueryWriteable.create();
        query.fromQueryString("p1=v1&p2=v2&p3=%2F%2Fv3%2F%2F");
        ClientUri helper = ClientUri.create(URI.create("http://localhost:8080/loom/quick?" + query.value()));

        assertThat(helper.authority(), is("localhost:8080"));
        assertThat(helper.host(), is("localhost"));
        assertThat(helper.path(), is(UriPath.create("/loom/quick")));
        assertThat(helper.port(), is(8080));
        assertThat(helper.scheme(), is("http"));
        assertThat(helper.query().get("p1"), is("v1"));
        assertThat(helper.query().get("p2"), is("v2"));
        assertThat(helper.query().get("p3"), is("//v3//"));
        assertThat(helper.query().getRaw("p3"), is("%2F%2Fv3%2F%2F"));
    }

    @Test
    void testResolvePath() {
        ClientUri helper = ClientUri.create(URI.create("http://localhost:8080/loom"));
        helper.resolve(URI.create("/quick"));
        assertThat(helper.path(), is(UriPath.create("/loom/quick")));
    }

    @Test
    void testResolveSchemeAndAuthority() {
        ClientUri helper = ClientUri.create(URI.create("http://localhost:8080/loom/quick"));
        helper.resolve(URI.create("https://www.example.com:80"));
        assertThat(helper.authority(), is("www.example.com:80"));
        assertThat(helper.host(), is("www.example.com"));
        assertThat(helper.path(), is(UriPath.root()));
        assertThat(helper.port(), is(80));
        assertThat(helper.scheme(), is("https"));
    }

    @Test
    void testResolveAll() {
        ClientUri helper = ClientUri.create(URI.create("http://localhost:8080/loom/quick"));
        helper.resolve(URI.create("https://www.example.com:80/"));
        assertThat(helper.authority(), is("www.example.com:80"));
        assertThat(helper.host(), is("www.example.com"));
        assertThat(helper.path(), is(UriPath.root()));
        assertThat(helper.port(), is(80));
        assertThat(helper.scheme(), is("https"));
    }

    /**
     * Verifies that "+" is interpreted as a space character the query strings.
     * Note that the {@link URI} class does not appear to handle this correctly.
     */
    @Test
    void testResolveQuery() {
        URI uri = URI.create("http://localhost:8080/greet?filter=a+b+c");
        ClientUri clientUri = ClientUri.create();
        clientUri.resolve(uri);
        assertThat(clientUri.query().get("filter"), is("a b c"));
        assertThat(clientUri.query().getRaw("filter"), is("a%20b%20c"));
    }
}