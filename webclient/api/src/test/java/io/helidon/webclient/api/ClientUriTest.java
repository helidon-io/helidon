/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQueryWriteable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void testDefaultsUppercaseHttps() {
        ClientUri helper = ClientUri.create(URI.create("HTTPS://localhost"));

        assertThat(helper.authority(), is("localhost:443"));
        assertThat(helper.host(), is("localhost"));
        assertThat(helper.path(), is(UriPath.root()));
        assertThat(helper.port(), is(443));
        assertThat(helper.scheme(), is("https"));
    }

    @Test
    void testDefaultsUppercaseHttpsUriInfo() {
        UriInfo baseUri = UriInfo.builder()
                .scheme("HTTPS")
                .host("localhost")
                .build();
        ClientUri helper = ClientUri.create(baseUri);

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

    @ParameterizedTest
    @CsvSource({
            "http://a_1.example.com, a_1.example.com, 80",
            "https://a_1.example.com, a_1.example.com, 443",
            "http://a_1.example.com:8080, a_1.example.com, 8080",
            "http://a_1.example.com:, a_1.example.com, 80",
            "http://a_1.example.com:0, a_1.example.com, 0",
            "http://a_1.example.com:65535, a_1.example.com, 65535",
            "http://a%5F1.example.com:8080, a_1.example.com, 8080",
            "http://a%401.example.com:8080, a%401.example.com, 8080",
            "http://a~1.example.com:8080, a~1.example.com, 8080"
    })
    void testRegisteredNameAuthority(String baseUri, String host, int port) {
        ClientUri uri = ClientUri.create(URI.create(baseUri + "/foo?key=value#fragment"));

        assertThat(uri.host(), is(host));
        assertThat(uri.port(), is(port));
        assertThat(uri.authority(), is(host + ":" + port));
        assertThat(uri.pathWithQueryAndFragment(), is("/foo?key=value#fragment"));
        assertThat(uri.toUri().getRawAuthority(), is(host + ":" + port));
    }

    @ParameterizedTest
    @ValueSource(strings = {"example.com", "a_1.example.com"})
    void testUserInfoIsNotPartOfHost(String host) {
        ClientUri uri = ClientUri.create(URI.create("http://user:p%40ssword@" + host + ":8080/foo"));

        assertThat(uri.host(), is(host));
        assertThat(uri.authority(), is(host + ":8080"));
        assertThat(uri.toString(), is("http://" + host + ":8080/foo"));
    }

    @Test
    void testResolveRegisteredNameAuthority() {
        ClientUri uri = ClientUri.create(URI.create("https://example.com:8443/"));

        uri.resolve(URI.create("//a_1.example.com:9090/foo"));

        assertThat(uri.scheme(), is("https"));
        assertThat(uri.host(), is("a_1.example.com"));
        assertThat(uri.port(), is(9090));
        assertThat(uri.path().path(), is("/foo"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://a_1.example.com:abc/foo",
            "http://a_1.example.com:-1/foo",
            "http://a_1.example.com:+80/foo",
            "http://a_1.example.com:65536/foo",
            "http://a_1.example.com:999999999999999999999/foo",
            "http://a_1.example.com:80:90/foo",
            "http://user@other@a_1.example.com/foo",
            "http://:8080/foo",
            "http://user@/foo"
    })
    void testInvalidAuthorityRejected(String uriString) {
        URI uri = URI.create(uriString);

        assertThrows(IllegalArgumentException.class, () -> ClientUri.create(uri));
    }

    @Test
    void testQueryParams() {
        UriQueryWriteable query = UriQueryWriteable.create();
        query.fromQueryString("p1=v1&p2=v2&p3=%2F%2Fv3%2F%2F");
        assertThat(query.get("p1"), is("v1"));
        assertThat(query.get("p2"), is("v2"));
        assertThat(query.get("p3"), is("//v3//"));
        assertThat(query.getRaw("p3"), is("%2F%2Fv3%2F%2F"));

        ClientUri helper = ClientUri.create(URI.create("http://localhost:8080/loom/quick?" + query.rawValue()));

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
    void testRequestTargetQueryUsesConfiguredEncoding() {
        ClientUri helper = ClientUri.create(URI.create("http://localhost/path"));
        helper.writeableQuery().set("q", "a#b");

        assertThat(helper.requestTargetQuery().rawValue(), is("q=a%23b"));

        helper.skipUriEncoding(true);

        assertThat(helper.requestTargetQuery().rawValue(), is("q=a#b"));
        assertThat(helper.query().rawValue(), is("q=a%23b"));
    }

    @Test
    void testEmptyQueryDelimiter() {
        ClientUri helper = ClientUri.create(URI.create("http://localhost:8080/loom/quick?"));

        assertThat(helper.hasQuery(), is(true));
        assertThat(helper.query().rawValue(), is(""));
        assertThat(helper.pathWithQueryAndFragment(), is("/loom/quick?"));
        assertThat(helper.toUri(), is(URI.create("http://localhost:8080/loom/quick?")));
    }

    @Test
    void testNoQueryDelimiter() {
        ClientUri helper = ClientUri.create(URI.create("http://localhost:8080/loom/quick"));

        assertThat(helper.hasQuery(), is(false));
        assertThat(helper.query().rawValue(), is(""));
        assertThat(helper.pathWithQueryAndFragment(), is("/loom/quick"));
        assertThat(helper.toUri(), is(URI.create("http://localhost:8080/loom/quick")));
    }

    @Test
    void testQueryMultipleValues() {
        UriQueryWriteable query = UriQueryWriteable.create();
        query.fromQueryString("p1=v1&p1=v2");
        assertThat(query.all("p1"), hasItems("v1", "v2"));
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
