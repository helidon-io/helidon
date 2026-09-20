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

import java.lang.reflect.InvocationHandler;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.buffers.LazyString;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.uri.UriPath;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.lang.reflect.Proxy.newProxyInstance;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientRequestBaseTest {

    @Test
    void redirectStateDoesNotUseRequestProperties() {
        TestRequest request = new TestRequest(Method.GET, "http://service.example");
        request.property("user-property", "value");
        request.redirectSecurityState(request.redirectSecurityState().forRedirect(false));

        assertThat(request.properties(), is(Map.of("user-property", "value")));
    }

    @Test
    void rejectsMultipleHostHeaderBeforeRequest() {
        TestRequest request = new TestRequest(Method.GET, "http://service.example");
        request.headers().add(HeaderValues.create(HeaderNames.HOST, "first.example", "second.example"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, request::request);

        assertThat(exception.getMessage(), containsString("Request Host header must be single-valued"));
        assertThat(request.endpointCount(), is(0));
    }

    @Test
    void rejectsMultipleHostHeaderBeforeSubmit() {
        TestRequest request = new TestRequest(Method.POST, "http://service.example");
        request.headers().add(HeaderValues.create(HeaderNames.HOST, "first.example", "second.example"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                         () -> request.submit(BufferData.EMPTY_BYTES));

        assertThat(exception.getMessage(), containsString("Request Host header must be single-valued"));
        assertThat(request.endpointCount(), is(0));
    }

    @Test
    void rejectsMultipleHostHeaderBeforeOutputStream() {
        TestRequest request = new TestRequest(Method.POST, "http://service.example");
        request.headers().add(HeaderValues.create(HeaderNames.HOST, "first.example", "second.example"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                         () -> request.outputStream(out -> { }));

        assertThat(exception.getMessage(), containsString("Request Host header must be single-valued"));
        assertThat(request.endpointCount(), is(0));
    }

    @Test
    void rejectsHostHeaderMadeMultipleByService() {
        HttpClientConfig config = HttpClientConfig.builder()
                .addService((chain, request) -> {
                    request.headers().add(HeaderValues.create(HeaderNames.HOST, "first.example", "second.example"));
                    return chain.proceed(request);
                })
                .build();
        TestRequest request = new TestRequest(config, Method.GET, "http://service.example");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, request::request);

        assertThat(exception.getMessage(), containsString("Request Host header must be single-valued"));
        assertThat(request.endpointCount(), is(0));
    }

    @Test
    void rejectsQueriesKnownToLackContentTypeBeforeServices() {
        AtomicInteger serviceCount = new AtomicInteger();
        HttpClientConfig config = HttpClientConfig.builder()
                .addService((chain, request) -> {
                    serviceCount.incrementAndGet();
                    return chain.proceed(request);
                })
                .build();
        TestRequest request = new TestRequest(config, Method.QUERY, "http://service.example");
        TestRequest byteRequest = new TestRequest(config, Method.QUERY, "http://service.example");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                           request::request);
        IllegalArgumentException byteException = assertThrows(IllegalArgumentException.class,
                                                               () -> byteRequest.submit(new byte[] {1}));

        assertThat(exception.getMessage(), containsString("Content-Type header is required"));
        assertThat(byteException.getMessage(), containsString("Content-Type header is required"));
        assertThat(serviceCount.get(), is(0));
        assertThat(request.endpointCount(), is(0));
        assertThat(byteRequest.endpointCount(), is(0));
    }

    @Test
    void rejectsQueryOutputStreamWithoutContentTypeBeforeServices() {
        AtomicInteger serviceCount = new AtomicInteger();
        AtomicInteger handlerCount = new AtomicInteger();
        HttpClientConfig config = HttpClientConfig.builder()
                .addService((chain, request) -> {
                    serviceCount.incrementAndGet();
                    return chain.proceed(request);
                })
                .build();
        TestRequest request = new TestRequest(config, Method.QUERY, "http://service.example");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                           () -> request.outputStream(_ ->
                                                                   handlerCount.incrementAndGet()));

        assertThat(exception.getMessage(), containsString("Content-Type header is required"));
        assertThat(handlerCount.get(), is(0));
        assertThat(serviceCount.get(), is(0));
        assertThat(request.endpointCount(), is(0));
    }

    @Test
    void acceptsQueryContentTypeForEntityApis() {
        TestRequest submitRequest = new TestRequest(Method.QUERY, "http://service.example");
        submitRequest.header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN).submit("query");
        assertThat(submitRequest.endpointCount(), is(1));

        TestRequest streamRequest = new TestRequest(Method.QUERY, "http://service.example");
        streamRequest.header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN).outputStream(_ -> { });
        assertThat(streamRequest.endpointCount(), is(1));
    }

    @Test
    void customMethodEntityDoesNotRequireContentType() {
        TestRequest request = new TestRequest(Method.create("CUSTOM"), "http://service.example");
        request.submit("entity");
        assertThat(request.endpointCount(), is(1));
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

    @Test
    void selectedProxyRouteIsScopedToOneRequestInvocation() {
        Proxy proxy = Proxy.builder().host("proxy.example").port(8181).build();
        TestRequest request = new TestRequest(HttpClientConfig.builder().proxy(proxy).build(),
                                              Method.GET,
                                              "http://service.example");
        ProxyRoute route = proxy.effectiveRoute("http", "service.example", 80, false);
        request.selectedProxyRoute(route);

        request.request();

        assertThat(request.routeSeenByEndpoint, is(route));
        assertThat(request.selectedProxyRoute(), is(Optional.empty()));
    }

    @Test
    void selectedProxyRouteIsClearedWhenInvocationFails() {
        Proxy proxy = Proxy.builder().host("proxy.example").port(8181).build();
        TestRequest request = new TestRequest(HttpClientConfig.builder().proxy(proxy).build(),
                                              Method.HEAD,
                                              "http://service.example");
        request.selectedProxyRoute(proxy.effectiveRoute("http", "service.example", 80, false));

        assertThrows(IllegalArgumentException.class, () -> request.submit("entity"));

        assertThat(request.selectedProxyRoute(), is(Optional.empty()));
    }

    @Test
    void changingProxyClearsSelectedProxyRoute() {
        Proxy first = Proxy.builder().host("first.example").port(8181).build();
        TestRequest request = new TestRequest(HttpClientConfig.builder().proxy(first).build(),
                                              Method.GET,
                                              "http://service.example");
        request.selectedProxyRoute(first.effectiveRoute("http", "service.example", 80, false));

        request.proxy(Proxy.builder().host("second.example").port(8282).build());

        assertThat(request.selectedProxyRoute(), is(Optional.empty()));
    }

    @Test
    void inheritedAddressRejectsNullAddressWithoutChangingState() {
        TestRequest request = new TestRequest(Method.GET, "http://service.example/path?key=value");
        URI uri = request.resolvedUri().toUri();
        ClientRequestOrigin origin = ClientRequestOrigin.create(request.resolvedUri());
        ClientRequestOrigin replacementOrigin = ClientRequestOrigin.create(ClientUri.create(URI.create("http://other.example")));
        SocketAddress address = UnixDomainSocketAddress.of("inherited.sock");
        request.inheritedAddress(address, origin);

        assertThrows(NullPointerException.class, () -> request.inheritedAddress(null, replacementOrigin));

        assertInheritedAddressState(request, address, origin, uri);
    }

    @ParameterizedTest
    @MethodSource("replacementAddresses")
    void inheritedAddressRejectsNullOriginWithoutChangingState(SocketAddress replacement) {
        TestRequest request = new TestRequest(Method.GET, "http://service.example/path?key=value");
        URI uri = request.resolvedUri().toUri();
        ClientRequestOrigin origin = ClientRequestOrigin.create(request.resolvedUri());
        SocketAddress address = UnixDomainSocketAddress.of("inherited.sock");
        request.inheritedAddress(address, origin);

        assertThrows(NullPointerException.class, () -> request.inheritedAddress(replacement, null));

        assertInheritedAddressState(request, address, origin, uri);
    }

    @Test
    void inheritedConnectionRejectsNullConnectionWithoutChangingState() {
        TestRequest request = new TestRequest(Method.GET, "http://service.example");
        ClientRequestOrigin origin = ClientRequestOrigin.create(request.resolvedUri());
        ClientRequestOrigin replacementOrigin = ClientRequestOrigin.create(ClientUri.create(URI.create("http://other.example")));
        ClientConnection connection = new TestConnection();
        request.inheritedConnection(connection, origin);

        assertThrows(NullPointerException.class, () -> request.inheritedConnection(null, replacementOrigin));

        assertThat(request.connection().orElseThrow(), sameInstance(connection));
        assertThat(request.inheritedConnectionOrigin().orElseThrow(), sameInstance(origin));
    }

    @Test
    void inheritedConnectionRejectsNullOriginWithoutChangingState() {
        TestRequest request = new TestRequest(Method.GET, "http://service.example");
        ClientRequestOrigin origin = ClientRequestOrigin.create(request.resolvedUri());
        ClientConnection connection = new TestConnection();
        request.inheritedConnection(connection, origin);

        assertThrows(NullPointerException.class, () -> request.inheritedConnection(new TestConnection(), null));

        assertThat(request.connection().orElseThrow(), sameInstance(connection));
        assertThat(request.inheritedConnectionOrigin().orElseThrow(), sameInstance(origin));
    }

    @Test
    void inheritedSelectedProxyRouteRejectsNullRouteWithoutChangingState() {
        TestRequest request = new TestRequest(Method.GET, "http://service.example");
        ClientRequestOrigin origin = ClientRequestOrigin.create(request.resolvedUri());
        ClientRequestOrigin replacementOrigin = ClientRequestOrigin.create(ClientUri.create(URI.create("http://other.example")));
        ProxyRoute route = ProxyRoute.direct(Proxy.noProxy(), "http", "service.example", 80, false);
        request.inheritedSelectedProxyRoute(route, origin);

        assertThrows(NullPointerException.class, () -> request.inheritedSelectedProxyRoute(null, replacementOrigin));

        assertInheritedProxyRouteState(request, route, origin);
    }

    @Test
    void inheritedSelectedProxyRouteRejectsNullOriginWithoutChangingState() {
        TestRequest request = new TestRequest(Method.GET, "http://service.example");
        ClientRequestOrigin origin = ClientRequestOrigin.create(request.resolvedUri());
        ProxyRoute route = ProxyRoute.direct(Proxy.noProxy(), "http", "service.example", 80, false);
        ProxyRoute replacement = ProxyRoute.direct(Proxy.noProxy(), "http", "other.example", 8080, false);
        request.inheritedSelectedProxyRoute(route, origin);

        assertThrows(NullPointerException.class, () -> request.inheritedSelectedProxyRoute(replacement, null));

        assertInheritedProxyRouteState(request, route, origin);
    }

    @Test
    void inheritedBindingsRetainNonNullValuesAndOrigins() {
        TestRequest request = new TestRequest(Method.GET, "http://service.example/path?key=value");
        URI uri = request.resolvedUri().toUri();
        ClientRequestOrigin origin = ClientRequestOrigin.create(request.resolvedUri());
        SocketAddress address = UnixDomainSocketAddress.of("inherited.sock");
        ClientConnection connection = new TestConnection();
        ProxyRoute route = ProxyRoute.direct(Proxy.noProxy(), "http", "service.example", 80, false);

        request.inheritedAddress(address, origin);
        request.inheritedConnection(connection, origin);
        request.inheritedSelectedProxyRoute(route, origin);

        assertInheritedAddressState(request, address, origin, uri);
        assertThat(request.connection().orElseThrow(), sameInstance(connection));
        assertThat(request.inheritedConnectionOrigin().orElseThrow(), sameInstance(origin));
        assertInheritedProxyRouteState(request, route, origin);
    }

    @Test
    void inheritedAddressDefaultRejectsNullArgumentsBeforeDelegation() {
        SocketAddress address = InetSocketAddress.createUnresolved("service.example", 80);
        ClientRequestOrigin origin = ClientRequestOrigin.create(ClientUri.create(URI.create("http://service.example")));

        assertAll(() -> assertRejectedBeforeDelegation(request -> request.inheritedAddress(null, origin)),
                  () -> assertRejectedBeforeDelegation(request -> request.inheritedAddress(address, null)));
    }

    @Test
    void inheritedConnectionDefaultRejectsNullArgumentsBeforeDelegation() {
        ClientConnection connection = new TestConnection();
        ClientRequestOrigin origin = ClientRequestOrigin.create(ClientUri.create(URI.create("http://service.example")));

        assertAll(() -> assertRejectedBeforeDelegation(request -> request.inheritedConnection(null, origin)),
                  () -> assertRejectedBeforeDelegation(request -> request.inheritedConnection(connection, null)));
    }

    @Test
    void inheritedSelectedProxyRouteDefaultRejectsNullArgumentsBeforeDelegation() {
        ProxyRoute route = ProxyRoute.direct(Proxy.noProxy(), "http", "service.example", 80, false);
        ClientRequestOrigin origin = ClientRequestOrigin.create(ClientUri.create(URI.create("http://service.example")));

        assertAll(() -> assertRejectedBeforeDelegation(request -> request.inheritedSelectedProxyRoute(null, origin)),
                  () -> assertRejectedBeforeDelegation(request -> request.inheritedSelectedProxyRoute(route, null)));
    }

    @Test
    void inheritedBindingDefaultsDelegateNonNullValues() {
        RecordingRequest recording = new RecordingRequest();
        SocketAddress address = InetSocketAddress.createUnresolved("service.example", 80);
        ClientConnection connection = new TestConnection();
        ProxyRoute route = ProxyRoute.direct(Proxy.noProxy(), "http", "service.example", 80, false);
        ClientRequestOrigin origin = ClientRequestOrigin.create(ClientUri.create(URI.create("http://service.example")));

        recording.request.inheritedAddress(address, origin);
        recording.request.inheritedConnection(connection, origin);
        recording.request.inheritedSelectedProxyRoute(route, origin);

        assertThat(recording.calls, contains(new SetterCall("address", address),
                                             new SetterCall("connection", connection),
                                             new SetterCall("selectedProxyRoute", route)));
    }

    @Test
    void finalizedHeadersAreDeepSnapshots() {
        HeaderName mutableName = HeaderNames.create("X-Mutable");
        byte[] mutableValue = "before".getBytes(StandardCharsets.UTF_8);
        HttpClientConfig config = HttpClientConfig.builder()
                .addService((chain, request) -> {
                    request.headers().set(HeaderValues.create(mutableName,
                                                              new LazyString(mutableValue, StandardCharsets.UTF_8)));
                    WebClientServiceResponse response = chain.proceed(request);
                    byte[] replacement = "after!".getBytes(StandardCharsets.UTF_8);
                    System.arraycopy(replacement, 0, mutableValue, 0, replacement.length);
                    return response;
                })
                .build();
        TestRequest request = new TestRequest(config, Method.GET, "http://service.example");

        request.request();

        ClientRequestHeaders firstSnapshot = request.capturedHeaders();
        assertThat(firstSnapshot.first(mutableName).orElseThrow(), is("before"));
        firstSnapshot.set(mutableName, "caller-change");
        assertThat(request.capturedHeaders().first(mutableName).orElseThrow(), is("before"));
    }

    @Test
    void uriRetargetDropsInheritedConnectionAndRouteButKeepsExplicitAddress() {
        HttpClientConfig config = HttpClientConfig.builder()
                .addService((chain, request) -> {
                    request.uri().host("other.example");
                    return chain.proceed(request);
                })
                .build();
        TestRequest request = new TestRequest(config, Method.GET, "http://service.example");
        ClientRequestOrigin sourceOrigin = ClientRequestOrigin.create(request.resolvedUri(), request.headers());
        ClientConnection inheritedConnection = new TestConnection();
        SocketAddress explicitAddress = UnixDomainSocketAddress.of("/tmp/helidon-explicit.sock");
        ProxyRoute inheritedRoute = ProxyRoute.direct(Proxy.noProxy(), "http", "service.example", 80, false);
        request.inheritedConnection(inheritedConnection, sourceOrigin);
        request.address(explicitAddress);
        request.inheritedSelectedProxyRoute(inheritedRoute, sourceOrigin);

        request.request();

        assertThat(request.endpointConnection(), is((ClientConnection) null));
        assertThat(request.endpointAddress(), sameInstance(explicitAddress));
        assertThat(request.endpointRoute(), is((ProxyRoute) null));
    }

    @Test
    void hostRetargetDropsInheritedAddressAndRouteButKeepsExplicitConnection() {
        HttpClientConfig config = HttpClientConfig.builder()
                .addService((chain, request) -> {
                    request.headers().set(HeaderNames.HOST, "other.example");
                    return chain.proceed(request);
                })
                .build();
        TestRequest request = new TestRequest(config, Method.GET, "http://service.example");
        ClientRequestOrigin sourceOrigin = ClientRequestOrigin.create(request.resolvedUri(), request.headers());
        ClientConnection explicitConnection = new TestConnection();
        SocketAddress inheritedAddress = UnixDomainSocketAddress.of("/tmp/helidon-inherited.sock");
        ProxyRoute inheritedRoute = ProxyRoute.direct(Proxy.noProxy(), "http", "service.example", 80, false);
        request.connection(explicitConnection);
        request.inheritedAddress(inheritedAddress, sourceOrigin);
        request.inheritedSelectedProxyRoute(inheritedRoute, sourceOrigin);

        request.request();

        assertThat(request.endpointConnection(), sameInstance(explicitConnection));
        assertThat(request.endpointAddress(), is((SocketAddress) null));
        assertThat(request.endpointRoute(), is((ProxyRoute) null));
    }

    @Test
    void matchingFinalOriginKeepsInheritedBindings() {
        TestRequest request = new TestRequest(Method.GET, "http://service.example");
        ClientRequestOrigin sourceOrigin = ClientRequestOrigin.create(request.resolvedUri(), request.headers());
        ClientConnection inheritedConnection = new TestConnection();
        SocketAddress inheritedAddress = UnixDomainSocketAddress.of("/tmp/helidon-inherited.sock");
        ProxyRoute inheritedRoute = ProxyRoute.direct(Proxy.noProxy(), "http", "service.example", 80, false);
        request.inheritedConnection(inheritedConnection, sourceOrigin);
        request.inheritedAddress(inheritedAddress, sourceOrigin);
        request.inheritedSelectedProxyRoute(inheritedRoute, sourceOrigin);

        request.request();

        assertThat(request.endpointConnection(), sameInstance(inheritedConnection));
        assertThat(request.endpointAddress(), sameInstance(inheritedAddress));
        assertThat(request.endpointRoute(), sameInstance(inheritedRoute));
    }

    @Test
    void hostSensitiveRedirectUsesPostStripOriginForBindingsAndCookies() {
        HttpClientConfig config = HttpClientConfig.builder()
                .addRedirectSensitiveHeader(HeaderNames.HOST)
                .build();
        WebClientCookieManager cookieManager = WebClientCookieManager.builder()
                .automaticStoreEnabled(true)
                .build();
        WritableHeaders<?> virtualCookieHeaders = WritableHeaders.create();
        virtualCookieHeaders.set(HeaderNames.SET_COOKIE, "virtual-cookie=stale; Path=/");
        cookieManager.response(ClientUri.create(URI.create("http://virtual.example/")),
                               ClientResponseHeaders.create(virtualCookieHeaders));
        WritableHeaders<?> targetCookieHeaders = WritableHeaders.create();
        targetCookieHeaders.set(HeaderNames.SET_COOKIE, "target-cookie=current; Path=/");
        cookieManager.response(ClientUri.create(URI.create("http://uri.example/")),
                               ClientResponseHeaders.create(targetCookieHeaders));

        TestRequest request = new TestRequest(config,
                                              cookieManager,
                                              Method.GET,
                                              "http://uri.example/target");
        ClientUri sourceUri = ClientUri.create(URI.create("http://source.example/source"));
        request.redirectSecurityState(RedirectSecurityState.initial()
                                              .finalized(sourceUri,
                                                         ClientRequestHeaders.create(WritableHeaders.create()))
                                              .forRedirect(false));
        request.headers().set(HeaderNames.HOST, "virtual.example");
        ClientRequestOrigin virtualOrigin = ClientRequestOrigin.create(request.resolvedUri(), request.headers());
        request.inheritedConnection(new TestConnection(), virtualOrigin);

        request.request();

        ClientRequestHeaders dispatchedHeaders = request.capturedHeaders();
        assertThat(dispatchedHeaders.contains(HeaderNames.HOST), is(false));
        String cookie = dispatchedHeaders.get(HeaderNames.COOKIE).get();
        assertThat(cookie, containsString("target-cookie=current"));
        assertThat(cookie.contains("virtual-cookie=stale"), is(false));
        assertThat(request.endpointConnection(), is((ClientConnection) null));
    }

    @Test
    void queryOnlyRetargetPreservesCookieOrder() {
        HttpClientConfig config = HttpClientConfig.builder()
                .addService((chain, request) -> {
                    request.uri().writeableQuery().set("q", "after");
                    request.headers().add(HeaderNames.COOKIE, "service=value");
                    return chain.proceed(request);
                })
                .build();
        WebClientCookieManager cookieManager = WebClientCookieManager.builder()
                .automaticStoreEnabled(true)
                .putDefaultCookie("default", "value")
                .build();
        WritableHeaders<?> storedCookieHeaders = WritableHeaders.create();
        storedCookieHeaders.set(HeaderNames.SET_COOKIE, "stored=value; Path=/");
        cookieManager.response(ClientUri.create(URI.create("http://service.example/")),
                               ClientResponseHeaders.create(storedCookieHeaders));
        TestRequest request = new TestRequest(config,
                                              cookieManager,
                                              Method.GET,
                                              "http://service.example/path?q=before");
        request.headers().set(HeaderNames.COOKIE, "explicit=value");

        request.request();

        assertThat(request.capturedHeaders().get(HeaderNames.COOKIE).allValues(),
                   is(List.of("explicit=value", "stored=value", "default=value", "service=value")));
    }

    private static Stream<SocketAddress> replacementAddresses() {
        return Stream.of(UnixDomainSocketAddress.of("replacement.sock"),
                         InetSocketAddress.createUnresolved("other.example", 8080));
    }

    private static void assertInheritedAddressState(TestRequest request,
                                                    SocketAddress address,
                                                    ClientRequestOrigin origin,
                                                    URI uri) {
        assertAll(() -> assertThat("transport address", request.address().orElseThrow(), sameInstance(address)),
                  () -> assertThat("address origin", request.inheritedAddressOrigin().orElseThrow(), sameInstance(origin)),
                  () -> assertThat("request URI", request.uri().toUri(), is(uri)),
                  () -> assertThat("resolved URI", request.resolvedUri().toUri(), is(uri)));
    }

    private static void assertInheritedProxyRouteState(TestRequest request, ProxyRoute route, ClientRequestOrigin origin) {
        assertAll(() -> assertThat("selected route", request.selectedProxyRoute().orElseThrow(), sameInstance(route)),
                  () -> assertThat("selected route origin",
                                   request.inheritedSelectedProxyRouteOrigin().orElseThrow(),
                                   sameInstance(origin)),
                  () -> assertThat("last route", request.lastSelectedProxyRoute().orElseThrow(), sameInstance(route)),
                  () -> assertThat("last route origin",
                                   request.inheritedLastSelectedProxyRouteOrigin().orElseThrow(),
                                   sameInstance(origin)));
    }

    private static void assertRejectedBeforeDelegation(Consumer<FullClientRequest<?>> invocation) {
        RecordingRequest recording = new RecordingRequest();
        assertAll(() -> assertThrows(NullPointerException.class, () -> invocation.accept(recording.request)),
                  () -> assertThat("setter calls for rejected arguments", recording.calls, empty()));
    }

    private record SetterCall(String method, Object argument) {
    }

    private static final class RecordingRequest {
        private final List<SetterCall> calls = new ArrayList<>();
        private final FullClientRequest<?> request = (FullClientRequest<?>) newProxyInstance(
                FullClientRequest.class.getClassLoader(),
                new Class<?>[] {FullClientRequest.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "inheritedAddress", "inheritedConnection", "inheritedSelectedProxyRoute" ->
                            InvocationHandler.invokeDefault(proxy, method, arguments);
                    case "address", "connection", "selectedProxyRoute" -> {
                        calls.add(new SetterCall(method.getName(), arguments[0]));
                        yield method.getReturnType() == void.class ? null : proxy;
                    }
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static final class TestRequest extends ClientRequestBase<TestRequest, HttpClientResponse> {
        private final AtomicInteger endpointCount = new AtomicInteger();
        private ProxyRoute routeSeenByEndpoint;
        private ClientConnection endpointConnection;
        private SocketAddress endpointAddress;
        private ProxyRoute endpointRoute;

        private TestRequest(Method method, String uri) {
            this(HttpClientConfig.builder().build(), method, uri);
        }

        private TestRequest(HttpClientConfig clientConfig, Method method, String uri) {
            this(clientConfig, WebClientCookieManager.builder().build(), method, uri);
        }

        private TestRequest(HttpClientConfig clientConfig,
                            WebClientCookieManager cookieManager,
                            Method method,
                            String uri) {
            super(clientConfig,
                  cookieManager,
                  "test",
                  method,
                  ClientUri.create(URI.create(uri)),
                  Map.of());
        }

        @Override
        protected HttpClientResponse doSubmit(Object entity) {
            invokeEndpoint();
            return null;
        }

        @Override
        protected HttpClientResponse doOutputStream(OutputStreamHandler outputStreamHandler) {
            invokeEndpoint();
            return null;
        }

        private void invokeEndpoint() {
            routeSeenByEndpoint = selectedProxyRoute().orElse(null);
            CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
            CompletableFuture<WebClientServiceResponse> whenComplete = new CompletableFuture<>();
            invokeServices(endpoint(), whenSent, whenComplete, resolvedUri());
        }

        private WebClientService.Chain endpoint() {
            return serviceRequest -> {
                endpointCount.incrementAndGet();
                endpointConnection = connection().orElse(null);
                endpointAddress = address().orElse(null);
                endpointRoute = selectedProxyRoute().orElse(null);
                return WebClientServiceResponse.builder()
                        .serviceRequest(serviceRequest)
                        .whenComplete(new CompletableFuture<>())
                        .connection(() -> { })
                        .status(Status.OK_200)
                        .headers(ClientResponseHeaders.create(WritableHeaders.create()))
                        .build();
            };
        }

        private int endpointCount() {
            return endpointCount.get();
        }

        private ClientRequestHeaders capturedHeaders() {
            return finalizedRequestHeaders();
        }

        private ClientConnection endpointConnection() {
            return endpointConnection;
        }

        private SocketAddress endpointAddress() {
            return endpointAddress;
        }

        private ProxyRoute endpointRoute() {
            return endpointRoute;
        }
    }

    private static final class TestConnection implements ClientConnection {
        @Override
        public DataReader reader() {
            return null;
        }

        @Override
        public DataWriter writer() {
            return null;
        }

        @Override
        public String channelId() {
            return "test";
        }

        @Override
        public HelidonSocket helidonSocket() {
            return null;
        }

        @Override
        public void readTimeout(Duration readTimeout) {
        }

        @Override
        public void closeResource() {
        }
    }

    private static final class FakeClientRequest extends ClientRequestBase<FakeClientRequest, HttpClientResponse> {
        private FakeClientRequest() {
            this(ClientUri.create());
        }

        private FakeClientRequest(ClientUri clientUri) {
            super(HttpClientConfig.builder().build(),
                  WebClientCookieManager.builder().build(),
                  "fake",
                  Method.GET,
                  clientUri,
                  Map.of());
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
