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
import io.helidon.common.uri.UriQuery;
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
    @ParameterizedTest
    @ValueSource(strings = {
            "/proxy?a=1",
            "/proxy?b=2&a=1&b=3",
            "/proxy?flag",
            "/proxy?x=1&&y=2",
            "/proxy?",
            "/objects/a%2Fb/%7euser/%25/%3f/%23?b=%2f&a=hello+world&b=%2F",
            "/objects;a=1/b;c=2?flag&",
            "/a//b/./c/../d?&&x=1&&",
            "/a+b?q=a+b&encoded=a%20b"
    })
    void preservesImportedRequestTarget(String target) {
        URI original = URI.create("https://example.com:8443" + target);
        ClientUri uri = ClientUri.create(original);

        assertThat(uri.path().rawPath(), is(original.getRawPath()));
        assertThat(uri.query().rawValue(), is(original.getRawQuery()));
        assertThat(uri.pathWithQueryAndFragment(), is(target));
        assertThat(uri.toUri(), is(original));
        assertThat(uri.toString(), is(original.toString()));
        assertThat(uri.requestTargetQuery().rawValue(), is(original.getRawQuery()));
        assertThat(ClientUri.create(uri).pathWithQueryAndFragment(), is(target));
        assertThat(ClientUri.create().resolve(uri).pathWithQueryAndFragment(), is(target));
    }

    @Test
    void readingParametersDoesNotChangeRequestTarget() {
        String target = "/a%2fb;v=1?b=two+words&a=%2f&b=3&flag&&";
        ClientUri uri = ClientUri.create(URI.create("https://example.com" + target));

        assertThat(uri.path().path(), is("/a/b"));
        assertThat(uri.query().get("b"), is("two words"));
        assertThat(uri.query().all("b"), hasItems("two words", "3"));
        assertThat(uri.query().getAllRaw("a"), hasItems("%2f"));
        assertThat(uri.query().names(), hasItems("a", "b", "flag", ""));
        uri.host("other.example.com").port(9443);

        assertThat(uri.pathWithQueryAndFragment(), is(target));
    }

    @Test
    void preservesRawPathWhenQueryChanges() {
        ClientUri uri = ClientUri.create(URI.create("https://example.com/a%2fb/%7e?q=old"));
        uri.writeableQuery().set("q", "new value");

        assertThat(uri.pathWithQueryAndFragment(), is("/a%2fb/%7e?q=new%20value"));
    }

    @Test
    void preservesRawQueryWhenPathChanges() {
        ClientUri uri = ClientUri.create(URI.create("https://example.com/old?b=2&a=1&flag&&"));
        uri.path("/new path");

        assertThat(uri.pathWithQueryAndFragment(), is("/new%20path?b=2&a=1&flag&&"));
    }

    @Test
    void preservesRawComponentsWhenResolvingRelativeUri() {
        ClientUri uri = ClientUri.create(URI.create("https://example.com/a%2fb;v=1?b=2&a=1"));
        uri.resolve(URI.create("child%2fpart?flag&&"));

        assertThat(uri.pathWithQueryAndFragment(), is("/a%2fb;v=1/child%2fpart?b=2&a=1&flag&&"));
    }

    @ParameterizedTest
    @CsvSource({
            "/base, item:revision, /base/item:revision",
            "/base, /item:revision, /base/item:revision",
            "/base/, item:revision, /base/item:revision",
            "'', item:revision, /item:revision",
            "/, item:revision, /item:revision",
            "/a%2fb;v=1, item:revision, /a%2fb;v=1/item:revision",
            "/a%2fb;v=1, item:revision/child:two, /a%2fb;v=1/item:revision/child:two",
            "/a%2fb;v=1, child name:version%#?, /a%2fb;v=1/child%20name:version%25%23%3F",
            "/a%2fb;v=1, child name, /a%2fb;v=1/child%20name",
            "/base, a/b:c, /base/a/b:c",
            "/base, item;v=one:two, /base/item;v=one:two"
    })
    void preservesDecodedPathAppendCharacters(String basePath, String appendedPath, String expectedPath) {
        ClientUri uri = ClientUri.create(URI.create("https://example.com" + basePath + "?flag&&a=1&a=2"));
        uri.resolvePath(appendedPath);

        assertThat(uri.pathWithQueryAndFragment(), is(expectedPath + "?flag&&a=1&a=2"));
    }

    @ParameterizedTest
    @CsvSource({
            "child, /child",
            "/child, /child",
            "item:revision, /item:revision",
            "http://localhost:9876/private, /http://localhost:9876/private",
            "https://other.example/private?q=a#b, /https://other.example/private%3Fq=a%23b",
            "child name:version%#?, /child%20name:version%25%23%3F"
    })
    void keepsAppendsAgainstEmptyBaseInPath(String appendedPath, String expectedPath) {
        ClientUri uri = ClientUri.create(URI.create("https://example.com?flag&&a=1&a=2")).path("");
        uri.resolvePath(appendedPath);

        assertThat(uri.requestTargetPath().rawPath(), is(expectedPath));
        assertThat(uri.pathWithQueryAndFragment(), is(expectedPath + "?flag&&a=1&a=2"));
        assertThat(uri.toUri().getHost(), is("example.com"));
        assertThat(uri.toUri().getRawPath(), is(expectedPath));
    }

    @Test
    void keepsAppendAgainstEmptyUriInfoBaseInPath() {
        UriInfo baseUri = UriInfo.builder()
                .scheme("https")
                .host("example.com")
                .path(UriPath.create(""))
                .query(UriQuery.create("flag&&a=1&a=2"))
                .build();
        ClientUri uri = ClientUri.create(baseUri).resolvePath("http://localhost:9876/private");

        assertThat(uri.requestTargetPath().rawPath(), is("/http://localhost:9876/private"));
        assertThat(uri.pathWithQueryAndFragment(), is("/http://localhost:9876/private?flag&&a=1&a=2"));
        assertThat(uri.toUri().getHost(), is("example.com"));
    }

    @Test
    void emptyAppendPreservesEmptyBasePath() {
        ClientUri uri = ClientUri.create(URI.create("https://example.com?flag&&a=1&a=2")).path("");
        uri.resolvePath("");

        assertThat(uri.path().rawPath(), is(""));
        assertThat(uri.pathWithQueryAndFragment(), is("/?flag&&a=1&a=2"));
    }

    @Test
    void preservesUriInfoRawComponents() {
        UriInfo baseUri = UriInfo.builder()
                .host("example.com")
                .path(UriPath.create("/a%2fb;v=1"))
                .query(UriQuery.create("b=2&a=1&b=3&flag&&"))
                .build();

        assertThat(ClientUri.create(baseUri).pathWithQueryAndFragment(), is("/a%2fb;v=1?b=2&a=1&b=3&flag&&"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a&a=1&a", "a&a=&a", "&&x=&flag&", "%61&%61=1&a=2&%61", "a=b=c&flag"})
    void preservesUriInfoQueryWithMixedFlagsAndValues(String query) {
        UriInfo baseUri = UriInfo.builder()
                .host("example.com")
                .path(UriPath.create("/a%2fb"))
                .query(UriQuery.create(query))
                .build();

        assertThat(ClientUri.create(baseUri).pathWithQueryAndFragment(), is("/a%2fb?" + query));
    }

    @Test
    void importsRawQueryValuesAddedBeforeUriInfoCopy() {
        UriQuery source = UriQuery.create("a=old");
        source.getAllRaw("a").add("new");
        UriInfo baseUri = UriInfo.builder().host("example.com").query(source).build();

        ClientUri copy = ClientUri.create(baseUri);

        assertThat(copy.pathWithQueryAndFragment(), is("/?a=old&a=new"));
        assertThat(copy.query().all("a"), is(List.of("old")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "%61"})
    void importsRawQueryValuesEditedBeforeUriInfoCopy(String rawName) {
        UriQuery source = UriQuery.create(rawName + "=old");
        source.getAllRaw(rawName).set(0, "raw-new");
        source.all("a").set(0, "decoded-new");
        UriInfo baseUri = UriInfo.builder()
                .host("example.com")
                .path(UriPath.create("/a%2fb"))
                .query(source)
                .build();

        ClientUri copy = ClientUri.create(baseUri);

        assertThat(copy.pathWithQueryAndFragment(), is("/a%2fb?" + rawName + "=raw-new"));
        assertThat(copy.query().getAllRaw(rawName), is(List.of("raw-new")));
        assertThat(copy.query().all("a"), is(List.of("decoded-new")));

        source.getAllRaw(rawName).set(0, "source-only");
        source.all("a").set(0, "decoded-source-only");
        assertThat(copy.pathWithQueryAndFragment(), is("/a%2fb?" + rawName + "=raw-new"));
        assertThat(copy.query().all("a"), is(List.of("decoded-new")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "%61"})
    void doesNotRestoreRawQueryValuesClearedBeforeUriInfoCopy(String rawName) {
        UriQuery source = UriQuery.create(rawName + "=old");
        source.getAllRaw(rawName).clear();
        UriInfo baseUri = UriInfo.builder()
                .host("example.com")
                .path(UriPath.create("/a%2fb"))
                .query(source)
                .build();

        ClientUri copy = ClientUri.create(baseUri);

        assertThat(copy.pathWithQueryAndFragment(), is("/a%2fb"));
        assertThat(copy.query().getAllRaw(rawName), is(List.of()));
        assertThat(copy.query().all("a"), is(List.of("old")));

        source.getAllRaw(rawName).add("source-only");
        assertThat(copy.pathWithQueryAndFragment(), is("/a%2fb"));
        assertThat(copy.query().getAllRaw(rawName), is(List.of()));
    }

    @Test
    void replacingUriDoesNotRetainPreviousTarget() {
        ClientUri uri = ClientUri.create(URI.create("https://example.com/a%2fb?b=2&a=1&flag"));
        uri.resolve(URI.create("https://other.example.com/new%2fpath?z=1&&flag"));

        assertThat(uri.pathWithQueryAndFragment(), is("/new%2fpath?z=1&&flag"));
        uri.resolve(URI.create("https://other.example.com/clean"));
        assertThat(uri.pathWithQueryAndFragment(), is("/clean"));
    }

    @Test
    void encodesUnicodeWithoutChangingExistingEscapes() {
        ClientUri uri = ClientUri.create(URI.create("https://example.com/café%2fmenu?q=café&raw=%2f"));

        assertThat(uri.pathWithQueryAndFragment(), is("/caf%C3%A9%2fmenu?q=caf%C3%A9&raw=%2f"));
    }

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

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "/%7e | /~",
            "/a%2fb | /a/b",
            "/a%3fb%23c | /a?b#c",
            "/a%25b | /a%b",
            "/a;b=1/c;d=2 | /a/c",
            "'' | /",
            "/ | /"
    })
    void testRequestTargetPathUsesConfiguredEncoding(String rawPath, String skippedPath) {
        ClientUri helper = ClientUri.create(URI.create("http://localhost" + rawPath + "?q=1"));
        String originalPath = helper.path().rawPath();
        String encodedPath = rawPath.isEmpty() ? "/" : rawPath;

        assertThat(helper.requestTargetPath().rawPath(), is(encodedPath));
        assertThat(helper.pathWithQueryAndFragment(), is(encodedPath + "?q=1"));

        helper.skipUriEncoding(true);

        assertThat(helper.requestTargetPath().rawPath(), is(skippedPath));
        assertThat(helper.pathWithQueryAndFragment(), is(skippedPath + "?q=1"));
        assertThat(helper.path().rawPath(), is(originalPath));
        assertThat(ClientUri.create(helper).requestTargetPath().rawPath(), is(skippedPath));

        helper.skipUriEncoding(false);

        assertThat(helper.requestTargetPath().rawPath(), is(encodedPath));
        assertThat(helper.pathWithQueryAndFragment(), is(encodedPath + "?q=1"));
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
    void testClearingNonEmptyQueryRemovesDelimiter() {
        ClientUri helper = ClientUri.create(URI.create("http://localhost:8080/loom/quick?p1=v1"));

        helper.writeableQuery().clear();

        assertThat(helper.hasQuery(), is(false));
        assertThat(helper.pathWithQueryAndFragment(), is("/loom/quick"));
        assertThat(helper.toUri(), is(URI.create("http://localhost:8080/loom/quick")));
    }

    @Test
    void testQueryMultipleValues() {
        UriQueryWriteable query = UriQueryWriteable.create();
        query.fromQueryString("p1=v1&p1=v2");
        assertThat(query.all("p1"), hasItems("v1", "v2"));
    }

    @ParameterizedTest
    @CsvSource({
            "http, 8080, quick",
            "http, 8080, /quick",
            "https, 8443, quick",
            "https, 8443, /quick"
    })
    void testResolvePath(String scheme, int port, String path) {
        ClientUri helper = ClientUri.create(URI.create(scheme + "://localhost:" + port + "/loom"));
        helper.resolve(URI.create(path));

        assertThat(helper.authority(), is("localhost:" + port));
        assertThat(helper.host(), is("localhost"));
        assertThat(helper.path(), is(UriPath.create("/loom/quick")));
        assertThat(helper.port(), is(port));
        assertThat(helper.scheme(), is(scheme));
        assertThat(helper.toUri(), is(URI.create(scheme + "://localhost:" + port + "/loom/quick")));
    }

    @ParameterizedTest
    @CsvSource({
            "http, 8080, //other.example/path, other.example, 80",
            "https, 8443, //other.example/path, other.example, 443",
            "http, 8080, //other.example:9090/path, other.example, 9090",
            "https, 8443, //other.example:9090/path, other.example, 9090",
            "http, 8080, //other.example:0/path, other.example, 0",
            "http, 8080, //base.example/path, base.example, 80",
            "https, 8443, //base.example/path, base.example, 443"
    })
    void testResolveNetworkPathAuthority(String scheme, int basePort, String reference, String host, int port) {
        ClientUri helper = ClientUri.create(URI.create(scheme + "://base.example:" + basePort + "/base"));
        helper.resolve(URI.create(reference));

        assertThat(helper.authority(), is(host + ":" + port));
        assertThat(helper.host(), is(host));
        assertThat(helper.path(), is(UriPath.create("/base/path")));
        assertThat(helper.port(), is(port));
        assertThat(helper.scheme(), is(scheme));
        assertThat(helper.toUri(), is(URI.create(scheme + "://" + host + ":" + port + "/base/path")));
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
