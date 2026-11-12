/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
import java.net.UnixDomainSocketAddress;
import java.util.Collections;
import java.util.List;

import io.helidon.common.uri.UriPath;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientRequestBaseTest {

    @ParameterizedTest
    @ValueSource(strings = {"item:revision", "/item:revision"})
    void preservesColonWhenAppendingRequestPath(String path) {
        TestRequest request = new TestRequest(Method.GET, "https://example.com/a%2fb?b=2&a=1&b=3&flag&&");
        request.path(path);

        assertThat(request.resolvedUri().pathWithQueryAndFragment(), is("/a%2fb/item:revision?b=2&a=1&b=3&flag&&"));
    }

    /**
     * Verify that query parameters are preserved when resolving URI templates (cf. issue #8566).
     * Make sure to test both absolute and relative URIs as they are handled differently when resolving.
     */
    @Test
    void resolvedUriTest() {
        ClientUri uri = new FakeClientRequest()
                .uri("https://www.example.com/")
                .queryParam("k", "v").resolvedUri();
        assertThat(uri.authority(), is("www.example.com:443"));
        assertThat(uri.host(), is("www.example.com"));
        assertThat(uri.path(), is(UriPath.root()));
        assertThat(uri.port(), is(443));
        assertThat(uri.scheme(), is("https"));
        assertThat(uri.query().get("k"), is("v"));

        uri = new FakeClientRequest()
                .uri("https://www.example.com/{path}")
                .pathParam("path", "p")
                .queryParam("k", "v").resolvedUri();
        assertThat(uri.authority(), is("www.example.com:443"));
        assertThat(uri.host(), is("www.example.com"));
        assertThat(uri.path(), is(UriPath.create("/p")));
        assertThat(uri.port(), is(443));
        assertThat(uri.scheme(), is("https"));
        assertThat(uri.query().get("k"), is("v"));

        uri = new FakeClientRequest()
                .uri("example/{path}")
                .pathParam("path", "p")
                .queryParam("k", "v").resolvedUri();
        assertThat(uri.authority(), is("localhost:80"));
        assertThat(uri.host(), is("localhost"));
        assertThat(uri.path(), is(UriPath.create("/example/p")));
        assertThat(uri.port(), is(80));
        assertThat(uri.scheme(), is("http"));
        assertThat(uri.query().get("k"), is("v"));
    }

    @Test
    void registeredNameInStringUri() {
        ClientUri uri = new FakeClientRequest()
                .uri("http://a_1.example.com:8080/foo")
                .resolvedUri();

        assertThat(uri.toString(), is("http://a_1.example.com:8080/foo"));
    }

    @Test
    void registeredNameInUriTemplate() {
        ClientUri uri = new FakeClientRequest()
                .uri("https://a_1.example.com:8443/{path}")
                .pathParam("path", "foo")
                .resolvedUri();

        assertThat(uri.toString(), is("https://a_1.example.com:8443/foo"));
    }

    @Test
    void unixDomainSocketUsesDefaultPort() {
        ClientUri uri = new FakeClientRequest()
                .address(UnixDomainSocketAddress.of("helidon.sock"))
                .resolvedUri();

        assertThat(uri.authority(), is("localhost:80"));
    }

    @Test
    void requestQueryOverridesTemplateQuery() {
        ClientUri uri = new FakeClientRequest()
                .uri("https://www.example.com/{path}?k=template")
                .pathParam("path", "p")
                .skipUriEncoding(true)
                .queryParam("k", "request")
                .resolvedUri();

        assertThat(uri.path(), is(UriPath.create("/p")));
        assertThat(uri.query().all("k"), is(List.of("request")));
    }

    @Test
    void requestQueryOverridesRelativeTemplateQuery() {
        ClientUri uri = new FakeClientRequest()
                .uri("example/{path}?k=template")
                .pathParam("path", "p")
                .skipUriEncoding(true)
                .queryParam("k", "request")
                .resolvedUri();

        assertThat(uri.path(), is(UriPath.create("/example/p")));
        assertThat(uri.query().all("k"), is(List.of("request")));
    }

    @Test
    void requestQueryOverridesRelativeTemplateQueryWhenEncodingIsConfiguredLater() {
        ClientUri uri = new FakeClientRequest()
                .uri("example/{path}?k=template")
                .pathParam("path", "p")
                .queryParam("k", "request")
                .skipUriEncoding(true)
                .resolvedUri();

        assertThat(uri.path(), is(UriPath.create("/example/p")));
        assertThat(uri.query().all("k"), is(List.of("request")));
    }

    @Test
    void multipleRequestQueryParamsArePreserved() {
        ClientUri uri = new FakeClientRequest()
                .uri("https://www.example.com/{path}")
                .pathParam("path", "p")
                .queryParam("one", "1")
                .queryParam("two", "2")
                .queryParam("three", "3")
                .queryParam("four", "4")
                .queryParam("five", "5")
                .queryParam("one", "replacement")
                .resolvedUri();

        assertThat(uri.query().all("one"), is(List.of("replacement")));
        assertThat(uri.query().all("two"), is(List.of("2")));
        assertThat(uri.query().all("three"), is(List.of("3")));
        assertThat(uri.query().all("four"), is(List.of("4")));
        assertThat(uri.query().all("five"), is(List.of("5")));
    }

    @Test
    void requestQueryCoexistsWithRelativeTemplateQuery() {
        ClientUri uri = new FakeClientRequest()
                .uri("example/{path}?template=value")
                .pathParam("path", "p")
                .skipUriEncoding(true)
                .queryParam("request", "value")
                .resolvedUri();

        assertThat(uri.path(), is(UriPath.create("/example/p")));
        assertThat(uri.query().all("template"), is(List.of("value")));
        assertThat(uri.query().all("request"), is(List.of("value")));
    }

    @Test
    void requestQueryOverridesEncodedTemplateQueryName() {
        ClientUri uri = new FakeClientRequest()
                .uri("https://www.example.com/{path}?a%20b=template")
                .pathParam("path", "p")
                .skipUriEncoding(true)
                .queryParam("a b", "request")
                .resolvedUri();

        assertThat(uri.query().all("a b"), is(List.of("request")));
        assertThat(uri.query().rawValue(), is("a%20b=request"));
    }

    @Test
    void requestQueryOverridesEquivalentEncodedTemplateQueryName() {
        ClientUri uri = new FakeClientRequest()
                .uri("https://www.example.com/{path}?%61=template")
                .pathParam("path", "p")
                .skipUriEncoding(true)
                .queryParam("a", "request")
                .resolvedUri();

        assertThat(uri.query().all("a"), is(List.of("request")));
        assertThat(uri.query().rawValue(), is("a=request"));
        assertThat(uri.pathWithQueryAndFragment(), is("/p?a=request"));
    }

    @Test
    void replacingTemplateDoesNotCarryEarlierQueryParam() {
        ClientUri uri = new FakeClientRequest()
                .uri("https://www.example.com/{path}")
                .queryParam("old", "value")
                .uri("https://www.example.com/{replacement}")
                .pathParam("replacement", "p")
                .queryParam("new", "value")
                .resolvedUri();

        assertThat(uri.query().contains("old"), is(false));
        assertThat(uri.query().all("new"), is(List.of("value")));
    }

    @Test
    void replacingRelativeTemplateRetainsEarlierQueryParams() {
        ClientUri baseUri = ClientUri.create(URI.create("https://www.example.com/base?k=base&unrelated=value"));
        ClientUri uri = new FakeClientRequest(baseUri)
                .uri("first/{path}")
                .pathParam("path", "p")
                .queryParam("k", "old")
                .queryParam("old", "value")
                .uri("second/{replacement}")
                .pathParam("replacement", "p")
                .queryParam("new", "value")
                .resolvedUri();

        assertThat(uri.query().all("k"), is(List.of("old")));
        assertThat(uri.query().all("unrelated"), is(List.of("value")));
        assertThat(uri.query().all("old"), is(List.of("value")));
        assertThat(uri.query().all("new"), is(List.of("value")));
    }

    @Test
    void replacingRelativeTemplateRetainsReplacementForEncodedBaseQueryName() {
        ClientUri baseUri = ClientUri.create(URI.create("https://www.example.com/base?%61=base"));
        ClientUri uri = new FakeClientRequest(baseUri)
                .uri("first/{path}")
                .pathParam("path", "p")
                .queryParam("a", "old")
                .uri("second/{replacement}")
                .pathParam("replacement", "p")
                .resolvedUri();

        assertThat(uri.query().all("a"), is(List.of("old")));
        assertThat(uri.query().rawValue(), is("a=old"));
    }

    @Test
    void inheritedQueryIsNotCopiedToAnotherAuthority() {
        ClientUri baseUri = ClientUri.create(URI.create("https://trusted.example/base?access_token=secret"));
        ClientUri uri = new FakeClientRequest(baseUri)
                .uri("https://{host}/path")
                .pathParam("host", "other.example")
                .queryParam("request", "value")
                .resolvedUri();

        assertThat(uri.authority(), is("other.example:443"));
        assertThat(uri.query().contains("access_token"), is(false));
        assertThat(uri.query().all("request"), is(List.of("value")));
    }

    @Test
    void directQueryMutationIsNotPromotedToTemplateRequestParam() {
        FakeClientRequest request = new FakeClientRequest()
                .uri("https://www.example.com/{path}")
                .pathParam("path", "p");
        request.uri().writeableQuery().set("direct", "value");

        assertThat(request.resolvedUri().query().contains("direct"), is(false));
    }

    @Test
    void queryParamConfiguredBeforeTemplateIsNotPromoted() {
        ClientUri uri = new FakeClientRequest()
                .queryParam("before", "value")
                .uri("https://www.example.com/{path}")
                .pathParam("path", "p")
                .resolvedUri();

        assertThat(uri.query().contains("before"), is(false));
    }

    @Test
    void queryParamConfiguredBeforeRelativeTemplateIsRetained() {
        ClientUri uri = new FakeClientRequest()
                .queryParam("before", "value")
                .uri("example/{path}")
                .pathParam("path", "p")
                .resolvedUri();

        assertThat(uri.query().all("before"), is(List.of("value")));
    }

    @Test
    void directQueryMutationRetainsRelativeTemplateBehavior() {
        FakeClientRequest request = new FakeClientRequest()
                .uri("example/{path}")
                .pathParam("path", "p");
        request.uri().writeableQuery().set("direct", "value");

        assertThat(request.resolvedUri().query().all("direct"), is(List.of("value")));
    }

    @Test
    void defaultEncodingKeepsTemplateQueryInPath() {
        ClientUri uri = new FakeClientRequest()
                .uri("https://www.example.com/{path}?template=value")
                .pathParam("path", "p")
                .queryParam("request", "value")
                .resolvedUri();

        assertThat(uri.pathWithQueryAndFragment(), is("/p%3Ftemplate=value?request=value"));
    }

    @Test
    void requestQueryCoexistsWithAbsoluteTemplateQueryForEitherEncodingOrder() {
        ClientUri encodingFirst = new FakeClientRequest()
                .uri("https://www.example.com/{path}?template=value")
                .pathParam("path", "p")
                .skipUriEncoding(true)
                .queryParam("request", "value")
                .resolvedUri();
        ClientUri encodingLast = new FakeClientRequest()
                .uri("https://www.example.com/{path}?template=value")
                .pathParam("path", "p")
                .queryParam("request", "value")
                .skipUriEncoding(true)
                .resolvedUri();

        assertThat(encodingFirst.query().all("template"), is(List.of("value")));
        assertThat(encodingFirst.query().all("request"), is(List.of("value")));
        assertThat(encodingLast.query().all("template"), is(List.of("value")));
        assertThat(encodingLast.query().all("request"), is(List.of("value")));
    }

    @Test
    void rejectedQueryParamDoesNotArmInheritedQuery() {
        ClientUri baseUri = ClientUri.create(URI.create("https://trusted.example/base?access_token=secret"));
        FakeClientRequest request = new FakeClientRequest(baseUri)
                .uri("https://{host}/path")
                .pathParam("host", "other.example");

        assertThrows(NullPointerException.class, () -> request.queryParam("access_token", (String) null));

        ClientUri uri = request.resolvedUri();
        assertThat(uri.authority(), is("other.example:443"));
        assertThat(uri.query().contains("access_token"), is(false));
    }

    @Test
    void rejectedQueryParamDoesNotDiscardEarlierTrackedName() {
        FakeClientRequest request = new FakeClientRequest()
                .uri("https://www.example.com/{path}")
                .pathParam("path", "p")
                .queryParam("accepted", "value");

        assertThrows(NullPointerException.class, () -> request.queryParam("rejected", (String) null));

        ClientUri uri = request.resolvedUri();
        assertThat(uri.query().all("accepted"), is(List.of("value")));
        assertThat(uri.query().contains("rejected"), is(false));
    }

    @Test
    void templateQueryResolutionIsRepeatableAndAliasSafe() {
        FakeClientRequest request = new FakeClientRequest()
                .uri("https://www.example.com/{path}")
                .pathParam("path", "p")
                .queryParam("request", "value");

        assertThat(request.resolvedUri().query().rawValue(), is("request=value"));
        assertThat(request.resolvedUri().query().rawValue(), is("request=value"));
        assertThat(request.resolveUri(request.uri()).query().rawValue(), is("request=value"));
        assertThat(request.resolveUri(request.uri()).query().rawValue(), is("request=value"));
    }

    @Test
    void mutableRequestQueryIsAuthoritative() {
        FakeClientRequest removed = new FakeClientRequest()
                .uri("https://www.example.com/{path}")
                .pathParam("path", "p")
                .queryParam("access_token", "secret");
        removed.uri().writeableQuery().remove("access_token");

        assertThat(removed.resolvedUri().query().contains("access_token"), is(false));

        FakeClientRequest replaced = new FakeClientRequest()
                .uri("https://www.example.com/{path}")
                .pathParam("path", "p")
                .queryParam("access_token", "secret");
        replaced.uri().writeableQuery().set("access_token", "replacement");

        assertThat(replaced.resolvedUri().query().all("access_token"), is(List.of("replacement")));
    }

    @Test
    void rawAliasDoesNotMasqueradeAsTrackedDecodedName() {
        FakeClientRequest request = new FakeClientRequest()
                .uri("https://www.example.com/{path}")
                .pathParam("path", "p")
                .queryParam("%61", "request");
        request.uri().writeableQuery().clear();
        request.uri().writeableQuery().fromQueryString("%61=raw");

        assertThat(request.resolvedUri().query().isEmpty(), is(true));
    }

    @Test
    void resolvedUriDoesNotChangeTemplateQueryTracking() {
        FakeClientRequest request = new FakeClientRequest()
                .uri("https://www.example.com/{path}?access_token=template")
                .pathParam("path", "p")
                .skipUriEncoding(true)
                .queryParam("access_token", "secret");
        request.uri().writeableQuery().remove("access_token");

        assertThat(request.resolvedUri().query().all("access_token"), is(List.of("template")));

        request.uri().writeableQuery().set("access_token", "replacement");

        assertThat(request.resolvedUri().query().all("access_token"), is(List.of("replacement")));
    }

    @Test
    void failedTemplateResolutionDoesNotChangeTrackedQueryParams() {
        FakeClientRequest request = new FakeClientRequest()
                .uri("https://www.example.com/{path}?access_token=template")
                .pathParam("path", "{invalid")
                .skipUriEncoding(true)
                .queryParam("access_token", "secret");
        request.uri().writeableQuery().remove("access_token");

        assertThrows(IllegalArgumentException.class, request::resolvedUri);

        request.uri().writeableQuery().set("access_token", "replacement");
        request.pathParam("path", "p");

        assertThat(request.resolvedUri().query().all("access_token"), is(List.of("replacement")));
    }

    @Test
    void relativeTemplateQueryResolutionIsRepeatable() {
        FakeClientRequest request = new FakeClientRequest()
                .uri("example/{path}?template=value")
                .pathParam("path", "p")
                .skipUriEncoding(true)
                .queryParam("request", "value");

        ClientUri first = request.resolvedUri();
        ClientUri second = request.resolvedUri();
        assertThat(second.query().rawValue(), is(first.query().rawValue()));
        assertThat(second.query().all("template"), is(List.of("value")));
        assertThat(second.query().all("request"), is(List.of("value")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/incoming?a=1",
            "/incoming?b=2&a=1&b=3",
            "/incoming?flag&&",
            "/objects/a%2fb/%7e/%25/%3f/%23?b=%2f&a=hello+world&b=%2F",
            "/objects;a=1/b;c=2?flag&",
            "/a//b/./c/../d?&&x=1&&",
            "/a+b?q=a+b&encoded=a%20b"
    })
    void requestTargetReplacesPathAndQueryWhileKeepingDestination(String target) {
        FakeClientRequest request = new FakeClientRequest(ClientUri.create(
                URI.create("https://backend.example:9443/base/path?base=secret#base-fragment")));

        request.requestTarget(prologue(target));

        ClientUri resolved = request.resolvedUri();
        assertThat(resolved.pathWithQueryAndFragment(), is(target));
        assertThat(resolved.scheme(), is("https"));
        assertThat(resolved.host(), is("backend.example"));
        assertThat(resolved.port(), is(9443));
        assertThat(resolved.fragment().hasValue(), is(false));
        assertThat(request.uri().pathWithQueryAndFragment(), is(target));
    }

    @Test
    void requestTargetDistinguishesMissingAndEmptyQueries() {
        FakeClientRequest request = new FakeClientRequest(ClientUri.create(
                URI.create("https://backend.example/base?old=value")));

        request.requestTarget(prologue("/without-query"));
        assertThat(request.resolvedUri().pathWithQueryAndFragment(), is("/without-query"));
        assertThat(request.resolvedUri().hasQuery(), is(false));
        assertThat(request.resolvedUri().query().isEmpty(), is(true));

        request.requestTarget(prologue("/empty-query?"));
        assertThat(request.resolvedUri().pathWithQueryAndFragment(), is("/empty-query?"));
        assertThat(request.resolvedUri().hasQuery(), is(true));
        assertThat(request.resolvedUri().query().rawValue(), is(""));

        request.requestTarget(prologue("/without-query-again"));
        assertThat(request.resolvedUri().pathWithQueryAndFragment(), is("/without-query-again"));
        assertThat(request.resolvedUri().hasQuery(), is(false));
    }

    @Test
    void requestTargetClearsEarlierTemplateAndTrackedParameters() {
        FakeClientRequest request = new FakeClientRequest(ClientUri.create(
                URI.create("https://backend.example:9443/base?base=secret")))
                .uri("/old/{unresolved}")
                .queryParam("tracked", "old");

        request.requestTarget(prologue("/replacement%2fpath?b=2&a=1&flag&&"));
        request.pathParam("unresolved", "later-value");

        assertThat(request.resolvedUri().pathWithQueryAndFragment(),
                   is("/replacement%2fpath?b=2&a=1&flag&&"));
        assertThat(request.resolvedUri().host(), is("backend.example"));
        assertThat(request.resolvedUri().query().contains("tracked"), is(false));
        assertThat(request.resolvedUri().query().contains("base"), is(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://incoming.example:8080/in%2fcoming?b=2&a=1&flag&&",
            "https://a_1.example:8443/in%2fcoming?b=2&a=1&flag&&",
            "HTTPS://incoming.example/in%2fcoming?b=2&a=1&flag&&"
    })
    void absoluteRequestTargetDoesNotReplaceDestinationAuthority(String target) {
        TestRequest request = new TestRequest(Method.POST, "https://backend.example:9443/base?old=value");

        request.requestTarget(prologue(target));

        ClientUri resolved = request.resolvedUri();
        assertThat(resolved.pathWithQueryAndFragment(), is("/in%2fcoming?b=2&a=1&flag&&"));
        assertThat(resolved.scheme(), is("https"));
        assertThat(resolved.host(), is("backend.example"));
        assertThat(resolved.port(), is(9443));
        assertThat(request.method(), is(Method.POST));
    }

    @ParameterizedTest
    @CsvSource({
            "http://incoming.example, /",
            "https://a_1.example:8443, /",
            "http://incoming.example?, /?",
            "https://a_1.example:8443?flag, /?flag"
    })
    void absoluteRequestTargetWithEmptyPathUsesRoot(String target, String expectedTarget) {
        FakeClientRequest request = new FakeClientRequest(ClientUri.create(
                URI.create("https://backend.example:9443/base?old=value")));

        request.requestTarget(prologue(target));

        assertThat(request.resolvedUri().pathWithQueryAndFragment(), is(expectedTarget));
        assertThat(request.resolvedUri().authority(), is("backend.example:9443"));
    }

    @Test
    void leadingDoubleSlashInOriginTargetRemainsPartOfPath() {
        FakeClientRequest request = new FakeClientRequest(ClientUri.create(
                URI.create("https://backend.example:9443/base?old=value")));

        request.requestTarget(prologue("//incoming.example/a%2fb?flag&&"));

        assertThat(request.resolvedUri().pathWithQueryAndFragment(), is("//incoming.example/a%2fb?flag&&"));
        assertThat(request.resolvedUri().authority(), is("backend.example:9443"));
    }

    @Test
    void requestTargetDiscardsIncomingAndCurrentFragments() {
        FakeClientRequest request = new FakeClientRequest(ClientUri.create(
                URI.create("https://backend.example/base?old=value#current-fragment")));

        request.requestTarget(prologue("/incoming%23path?q=%23#incoming-fragment"));

        assertThat(request.resolvedUri().pathWithQueryAndFragment(), is("/incoming%23path?q=%23"));
        assertThat(request.resolvedUri().fragment().hasValue(), is(false));
    }

    @Test
    void requestTargetKeepsMethodAndEncodingMode() {
        TestRequest request = new TestRequest(Method.POST, "https://backend.example/base?old=value");
        request.skipUriEncoding(true);

        request.requestTarget(prologue("/a%2fb?q=a%2Fb"));

        assertThat(request.method(), is(Method.POST));
        assertThat(request.skipUriEncoding(), is(true));
        assertThat(request.resolvedUri().pathWithQueryAndFragment(), is("/a/b?q=a/b"));

        request.skipUriEncoding(false);
        assertThat(request.resolvedUri().pathWithQueryAndFragment(), is("/a%2fb?q=a%2Fb"));
    }

    @Test
    void requestTargetDoesNotShareMutableQueryStateWithSourceOrResolvedCopies() {
        String target = "/a%2fb?%61=first&a=second&flag&&";
        HttpPrologue source = prologue(target);
        FakeClientRequest request = new FakeClientRequest(ClientUri.create(
                URI.create("https://backend.example/base?old=value")));
        request.requestTarget(source);
        ClientUri copy = request.resolvedUri();

        source.query().getAllRaw("%61").set(0, "source-only");
        source.query().all("a").set(0, "decoded-source-only");
        assertThat(request.uri().pathWithQueryAndFragment(), is(target));
        assertThat(copy.pathWithQueryAndFragment(), is(target));

        request.uri().writeableQuery().set("a", "request-only");
        assertThat(copy.pathWithQueryAndFragment(), is(target));
        copy.writeableQuery().set("a", "copy-only");
        assertThat(request.uri().query().all("a"), is(List.of("request-only")));
        assertThat(source.query().getAllRaw("%61"), is(List.of("source-only")));
        assertThat(source.query().all("a"), is(List.of("decoded-source-only", "second")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "*",
            "incoming.example:443",
            "relative/path",
            "?query-only",
            "",
            "ftp://incoming.example/path",
            "http:///path",
            "https://"
    })
    void requestTargetRejectsUnsupportedForms(String target) {
        FakeClientRequest request = new FakeClientRequest(ClientUri.create(URI.create("https://backend.example/base")));

        assertThrows(IllegalArgumentException.class, () -> request.requestTarget(prologue(target)));
    }

    @Test
    void requestTargetRejectsConnectAuthorityForm() {
        FakeClientRequest request = new FakeClientRequest(ClientUri.create(URI.create("https://backend.example/base")));
        HttpPrologue source = HttpPrologue.create("HTTP/1.1", "HTTP", "1.1", Method.CONNECT, "incoming.example:443", false);

        assertThrows(IllegalArgumentException.class, () -> request.requestTarget(source));
    }

    @Test
    void requestTargetRejectsNull() {
        FakeClientRequest request = new FakeClientRequest(ClientUri.create(URI.create("https://backend.example/base")));

        assertThrows(NullPointerException.class, () -> request.requestTarget(null));
    }

    @Test
    void defaultRequestTargetIsUnsupportedWithoutChangingRequest() {
        FakeClientRequest request = new FakeClientRequest(ClientUri.create(
                URI.create("https://backend.example:9443/base?base=secret#current-fragment")))
                .queryParam("tracked", "old")
                .skipUriEncoding(true);
        String originalTarget = request.resolvedUri().pathWithQueryAndFragment();

        assertThrows(UnsupportedOperationException.class,
                     () -> request.requestTargetUsingDefault(prologue("/new%2fpath?q=a%2Fb")));

        assertThat(request.resolvedUri().pathWithQueryAndFragment(), is(originalTarget));
        assertThat(request.resolvedUri().scheme(), is("https"));
        assertThat(request.resolvedUri().authority(), is("backend.example:9443"));
        assertThat(request.skipUriEncoding(), is(true));
        assertThat(request.method(), is(Method.GET));
    }

    @Test
    void defaultRequestTargetDoesNotResolveOrDiscardPendingTemplate() {
        FakeClientRequest request = new FakeClientRequest(ClientUri.create(
                URI.create("https://backend.example:9443/base?base=secret")))
                .uri("/old/{missing}");
        String originalTarget = request.uri().pathWithQueryAndFragment();

        assertThrows(UnsupportedOperationException.class,
                     () -> request.requestTargetUsingDefault(prologue("/incoming?x=1")));

        assertThat(request.uri().pathWithQueryAndFragment(), is(originalTarget));
        assertThrows(IllegalArgumentException.class, request::resolvedUri);
    }

    private static HttpPrologue prologue(String target) {
        return HttpPrologue.create("HTTP/1.1", "HTTP", "1.1", Method.PUT, target, false);
    }

    private interface DefaultRequestTarget extends ClientRequest<FakeClientRequest> {
        default FakeClientRequest requestTargetUsingDefault(HttpPrologue prologue) {
            return ClientRequest.super.requestTarget(prologue);
        }
    }

    private static final class TestRequest extends ClientRequestBase<TestRequest, HttpClientResponse> {
        private TestRequest(Method method, String uri) {
            super(WebClientConfig.create(), null, "test", method, ClientUri.create(URI.create(uri)), Collections.emptyMap());
        }

        @Override
        protected HttpClientResponse doSubmit(Object entity) {
            return null;
        }

        @Override
        protected HttpClientResponse doOutputStream(OutputStreamHandler outputStreamHandler) {
            return null;
        }
    }

    private static final class FakeClientRequest extends ClientRequestBase<FakeClientRequest, HttpClientResponse>
            implements DefaultRequestTarget {
        private FakeClientRequest() {
            this(ClientUri.create());
        }

        private FakeClientRequest(ClientUri clientUri) {
            super(WebClientConfig.create(), null, "fake", Method.GET, clientUri, Collections.emptyMap());
        }

        @Override
        protected HttpClientResponse doSubmit(Object entity) {
            return null;
        }

        @Override
        protected HttpClientResponse doOutputStream(OutputStreamHandler outputStreamHandler) {
            return null;
        }
    }
}
