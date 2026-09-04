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
import java.util.List;

import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQueryWriteable;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
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

    @Test
    void preservesRawQueryWireFormAndDecodedAccess() {
        String rawQuery = "first=one&space=a%20b&first=two&plus=a+b&escaped=%2f%25";
        ClientUri clientUri = ClientUri.create(URI.create("http://localhost:8080/path?" + rawQuery + "#fragment"));

        assertThat(clientUri.pathWithQueryAndFragment(), is("/path?" + rawQuery + "#fragment"));
        assertThat(clientUri.toUri(), is(URI.create("http://localhost:8080/path?" + rawQuery + "#fragment")));
        assertThat(clientUri.query().all("first"), is(List.of("one", "two")));
        assertThat(clientUri.query().get("space"), is("a b"));
        assertThat(clientUri.query().get("plus"), is("a b"));
        assertThat(clientUri.query().get("escaped"), is("/%"));
        assertThat(clientUri.query().getRaw("plus"), is("a%20b"));
    }

    @Test
    void writableQueryMutationUsesCurrentSerialization() {
        String rawQuery = "first=one&second=%2f&first=two";
        ClientUri clientUri = ClientUri.create(URI.create("http://localhost/path?" + rawQuery));

        clientUri.writeableQuery().set("second", "changed");

        String requestTarget = clientUri.pathWithQueryAndFragment();
        assertThat(requestTarget, startsWith("/path?"));
        assertThat(requestTarget, not(is("/path?" + rawQuery)));
        assertThat(clientUri.query().get("second"), is("changed"));
    }

    @Test
    void copiesAndResolvesPreservedRawQuery() {
        String rawUri = "http://localhost:8080/path?one=1&two=%2F&one=2#fragment";
        ClientUri original = ClientUri.create(URI.create(rawUri));
        ClientUri copy = ClientUri.create(original);
        ClientUri resolved = ClientUri.create().resolve(original);

        assertThat(copy.toUri(), is(URI.create(rawUri)));
        assertThat(resolved.toUri(), is(URI.create(rawUri)));
    }

    @Test
    void preservesEmptyQueryDelimiterWithFragment() {
        String rawUri = "http://localhost:8080/path?#fragment";
        ClientUri clientUri = ClientUri.create(URI.create(rawUri));

        assertThat(clientUri.hasQuery(), is(true));
        assertThat(clientUri.pathWithQueryAndFragment(), is("/path?#fragment"));
        assertThat(ClientUri.create(clientUri).toUri(), is(URI.create(rawUri)));
    }

    @Test
    void preservesRawPathAndDecodedPathAccess() {
        String rawPath = "/capture/a%2Fb/%25/%2E;name=a%2Fb";
        ClientUri clientUri = ClientUri.create(URI.create("http://localhost:8080" + rawPath));

        assertThat(clientUri.path().rawPath(), is(rawPath));
        assertThat(clientUri.path().path(), is("/capture/a/b/%/."));
        assertThat(clientUri.pathWithQueryAndFragment(), is(rawPath));
        assertThat(clientUri.toUri(), is(URI.create("http://localhost:8080" + rawPath)));
    }

    @Test
    void queryAndFragmentReferencesRetainInheritedRawPath() {
        URI source = URI.create("http://localhost/capture/a%2Fb/%25");
        ClientUri queryRedirect = ClientUri.create(source).resolve(URI.create("?value=%2F"));
        ClientUri fragmentRedirect = ClientUri.create(source).resolve(URI.create("#next"));

        assertThat(queryRedirect.pathWithQueryAndFragment(), is("/capture/a%2Fb/%25?value=%2F"));
        assertThat(fragmentRedirect.pathWithQueryAndFragment(), is("/capture/a%2Fb/%25#next"));
        assertThat(queryRedirect.path().path(), is("/capture/a/b/%"));
        assertThat(fragmentRedirect.path().path(), is("/capture/a/b/%"));
    }

    @Test
    void decodedPathResolutionRetainsExistingRawPath() {
        ClientUri clientUri = ClientUri.create(URI.create("http://localhost/capture/a%2Fb/%25"));

        clientUri.resolvePath("next%/part");

        assertThat(clientUri.path().rawPath(), is("/capture/a%2Fb/%25/next%25/part"));
        assertThat(clientUri.path().path(), is("/capture/a/b/%/next%/part"));
    }

    @Test
    void skipEncodingContinuesToUseDecodedQuery() {
        ClientUri clientUri = ClientUri.create(URI.create("http://localhost/path?value=%2F%20value"))
                .skipUriEncoding(true);

        assertThat(clientUri.pathWithQueryAndFragment(), is("/path?value=/ value"));
    }

    @Test
    void emptyRelativeQueryDoesNotAppendSeparator() {
        ClientUri clientUri = ClientUri.create(URI.create("http://localhost/path?first=one"));

        clientUri.resolve(URI.create("?"));

        assertThat(clientUri.pathWithQueryAndFragment(), is("/path?first=one"));
    }
}
