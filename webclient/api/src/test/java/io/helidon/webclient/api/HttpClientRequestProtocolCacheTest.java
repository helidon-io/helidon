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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.HttpCookie;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.common.GenericType;
import io.helidon.common.LruCache;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.LazyString;
import io.helidon.common.context.Context;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsMaterial;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.InstanceWriter;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.MediaSupport;
import io.helidon.http.media.ReadableEntity;
import io.helidon.http.media.ReadableEntityBase;
import io.helidon.webclient.spi.HttpClientSpi;
import io.helidon.webclient.spi.Protocol;
import io.helidon.webclient.spi.ProtocolConfig;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpClientRequestProtocolCacheTest {
    @Test
    void invalidatesCachedProtocolWhenSupportIsLost() {
        TestContext context = TestContext.create();
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);

        context.request().request();
        assertThat("initial protocol", context.selected().get(), is("dynamic"));

        context.dynamic().support(HttpClientSpi.SupportLevel.NOT_SUPPORTED);
        context.request().request();

        assertThat("protocol after cached support was lost", context.selected().get(), is("http/1.1"));
        assertThat("dynamic protocol support checks", context.dynamic().supportsInvocations(), is(3));
        assertThat("fallback protocol support checks", context.fallback().supportsInvocations(), is(1));
    }

    @Test
    void promotesHigherPriorityProtocolFromCachedTcpFallback() {
        TestContext context = TestContext.create();
        context.dynamic().support(HttpClientSpi.SupportLevel.NOT_SUPPORTED);

        context.request().request();
        assertThat("initial protocol", context.selected().get(), is("http/1.1"));

        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.request().request();

        assertThat("protocol after higher-priority support appeared", context.selected().get(), is("dynamic"));
        assertThat("higher-priority protocol support checks", context.dynamic().supportsInvocations(), is(2));
        assertThat("fallback protocol support checks", context.fallback().supportsInvocations(), is(1));
    }

    @Test
    void reusesRevalidatedCompatibleProtocol() {
        TestContext context = TestContext.create();
        context.dynamic().support(HttpClientSpi.SupportLevel.NOT_SUPPORTED);

        context.request().request();
        context.selected().set(null);
        context.dynamic().support(HttpClientSpi.SupportLevel.COMPATIBLE);
        context.request().request();

        assertThat("revalidated cached protocol", context.selected().get(), is("http/1.1"));
        assertThat("higher-priority compatible protocol support checks",
                   context.dynamic().supportsInvocations(),
                   is(2));
        assertThat("cached compatible protocol support checks", context.fallback().supportsInvocations(), is(2));
    }

    @Test
    void explicitProtocolSelectionBypassesAutomaticRevalidation() {
        TestContext context = TestContext.create();

        context.request().protocolId("dynamic").request();

        assertThat("explicitly selected protocol", context.selected().get(), is("dynamic"));
        assertThat("explicit protocol support checks", context.dynamic().supportsInvocations(), is(0));
        assertThat("fallback protocol support checks", context.fallback().supportsInvocations(), is(0));
        assertThat(context.dynamic().requestedProtocolId(), is("dynamic"));
    }

    @Test
    void shouldSnapshotTlsGenerationBeforeProtocolSelection() {
        TestContext context = TestContext.create();
        Tls tls = Tls.builder().trustAll(true).build();
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().reloadTlsOnSelection();

        context.request().tls(tls).request();

        assertThat(context.dynamic().tlsGeneration(), is(0L));
        assertThat(tls.generation(), is(1L));
    }

    @Test
    void shouldPropagateFinalTlsGenerationAfterServiceHandoff() {
        Tls tls = Tls.builder().trustAll(true).build();
        TestContext context = TestContext.createWithHandoffService((chain, request) -> {
            tls.reload(TlsMaterial.builder().trustAll(true).build());
            return chain.proceed(request);
        });
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        HttpClientRequest request = context.request()
                .tls(tls)
                .uri("https://example.test");

        try (HttpClientResponse ignored = request.request()) {
        }

        assertThat(context.dynamic().tlsGeneration(), is(1L));
        assertThat(request.tlsGeneration(), is(1L));
    }

    @Test
    void shouldDropUriOriginMismatchedInheritedTransportAndKeepExplicitAddress() {
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://example.test")
                .addService((chain, request) -> {
                    request.uri().host("other.test");
                    return chain.proceed(request);
                })
                .buildPrototype();
        TestContext context = TestContext.create(config, true);
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        HttpClientRequest request = context.request();
        ClientRequestOrigin sourceOrigin = ClientRequestOrigin.create(request.resolvedUri(), request.headers());
        ClientConnection inheritedConnection = explicitConnection(false, null);
        SocketAddress explicitAddress = UnixDomainSocketAddress.of("/tmp/helidon-explicit.sock");
        ProxyRoute inheritedRoute = ProxyRoute.direct(io.helidon.webclient.api.Proxy.noProxy(),
                                                      "http",
                                                      "example.test",
                                                      80,
                                                      false);
        request.inheritedConnection(inheritedConnection, sourceOrigin);
        request.address(explicitAddress);
        request.inheritedSelectedProxyRoute(inheritedRoute, sourceOrigin);

        try (HttpClientResponse ignored = request.request()) {
        }

        assertThat(context.dynamic().capturedConnection(), is((ClientConnection) null));
        assertThat(context.dynamic().capturedAddress(), sameInstance(explicitAddress));
        assertThat(context.dynamic().capturedSelectedProxyRoute(), is((ProxyRoute) null));
    }

    @Test
    void shouldDropHostOriginMismatchedInheritedTransportAndKeepExplicitConnection() {
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://example.test")
                .addService((chain, request) -> {
                    request.headers().set(HeaderNames.HOST, "other.test");
                    return chain.proceed(request);
                })
                .buildPrototype();
        TestContext context = TestContext.create(config, true);
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        HttpClientRequest request = context.request();
        ClientRequestOrigin sourceOrigin = ClientRequestOrigin.create(request.resolvedUri(), request.headers());
        ClientConnection explicitConnection = explicitConnection(false, null);
        SocketAddress inheritedAddress = UnixDomainSocketAddress.of("/tmp/helidon-inherited.sock");
        ProxyRoute inheritedRoute = ProxyRoute.direct(io.helidon.webclient.api.Proxy.noProxy(),
                                                      "http",
                                                      "example.test",
                                                      80,
                                                      false);
        request.connection(explicitConnection);
        request.inheritedAddress(inheritedAddress, sourceOrigin);
        request.inheritedSelectedProxyRoute(inheritedRoute, sourceOrigin);

        try (HttpClientResponse ignored = request.request()) {
        }

        assertThat(context.fallback().capturedConnection(), sameInstance(explicitConnection));
        assertThat(context.fallback().capturedAddress(), is((SocketAddress) null));
        assertThat(context.fallback().capturedSelectedProxyRoute(), is((ProxyRoute) null));
    }

    @Test
    void shouldUseHttp1ForUnnegotiatedExplicitTcpConnection() {
        TestContext context = TestContext.create();

        context.request()
                .connection(explicitConnection(false, null))
                .request();

        assertThat(context.selected().get(), is("http/1.1"));
    }

    @Test
    void shouldDispatchNegotiatedExplicitTcpConnectionToMatchingProtocol() {
        TestContext context = TestContext.create();

        context.request()
                .connection(explicitConnection(true, "dynamic"))
                .request();

        assertThat(context.selected().get(), is("dynamic"));
    }

    @Test
    void shouldNotRequireServiceHandoffFromExistingProtocolProvider() {
        TestContext context = TestContext.createWithService();
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);

        context.request().request();

        assertThat(context.selected().get(), is("dynamic"));
    }

    @Test
    void shouldNotReplayOpaqueEntityThroughNonHandoffProvider() {
        TestContext context = TestContext.createWithService();
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().redirectOnce(Status.TEMPORARY_REDIRECT_307);
        Object entity = new Object();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                      () -> context.request(Method.POST)
                                                              .followRedirects(true)
                                                              .submit(entity));

        assertThat(failure.getMessage(), is("Cannot replay a one-shot request body after redirect status 307."));
        assertThat(context.dynamic().transportInvocations(), is(1));
        assertThat(context.dynamic().submittedEntities().size(), is(1));
        assertThat(context.dynamic().submittedEntities().get(0), sameInstance(entity));
    }

    @Test
    void shouldDropOpaqueEntityForBodylessRedirectThroughNonHandoffProvider() {
        TestContext context = TestContext.createWithService();
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().redirectOnce(Status.FOUND_302);
        Object entity = new Object();

        try (HttpClientResponse response = context.request(Method.POST)
                .followRedirects(true)
                .submit(entity)) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(context.dynamic().transportInvocations(), is(2));
        assertThat(context.dynamic().submittedEntities().size(), is(2));
        assertThat(context.dynamic().submittedEntities().get(0), sameInstance(entity));
        assertThat(((byte[]) context.dynamic().submittedEntities().get(1)).length, is(0));
    }

    @Test
    void shouldReplayWriterHeadersBeforeTargetServicesAndRedirectSanitization() {
        HeaderName writerHeader = HeaderNames.create("X-Writer");
        HeaderName serviceDefaultHeader = HeaderNames.create("X-Service-Default");
        byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
        AtomicInteger serviceInvocations = new AtomicInteger();
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://example.test")
                .followCrossOriginEntityRedirects(true)
                .mediaContext(redirectWriterMediaContext(writerHeader, serviceDefaultHeader, payload))
                .addService((chain, request) -> {
                    request.headers().setIfAbsent(HeaderValues.create(serviceDefaultHeader, "service-default"));
                    if (serviceInvocations.incrementAndGet() == 2) {
                        request.headers().set(writerHeader, "target-service");
                        request.headers().set(HeaderNames.AUTHORIZATION, "target-service-secret");
                    }
                    return chain.proceed(request);
                })
                .buildPrototype();
        TestContext context = TestContext.create(config, true);
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().redirectOnce(Status.TEMPORARY_REDIRECT_307);
        context.dynamic().redirectLocation.set("http://other.test/target");

        try (HttpClientResponse response = context.request(Method.POST)
                .followRedirects(true)
                .submit(new Object())) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(serviceInvocations.get(), is(2));
        assertThat(context.dynamic().submittedHeaders().size(), is(2));
        ClientRequestHeaders sourceHeaders = context.dynamic().submittedHeaders().get(0);
        ClientRequestHeaders targetHeaders = context.dynamic().submittedHeaders().get(1);
        assertThat(sourceHeaders.first(writerHeader).orElseThrow(), is("writer"));
        assertThat(sourceHeaders.first(serviceDefaultHeader).orElseThrow(), is("writer-final"));
        assertThat(sourceHeaders.first(HeaderNames.AUTHORIZATION).orElseThrow(), is("writer-secret"));
        assertThat(sourceHeaders.first(HeaderNames.CONTENT_TYPE).orElseThrow(), is("text/plain"));
        assertThat(targetHeaders.first(writerHeader).orElseThrow(), is("target-service"));
        assertThat(targetHeaders.first(serviceDefaultHeader).orElseThrow(), is("writer-final"));
        assertThat(targetHeaders.contains(HeaderNames.AUTHORIZATION), is(false));
        assertThat(targetHeaders.first(HeaderNames.CONTENT_TYPE).orElseThrow(), is("text/plain"));
    }

    @Test
    void shouldSanitizeWriterHeadersAfterShortCircuitedCrossOriginRedirect() {
        HeaderName writerHeader = HeaderNames.create("X-Writer");
        HeaderName serviceDefaultHeader = HeaderNames.create("X-Service-Default");
        byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
        AtomicInteger serviceInvocations = new AtomicInteger();
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://example.test")
                .followCrossOriginEntityRedirects(true)
                .mediaContext(redirectWriterMediaContext(writerHeader, serviceDefaultHeader, payload))
                .addService((chain, request) -> {
                    request.headers().setIfAbsent(HeaderValues.create(serviceDefaultHeader, "service-default"));
                    if (serviceInvocations.getAndIncrement() == 0) {
                        WritableHeaders<?> responseHeaders = WritableHeaders.create();
                        responseHeaders.set(HeaderNames.LOCATION, "http://other.test/target");
                        return WebClientServiceResponse.builder()
                                .serviceRequest(request)
                                .whenComplete(new CompletableFuture<>())
                                .connection(() -> {
                                })
                                .status(Status.TEMPORARY_REDIRECT_307)
                                .headers(ClientResponseHeaders.create(responseHeaders))
                                .build();
                    }
                    return chain.proceed(request);
                })
                .buildPrototype();
        TestContext context = TestContext.create(config, true);
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);

        try (HttpClientResponse response = context.request(Method.POST)
                .followRedirects(true)
                .submit(new Object())) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(serviceInvocations.get(), is(2));
        assertThat(context.dynamic().submittedHeaders().size(), is(1));
        ClientRequestHeaders targetHeaders = context.dynamic().submittedHeaders().getFirst();
        assertThat(targetHeaders.first(writerHeader).orElseThrow(), is("writer"));
        assertThat(targetHeaders.first(serviceDefaultHeader).orElseThrow(), is("writer-final"));
        assertThat(targetHeaders.contains(HeaderNames.AUTHORIZATION), is(false));
        assertThat(targetHeaders.first(HeaderNames.CONTENT_TYPE).orElseThrow(), is("text/plain"));
    }

    @Test
    void shouldUseWriterFinalHostForRedirectOrigin() {
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://example.test")
                .mediaContext(writerMediaContext("payload".getBytes(StandardCharsets.UTF_8),
                                                 headers -> headers.set(HeaderNames.HOST, "writer.test")))
                .addService((chain, request) -> chain.proceed(request))
                .buildPrototype();
        TestContext context = TestContext.create(config, true);
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().redirectOnce(Status.SEE_OTHER_303);

        try (HttpClientResponse response = context.request(Method.POST)
                .header(HeaderNames.AUTHORIZATION, "secret")
                .followRedirects(true)
                .submit(new Object())) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(context.dynamic().submittedHeaders().size(), is(2));
        ClientRequestHeaders sourceHeaders = context.dynamic().submittedHeaders().get(0);
        ClientRequestHeaders targetHeaders = context.dynamic().submittedHeaders().get(1);
        assertThat(sourceHeaders.first(HeaderNames.HOST).orElseThrow(), is("writer.test"));
        assertThat(sourceHeaders.first(HeaderNames.AUTHORIZATION).orElseThrow(), is("secret"));
        assertThat(targetHeaders.contains(HeaderNames.AUTHORIZATION), is(false));
    }

    @Test
    void shouldRetargetCookiesAndStoreResponseForWriterFinalHost() {
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://example.test")
                .mediaContext(writerMediaContext("payload".getBytes(StandardCharsets.UTF_8),
                                                 headers -> headers.set(HeaderNames.HOST, "writer.test")))
                .addService((chain, request) -> chain.proceed(request))
                .buildPrototype();
        TestContext context = TestContext.create(config, true);
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        HttpCookie exampleCookie = new HttpCookie("example", "cookie");
        exampleCookie.setPath("/");
        context.webClient().cookieManager().getCookieStore().add(URI.create("http://example.test/"), exampleCookie);
        HttpCookie writerCookie = new HttpCookie("writer", "cookie");
        writerCookie.setPath("/");
        context.webClient().cookieManager().getCookieStore().add(URI.create("http://writer.test/"), writerCookie);
        context.dynamic().responseSetCookie("response=writer; Path=/");

        try (HttpClientResponse response = context.request(Method.POST).submit(new Object())) {
            assertThat(response.status(), is(Status.OK_200));
        }

        ClientRequestHeaders sourceHeaders = context.dynamic().submittedHeaders().getFirst();
        String submittedCookies = String.join("; ", sourceHeaders.get(HeaderNames.COOKIE).allValues());
        assertThat("Submitted cookies: " + submittedCookies,
                   submittedCookies.contains("writer="),
                   is(true));
        assertThat("Submitted cookies: " + submittedCookies,
                   submittedCookies.contains("example="),
                   is(false));
        assertThat(context.webClient().cookieManager().getCookieStore().get(URI.create("http://writer.test/"))
                           .stream()
                           .anyMatch(cookie -> cookie.getName().equals("response")),
                   is(true));
        assertThat(context.webClient().cookieManager().getCookieStore().get(URI.create("http://example.test/"))
                           .stream()
                           .anyMatch(cookie -> cookie.getName().equals("response")),
                   is(false));
    }

    @Test
    void shouldNotReplayPathScopedManagerCookieAsWriterState() {
        List<URI> serviceUris = new ArrayList<>();
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://example.test")
                .mediaContext(writerMediaContext("payload".getBytes(StandardCharsets.UTF_8),
                                                 headers -> headers.add(HeaderValues.create(HeaderNames.COOKIE,
                                                                                           "writer=cookie"))))
                .addService((chain, request) -> {
                    serviceUris.add(request.uri().toUri());
                    return chain.proceed(request);
                })
                .buildPrototype();
        TestContext context = TestContext.create(config, true);
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().redirectOnce(Status.TEMPORARY_REDIRECT_307);
        context.dynamic().redirectLocation.set("/other");
        HttpCookie sourceCookie = new HttpCookie("source", "cookie");
        sourceCookie.setPath("/source");
        context.webClient().cookieManager().getCookieStore().add(URI.create("http://localhost:80/source"), sourceCookie);

        try (HttpClientResponse response = context.request(Method.POST)
                .path("/source")
                .followRedirects(true)
                .submit(new Object())) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(context.dynamic().submittedHeaders().size(), is(2));
        assertThat(serviceUris, is(List.of(URI.create("http://localhost:80/source"),
                                           URI.create("http://localhost:80/other"))));
        String sourceCookies = String.join("; ",
                                           context.dynamic().submittedHeaders().get(0)
                                                   .get(HeaderNames.COOKIE)
                                                   .allValues());
        String targetCookies = String.join("; ",
                                           context.dynamic().submittedHeaders().get(1)
                                                   .get(HeaderNames.COOKIE)
                                                   .allValues());
        assertThat("Source cookies: " + sourceCookies, sourceCookies.contains("source="), is(true));
        assertThat("Source cookies: " + sourceCookies, sourceCookies.contains("writer="), is(true));
        assertThat("Target cookies: " + targetCookies, targetCookies.contains("source="), is(false));
        assertThat("Target cookies: " + targetCookies, targetCookies.contains("writer="), is(true));
    }

    @Test
    void shouldHonorServiceRemovalOfAutomaticCookies() {
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://example.test")
                .addService((chain, request) -> {
                    request.headers().remove(HeaderNames.COOKIE);
                    return chain.proceed(request);
                })
                .buildPrototype();
        TestContext context = TestContext.create(config, true);
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        HttpCookie storedCookie = new HttpCookie("stored", "cookie");
        storedCookie.setPath("/");
        context.webClient().cookieManager().getCookieStore().add(URI.create("http://localhost:80/"), storedCookie);

        try (HttpClientResponse response = context.request().request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(context.dynamic().submittedHeaders().getFirst().contains(HeaderNames.COOKIE), is(false));
    }

    @Test
    void shouldNotRestoreReplacedAutomaticCookieAfterAuthorityRetarget() {
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://example.test")
                .addService((chain, request) -> {
                    request.headers().set(HeaderNames.HOST, "target.test");
                    request.headers().set(HeaderNames.COOKIE, "session=service");
                    return chain.proceed(request);
                })
                .buildPrototype();
        TestContext context = TestContext.create(config, true);
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        HttpCookie sourceSession = new HttpCookie("session", "source");
        sourceSession.setPath("/");
        context.webClient().cookieManager().getCookieStore().add(URI.create("http://localhost:80/"), sourceSession);
        HttpCookie targetSession = new HttpCookie("session", "target");
        targetSession.setPath("/");
        context.webClient().cookieManager().getCookieStore().add(URI.create("http://target.test/"), targetSession);
        HttpCookie targetOther = new HttpCookie("other", "target");
        targetOther.setPath("/");
        context.webClient().cookieManager().getCookieStore().add(URI.create("http://target.test/"), targetOther);

        try (HttpClientResponse response = context.request().request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        String submittedCookies = String.join("; ",
                                              context.dynamic().submittedHeaders().getFirst()
                                                      .get(HeaderNames.COOKIE)
                                                      .allValues());
        assertThat("Submitted cookies: " + submittedCookies, submittedCookies.contains("session=service"), is(true));
        assertThat("Submitted cookies: " + submittedCookies, submittedCookies.contains("session=source"), is(false));
        assertThat("Submitted cookies: " + submittedCookies, submittedCookies.contains("session=target"), is(false));
        assertThat("Submitted cookies: " + submittedCookies, submittedCookies.contains("other="), is(true));
    }

    @Test
    void shouldHonorWriterRemovalOfAutomaticCookies() {
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://example.test")
                .mediaContext(writerMediaContext("payload".getBytes(StandardCharsets.UTF_8),
                                                 headers -> headers.remove(HeaderNames.COOKIE)))
                .addService((chain, request) -> chain.proceed(request))
                .buildPrototype();
        TestContext context = TestContext.create(config, true);
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        HttpCookie storedCookie = new HttpCookie("stored", "cookie");
        storedCookie.setPath("/");
        context.webClient().cookieManager().getCookieStore().add(URI.create("http://localhost:80/"), storedCookie);

        try (HttpClientResponse response = context.request(Method.POST).submit(new Object())) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(context.dynamic().submittedHeaders().getFirst().contains(HeaderNames.COOKIE), is(false));
    }

    @Test
    void shouldRetainWriterCookieMatchingRemovedManagerPair() {
        AtomicReference<String> managedPair = new AtomicReference<>();
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://example.test")
                .mediaContext(writerMediaContext("payload".getBytes(StandardCharsets.UTF_8), headers -> {
                    headers.set(HeaderNames.HOST, "target.test");
                    headers.add(HeaderNames.COOKIE, managedPair.get());
                }))
                .addService((chain, request) -> {
                    String pair = request.headers().get(HeaderNames.COOKIE).allValues()
                            .stream()
                            .flatMap(value -> List.of(value.split(";", -1)).stream())
                            .map(String::trim)
                            .filter(value -> value.startsWith("managed="))
                            .findFirst()
                            .orElseThrow();
                    managedPair.set(pair);
                    request.headers().remove(HeaderNames.COOKIE);
                    return chain.proceed(request);
                })
                .buildPrototype();
        TestContext context = TestContext.create(config, true);
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        HttpCookie storedCookie = new HttpCookie("managed", "cookie");
        storedCookie.setPath("/");
        context.webClient().cookieManager().getCookieStore().add(URI.create("http://localhost:80/"), storedCookie);

        try (HttpClientResponse response = context.request(Method.POST).submit(new Object())) {
            assertThat(response.status(), is(Status.OK_200));
        }

        String submittedCookies = String.join("; ",
                                              context.dynamic().submittedHeaders().getFirst()
                                                      .get(HeaderNames.COOKIE)
                                                      .allValues());
        assertThat("Submitted cookies: " + submittedCookies,
                   submittedCookies.contains(managedPair.get()),
                   is(true));
    }

    @Test
    void shouldPrepareOpaqueEntityForGenericServiceHandoff() {
        AtomicInteger writerInvocations = new AtomicInteger();
        HeaderName writerHeader = HeaderNames.create("X-Handoff-Writer");
        byte[] payload = "handoff".getBytes(StandardCharsets.UTF_8);
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://example.test")
                .mediaContext(writerMediaContext(payload, headers -> {
                    writerInvocations.incrementAndGet();
                    headers.set(writerHeader, "prepared");
                }))
                .addService((chain, request) -> chain.proceed(request))
                .buildPrototype();
        TestContext context = TestContext.create(config, true);
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        ClientRequestHeaders serviceHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        CompletableFuture<WebClientServiceRequest> whenSent = new CompletableFuture<>();
        WebClientServiceRequest serviceRequest = new ServiceRequestImpl(ClientUri.create(URI.create("http://example.test")),
                                                                        Method.POST,
                                                                        "any",
                                                                        null,
                                                                        serviceHeaders,
                                                                        Context.create(),
                                                                        "handoff",
                                                                        new CompletableFuture<>(),
                                                                        whenSent,
                                                                        Map.of());
        HttpClientRequest request = context.request(Method.POST);
        assertThat(request.serviceRequestAfterServices(serviceRequest,
                                                       _ -> {
                                                       },
                                                       whenSent,
                                                       _ -> {
                                                       }),
                   is(true));

        try (HttpClientResponse response = request.submit(new Object())) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(writerInvocations.get(), is(1));
        assertThat(context.dynamic().submittedEntities().getFirst(), is(payload));
        assertThat(context.dynamic().submittedHeaders().getFirst().first(writerHeader).orElseThrow(), is("prepared"));
    }

    @Test
    void shouldRetainFinalizedOriginWhenServiceRecoversTransportFailure() {
        IllegalStateException transportFailure = new IllegalStateException("transport failed");
        AtomicInteger serviceInvocations = new AtomicInteger();
        List<CompletableFuture<WebClientServiceRequest>> whenSent = new ArrayList<>();
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://example.test")
                .addService((chain, request) -> {
                    int invocation = serviceInvocations.getAndIncrement();
                    String sentHost = invocation == 0 ? "first.test" : "second.test";
                    request.headers().set(HeaderNames.HOST, sentHost);
                    whenSent.add(request.whenSent().toCompletableFuture());
                    try {
                        return chain.proceed(request);
                    } catch (IllegalStateException e) {
                        assertThat(e, sameInstance(transportFailure));
                        request.headers().set(HeaderNames.HOST, "late.test");
                        WritableHeaders<?> responseHeaders = WritableHeaders.create();
                        responseHeaders.set(HeaderNames.SET_COOKIE,
                                            "recovered" + invocation + "=stored; Path=/");
                        return WebClientServiceResponse.builder()
                                .serviceRequest(request)
                                .whenComplete(new CompletableFuture<>())
                                .connection(() -> {
                                })
                                .status(Status.OK_200)
                                .headers(ClientResponseHeaders.create(responseHeaders))
                                .build();
                    }
                })
                .buildPrototype();
        TestContext context = TestContext.create(config, true);
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().transportFailure(transportFailure);
        HttpClientRequest request = context.request();

        try (HttpClientResponse response = request.request()) {
            assertThat(response.status(), is(Status.OK_200));
        }
        try (HttpClientResponse response = request.request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(serviceInvocations.get(), is(2));
        assertThat(context.webClient().cookieManager().getCookieStore().get(URI.create("http://first.test/"))
                           .stream()
                           .anyMatch(cookie -> cookie.getName().equals("recovered0")),
                   is(true));
        assertThat(context.webClient().cookieManager().getCookieStore().get(URI.create("http://second.test/"))
                           .stream()
                           .anyMatch(cookie -> cookie.getName().equals("recovered1")),
                   is(true));
        assertThat(context.webClient().cookieManager().getCookieStore().get(URI.create("http://late.test/"))
                           .stream()
                           .anyMatch(cookie -> cookie.getName().startsWith("recovered")),
                   is(false));
        assertThat(whenSent.size(), is(2));
        for (CompletableFuture<WebClientServiceRequest> sent : whenSent) {
            CompletionException failure = assertThrows(CompletionException.class, sent::join);
            assertThat(failure.getCause(), sameInstance(transportFailure));
        }
    }

    @Test
    void shouldRetainFailedPreparationSnapshotWhenServiceRecovers() {
        IllegalStateException preparationFailure = new IllegalStateException("writer failed");
        AtomicReference<CompletableFuture<WebClientServiceRequest>> whenSent = new AtomicReference<>();
        AtomicReference<CompletableFuture<WebClientServiceResponse>> whenComplete = new AtomicReference<>();
        AtomicReference<WebClientServiceResponse> recoveredResponse = new AtomicReference<>();
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://example.test")
                .mediaContext(writerMediaContext("payload".getBytes(StandardCharsets.UTF_8), headers -> {
                    headers.set(HeaderNames.HOST, "prepared.test");
                    throw preparationFailure;
                }))
                .addService((chain, request) -> {
                    whenSent.set(request.whenSent().toCompletableFuture());
                    whenComplete.set(request.whenComplete().toCompletableFuture());
                    try {
                        return chain.proceed(request);
                    } catch (IllegalStateException failure) {
                        assertThat(failure, sameInstance(preparationFailure));
                        request.headers().set(HeaderNames.HOST, "late.test");
                        WritableHeaders<?> responseHeaders = WritableHeaders.create();
                        responseHeaders.set(HeaderNames.SET_COOKIE, "recovered=stored; Path=/");
                        WebClientServiceResponse response = WebClientServiceResponse.builder()
                                .serviceRequest(request)
                                .whenComplete(new CompletableFuture<>())
                                .connection(() -> {
                                })
                                .status(Status.ACCEPTED_202)
                                .headers(ClientResponseHeaders.create(responseHeaders))
                                .build();
                        recoveredResponse.set(response);
                        return response;
                    }
                })
                .buildPrototype();
        TestContext context = TestContext.create(config, true);
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);

        HttpClientResponse response = context.request(Method.POST).submit(new Object());
        assertThat(response.status(), is(Status.ACCEPTED_202));
        CompletionException sentFailure = assertThrows(CompletionException.class, () -> whenSent.get().join());
        assertThat(sentFailure.getCause(), sameInstance(preparationFailure));
        assertThat(whenComplete.get().isDone(), is(false));

        response.close();

        assertThat(whenComplete.get().join(), sameInstance(recoveredResponse.get()));
        assertThat(context.webClient().cookieManager().getCookieStore().get(URI.create("http://prepared.test/"))
                           .stream()
                           .anyMatch(cookie -> cookie.getName().equals("recovered")),
                   is(true));
        assertThat(context.webClient().cookieManager().getCookieStore().get(URI.create("http://late.test/"))
                           .stream()
                           .anyMatch(cookie -> cookie.getName().equals("recovered")),
                   is(false));
    }

    @Test
    void shouldReplayWriterHeadersForEmptyMethodPreservingEntity() {
        TestContext context = TestContext.createWithHandoffService((chain, request) -> chain.proceed(request));
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().redirectOnce(Status.TEMPORARY_REDIRECT_307);

        try (HttpClientResponse response = context.request(Method.POST)
                .followRedirects(true)
                .submit("")) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(context.dynamic().submittedHeaders().size(), is(2));
        String sourceContentType = context.dynamic().submittedHeaders().get(0)
                .first(HeaderNames.CONTENT_TYPE)
                .orElseThrow();
        assertThat(sourceContentType, startsWith("text/plain"));
        assertThat(context.dynamic().submittedHeaders().get(1)
                           .first(HeaderNames.CONTENT_TYPE)
                           .orElseThrow(),
                   is(sourceContentType));
    }

    @Test
    void shouldDropWriterHeadersWithEntityOnSeeOther() {
        TestContext context = TestContext.create();
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().redirectOnce(Status.SEE_OTHER_303);

        try (HttpClientResponse response = context.request(Method.POST)
                .followRedirects(true)
                .submit("payload")) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(context.dynamic().submittedHeaders().size(), is(2));
        assertThat(context.dynamic().submittedHeaders().get(0).contains(HeaderNames.CONTENT_TYPE), is(true));
        assertThat(context.dynamic().submittedHeaders().get(1).contains(HeaderNames.CONTENT_TYPE), is(false));
    }

    @Test
    void shouldRetainExplicitHeaderWhenDroppingEntityOnSeeOther() {
        TestContext context = TestContext.create();
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().redirectOnce(Status.SEE_OTHER_303);

        try (HttpClientResponse response = context.request(Method.POST)
                .header(HeaderNames.CONTENT_TYPE, "application/custom")
                .followRedirects(true)
                .submit("payload")) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(context.dynamic().submittedHeaders().size(), is(2));
        assertThat(context.dynamic().submittedHeaders().get(1)
                           .first(HeaderNames.CONTENT_TYPE)
                           .orElseThrow(),
                   is("application/custom"));
    }

    @Test
    void shouldDropWriterHeadersAfterPreservingRedirectThenSeeOther() {
        TestContext context = TestContext.create();
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().redirects(Status.TEMPORARY_REDIRECT_307, Status.SEE_OTHER_303);

        try (HttpClientResponse response = context.request(Method.POST)
                .followRedirects(true)
                .submit("payload")) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(context.dynamic().submittedHeaders().size(), is(3));
        assertThat(context.dynamic().submittedHeaders().get(0).contains(HeaderNames.CONTENT_TYPE), is(true));
        assertThat(context.dynamic().submittedHeaders().get(1).contains(HeaderNames.CONTENT_TYPE), is(true));
        assertThat(context.dynamic().submittedHeaders().get(2).contains(HeaderNames.CONTENT_TYPE), is(false));
    }

    @Test
    void shouldSnapshotLazyMultiValueWriterMutationAcrossRedirects() {
        HeaderName entityHeader = HeaderNames.create("X-Entity");
        byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://example.test")
                .mediaContext(writerMediaContext(payload,
                                                 headers -> headers.add(HeaderValues.create(entityHeader, "writer"))))
                .buildPrototype();
        TestContext context = TestContext.create(config);
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().redirects(Status.TEMPORARY_REDIRECT_307, Status.SEE_OTHER_303);

        try (HttpClientResponse response = context.request(Method.POST)
                .header(HeaderValues.create(entityHeader,
                                            new LazyString("base".getBytes(StandardCharsets.UTF_8),
                                                           StandardCharsets.UTF_8)))
                .followRedirects(true)
                .submit(new Object())) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(context.dynamic().submittedHeaders().size(), is(3));
        assertThat(context.dynamic().submittedHeaders().get(0).get(entityHeader).allValues(),
                   is(List.of("base", "writer")));
        assertThat(context.dynamic().submittedHeaders().get(1).get(entityHeader).allValues(),
                   is(List.of("base", "writer")));
        assertThat(context.dynamic().submittedHeaders().get(2).get(entityHeader).allValues(),
                   is(List.of("base")));
    }

    @Test
    void shouldDropWriterHeadersAfterEmptyPreservingRedirectThenSeeOther() {
        TestContext context = TestContext.create();
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().redirects(Status.TEMPORARY_REDIRECT_307, Status.SEE_OTHER_303);

        try (HttpClientResponse response = context.request(Method.POST)
                .followRedirects(true)
                .submit("")) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(context.dynamic().submittedHeaders().size(), is(3));
        assertThat(context.dynamic().submittedHeaders().get(0).contains(HeaderNames.CONTENT_TYPE), is(true));
        assertThat(context.dynamic().submittedHeaders().get(1).contains(HeaderNames.CONTENT_TYPE), is(true));
        assertThat(context.dynamic().submittedHeaders().get(2).contains(HeaderNames.CONTENT_TYPE), is(false));
    }

    @Test
    void shouldValidatePreservingRedirectScheme() {
        TestContext context = TestContext.create();
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().redirectOnce(Status.TEMPORARY_REDIRECT_307);
        context.dynamic().redirectLocation.set("ftp://other.test/target");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                        () -> context.request(Method.POST)
                                                                .followRedirects(true)
                                                                .submit("payload"));

        assertThat(failure.getMessage(), startsWith("Not supported scheme ftp"));
        assertThat(context.dynamic().transportInvocations(), is(1));
    }

    @Test
    void shouldFollowEmptyHeadPreservingRedirect() {
        TestContext context = TestContext.create();
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().redirectOnce(Status.TEMPORARY_REDIRECT_307);

        try (HttpClientResponse response = context.request(Method.HEAD)
                .followRedirects(true)
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(context.dynamic().transportInvocations(), is(2));
    }

    @Test
    void shouldRestoreRedirectBaseRatherThanSourceServiceHeaderWhenDroppingEntity() {
        HeaderName writerHeader = HeaderNames.create("X-Writer");
        HeaderName serviceDefaultHeader = HeaderNames.create("X-Service-Default");
        AtomicInteger serviceInvocations = new AtomicInteger();
        WebClientConfig config = WebClientConfig.builder()
                .baseUri("http://example.test")
                .mediaContext(redirectWriterMediaContext(writerHeader,
                                                         serviceDefaultHeader,
                                                         "payload".getBytes(StandardCharsets.UTF_8)))
                .addService((chain, request) -> {
                    int invocation = serviceInvocations.getAndIncrement();
                    String value = invocation == 0 ? "source-default" : "final-default";
                    request.headers().setIfAbsent(HeaderValues.create(serviceDefaultHeader, value));
                    return chain.proceed(request);
                })
                .buildPrototype();
        TestContext context = TestContext.create(config, true);
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().redirects(Status.TEMPORARY_REDIRECT_307, Status.SEE_OTHER_303);

        try (HttpClientResponse response = context.request(Method.POST)
                .followRedirects(true)
                .submit(new Object())) {
            assertThat(response.status(), is(Status.OK_200));
        }

        assertThat(serviceInvocations.get(), is(3));
        assertThat(context.dynamic().submittedHeaders().size(), is(3));
        assertThat(context.dynamic().submittedHeaders().get(0)
                           .first(serviceDefaultHeader)
                           .orElseThrow(),
                   is("writer-final"));
        assertThat(context.dynamic().submittedHeaders().get(1)
                           .first(serviceDefaultHeader)
                           .orElseThrow(),
                   is("writer-final"));
        assertThat(context.dynamic().submittedHeaders().get(2)
                           .first(serviceDefaultHeader)
                           .orElseThrow(),
                   is("final-default"));
    }

    @Test
    void shouldNotCacheProtocolFromUnattributedExplicitConnection() {
        TestContext context = TestContext.create();

        context.request()
                .connection(explicitConnection(false, null))
                .request();

        assertThat(context.cache().size(), is(0));
    }

    @Test
    void shouldKeepTlsIdentityInEndpointCacheKey() {
        ClientUri uri = ClientUri.create(URI.create("https://example.test"));
        Tls firstTls = Tls.builder().trustAll(true).build();
        Tls secondTls = Tls.builder().trustAll(true).build();
        SniSupport.State sniState = SniSupport.tlsDefault(uri, firstTls).state();

        LoomClient.EndpointKey first = new LoomClient.EndpointKey("https",
                                                                  "example.test:443",
                                                                  null,
                                                                  firstTls,
                                                                  sniState,
                                                                  io.helidon.webclient.api.Proxy.noProxy());
        LoomClient.EndpointKey second = new LoomClient.EndpointKey("https",
                                                                   "example.test:443",
                                                                   null,
                                                                   secondTls,
                                                                   sniState,
                                                                   io.helidon.webclient.api.Proxy.noProxy());
        Map<LoomClient.EndpointKey, String> cache = new HashMap<>();
        cache.put(first, "first");
        cache.put(second, "second");

        assertThat(first.equals(second), is(false));
        assertThat(cache.size(), is(2));
        assertThat(cache.get(first), is("first"));
        assertThat(cache.get(second), is("second"));
    }

    @Test
    void shouldPartitionProtocolCacheByFinalHostAuthority() {
        AtomicInteger serviceInvocations = new AtomicInteger();
        TestContext context = TestContext.createWithHandoffService((chain, request) -> {
            int invocation = serviceInvocations.incrementAndGet();
            request.headers().set(HeaderValues.create(HeaderNames.HOST,
                                                      invocation == 1
                                                              ? "first.example:80"
                                                              : "second.example:80"));
            return chain.proceed(request);
        });
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);

        try (HttpClientResponse ignored = context.request().request()) {
        }
        try (HttpClientResponse ignored = context.request().request()) {
        }

        assertThat(serviceInvocations.get(), is(2));
        assertThat(context.cache().size(), is(2));
    }

    @Test
    void shouldFailDecoratedResponseLifecyclesAndCloseResourcesOnReadFailure() {
        IllegalStateException readFailure = new IllegalStateException("decorated response read failed");
        AtomicInteger serviceCloseCount = new AtomicInteger();
        AtomicInteger serviceCompletionCount = new AtomicInteger();
        AtomicInteger outerCompletionCount = new AtomicInteger();
        AtomicReference<CompletableFuture<WebClientServiceResponse>> serviceCompletion = new AtomicReference<>();
        AtomicReference<CompletableFuture<WebClientServiceResponse>> outerCompletion = new AtomicReference<>();
        TestContext context = TestContext.createWithHandoffService((chain, request) -> {
            CompletableFuture<WebClientServiceResponse> outer = request.whenComplete().toCompletableFuture();
            outer.whenComplete((_, _) -> outerCompletionCount.incrementAndGet());
            outerCompletion.set(outer);
            WebClientServiceResponse rawResponse = chain.proceed(request);
            CompletableFuture<WebClientServiceResponse> decorated = new CompletableFuture<>();
            decorated.whenComplete((_, _) -> serviceCompletionCount.incrementAndGet());
            serviceCompletion.set(decorated);
            return WebClientServiceResponse.builder()
                    .serviceRequest(request)
                    .whenComplete(decorated)
                    .connection(serviceCloseCount::incrementAndGet)
                    .status(rawResponse.status())
                    .headers(rawResponse.headers())
                    .inputStream(new InputStream() {
                        @Override
                        public int read() {
                            throw readFailure;
                        }
                    })
                    .build();
        });
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);

        HttpClientResponse response = context.request().request();
        assertThat(serviceCompletion.get().isDone(), is(false));
        assertThat(outerCompletion.get().isDone(), is(false));
        IllegalStateException actualFailure = assertThrows(IllegalStateException.class,
                                                           () -> response.entity().inputStream().read());

        assertThat(actualFailure, sameInstance(readFailure));
        CompletionException serviceLifecycleFailure = assertThrows(CompletionException.class,
                                                                   serviceCompletion.get()::join);
        CompletionException outerLifecycleFailure = assertThrows(CompletionException.class,
                                                                 outerCompletion.get()::join);
        assertThat(serviceLifecycleFailure.getCause(), sameInstance(readFailure));
        assertThat(outerLifecycleFailure.getCause(), sameInstance(readFailure));
        assertThat(serviceCompletionCount.get(), is(1));
        assertThat(outerCompletionCount.get(), is(1));
        assertThat(serviceCloseCount.get(), is(1));
        assertThat(context.dynamic().responseCloseCount(), is(1));

        response.close();
        assertThat(serviceCompletionCount.get(), is(1));
        assertThat(outerCompletionCount.get(), is(1));
        assertThat(serviceCloseCount.get(), is(1));
        assertThat(context.dynamic().responseCloseCount(), is(1));
    }

    @Test
    void shouldFailResponseLifecyclesWhenCloseCleanupFails() {
        IllegalStateException cleanupFailure = new IllegalStateException("decorated response cleanup failed");
        AtomicInteger serviceCloseCount = new AtomicInteger();
        AtomicInteger serviceCompletionCount = new AtomicInteger();
        AtomicInteger outerCompletionCount = new AtomicInteger();
        AtomicReference<CompletableFuture<WebClientServiceResponse>> serviceCompletion = new AtomicReference<>();
        AtomicReference<CompletableFuture<WebClientServiceResponse>> outerCompletion = new AtomicReference<>();
        TestContext context = TestContext.createWithHandoffService((chain, request) -> {
            CompletableFuture<WebClientServiceResponse> outer = request.whenComplete().toCompletableFuture();
            outer.whenComplete((_, _) -> outerCompletionCount.incrementAndGet());
            outerCompletion.set(outer);
            WebClientServiceResponse rawResponse = chain.proceed(request);
            CompletableFuture<WebClientServiceResponse> decorated = new CompletableFuture<>();
            decorated.whenComplete((_, _) -> serviceCompletionCount.incrementAndGet());
            serviceCompletion.set(decorated);
            return WebClientServiceResponse.builder()
                    .serviceRequest(request)
                    .whenComplete(decorated)
                    .connection(() -> {
                        serviceCloseCount.incrementAndGet();
                        throw cleanupFailure;
                    })
                    .status(rawResponse.status())
                    .headers(rawResponse.headers())
                    .build();
        });
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);

        HttpClientResponse response = context.request().request();
        assertThat(serviceCompletion.get().isDone(), is(false));
        assertThat(outerCompletion.get().isDone(), is(false));
        IllegalStateException actualFailure = assertThrows(IllegalStateException.class, response::close);

        assertThat(actualFailure, sameInstance(cleanupFailure));
        CompletionException serviceLifecycleFailure = assertThrows(CompletionException.class,
                                                                   serviceCompletion.get()::join);
        CompletionException outerLifecycleFailure = assertThrows(CompletionException.class,
                                                                 outerCompletion.get()::join);
        assertThat(serviceLifecycleFailure.getCause(), sameInstance(cleanupFailure));
        assertThat(outerLifecycleFailure.getCause(), sameInstance(cleanupFailure));
        assertThat(serviceCompletionCount.get(), is(1));
        assertThat(outerCompletionCount.get(), is(1));
        assertThat(serviceCloseCount.get(), is(1));
        assertThat(context.dynamic().responseCloseCount(), is(1));

        response.close();
        assertThat(serviceCompletionCount.get(), is(1));
        assertThat(outerCompletionCount.get(), is(1));
        assertThat(serviceCloseCount.get(), is(1));
        assertThat(context.dynamic().responseCloseCount(), is(1));
    }

    @Test
    void shouldPreserveDecoratedCleanupFailureWhenRawCleanupAlsoFails() {
        IllegalStateException serviceFailure = new IllegalStateException("decorated response cleanup failed");
        IllegalStateException transportFailure = new IllegalStateException("transport response cleanup failed");
        AtomicReference<CompletableFuture<WebClientServiceResponse>> serviceCompletion = new AtomicReference<>();
        AtomicReference<CompletableFuture<WebClientServiceResponse>> outerCompletion = new AtomicReference<>();
        TestContext context = TestContext.createWithHandoffService((chain, request) -> {
            outerCompletion.set(request.whenComplete().toCompletableFuture());
            WebClientServiceResponse rawResponse = chain.proceed(request);
            CompletableFuture<WebClientServiceResponse> decorated = new CompletableFuture<>();
            serviceCompletion.set(decorated);
            return WebClientServiceResponse.builder()
                    .serviceRequest(request)
                    .whenComplete(decorated)
                    .connection(() -> {
                        throw serviceFailure;
                    })
                    .status(rawResponse.status())
                    .headers(rawResponse.headers())
                    .build();
        });
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().responseCloseFailure(transportFailure);

        HttpClientResponse response = context.request().request();
        IllegalStateException actualFailure = assertThrows(IllegalStateException.class, response::close);

        assertThat(actualFailure, sameInstance(serviceFailure));
        assertThat(actualFailure.getSuppressed().length, is(1));
        assertThat(actualFailure.getSuppressed()[0], sameInstance(transportFailure));
        CompletionException serviceLifecycleFailure = assertThrows(CompletionException.class,
                                                                   serviceCompletion.get()::join);
        CompletionException outerLifecycleFailure = assertThrows(CompletionException.class,
                                                                 outerCompletion.get()::join);
        CompletionException rawLifecycleFailure = assertThrows(
                CompletionException.class,
                context.dynamic().responseCompletion()::join);
        assertThat(serviceLifecycleFailure.getCause(), sameInstance(serviceFailure));
        assertThat(outerLifecycleFailure.getCause(), sameInstance(serviceFailure));
        assertThat(rawLifecycleFailure.getCause(), sameInstance(transportFailure));
        assertThat(context.dynamic().responseCloseCount(), is(1));

        response.close();
        assertThat(context.dynamic().responseCloseCount(), is(1));
    }

    @Test
    void shouldNotCompleteOuterLifecycleBeforeRawEntityCleanup() {
        IllegalStateException cleanupFailure = new IllegalStateException("raw entity cleanup failed");
        AtomicReference<CompletableFuture<WebClientServiceResponse>> outerCompletion = new AtomicReference<>();
        TestContext context = TestContext.createWithHandoffService((chain, request) -> {
            outerCompletion.set(request.whenComplete().toCompletableFuture());
            return chain.proceed(request);
        });
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().responseEntity("response body".getBytes(StandardCharsets.UTF_8));
        context.dynamic().responseCloseFailure(cleanupFailure);

        HttpClientResponse response = context.request().request();
        CompletableFuture<WebClientServiceResponse> rawCompletion = context.dynamic().responseCompletion();
        assertThat(rawCompletion.isDone(), is(false));
        assertThat(outerCompletion.get().isDone(), is(false));

        IllegalStateException actualFailure = assertThrows(
                IllegalStateException.class,
                () -> response.entity().inputStream().readAllBytes());

        assertThat(actualFailure, sameInstance(cleanupFailure));
        CompletionException rawLifecycleFailure = assertThrows(CompletionException.class, rawCompletion::join);
        CompletionException outerLifecycleFailure = assertThrows(CompletionException.class,
                                                                 outerCompletion.get()::join);
        assertThat(rawLifecycleFailure.getCause(), sameInstance(cleanupFailure));
        assertThat(outerLifecycleFailure.getCause(), sameInstance(cleanupFailure));
        assertThat(context.dynamic().responseCloseCount(), is(1));

        response.close();
        assertThat(context.dynamic().responseCloseCount(), is(1));
    }

    @Test
    void shouldNotMarkUnreadRawEntityConsumedForReplacementServiceBody() throws Exception {
        byte[] replacement = "cached service body".getBytes(StandardCharsets.UTF_8);
        TestContext context = TestContext.createWithHandoffService((chain, request) -> {
            WebClientServiceResponse rawResponse = chain.proceed(request);
            return WebClientServiceResponse.builder(rawResponse)
                    .inputStream(new ByteArrayInputStream(replacement))
                    .build();
        });
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().responseEntity("unread transport body".getBytes(StandardCharsets.UTF_8));

        HttpClientResponse response = context.request().request();
        try (InputStream inputStream = response.entity().inputStream()) {
            assertThat(inputStream.readAllBytes(), is(replacement));
        }

        assertThat(context.dynamic().serviceEntityConsumedCount(), is(0));
        assertThat(context.dynamic().responseCloseCount(), is(1));

        response.close();
        assertThat(context.dynamic().serviceEntityConsumedCount(), is(0));
        assertThat(context.dynamic().responseCloseCount(), is(1));
    }

    @Test
    void shouldPropagateRawTransportReadFailureToOuterLifecycle() {
        IllegalStateException readFailure = new IllegalStateException("raw response read failed");
        AtomicInteger rawCompletionCount = new AtomicInteger();
        AtomicInteger outerCompletionCount = new AtomicInteger();
        AtomicReference<CompletableFuture<WebClientServiceResponse>> outerCompletion = new AtomicReference<>();
        TestContext context = TestContext.createWithHandoffService((chain, request) -> {
            CompletableFuture<WebClientServiceResponse> outer = request.whenComplete().toCompletableFuture();
            outer.whenComplete((_, _) -> outerCompletionCount.incrementAndGet());
            outerCompletion.set(outer);
            return chain.proceed(request);
        });
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().responseEntityFailure(readFailure);

        HttpClientResponse response = context.request().request();
        CompletableFuture<WebClientServiceResponse> rawCompletion = context.dynamic().responseCompletion();
        rawCompletion.whenComplete((_, _) -> rawCompletionCount.incrementAndGet());
        assertThat(rawCompletion.isDone(), is(false));
        assertThat(outerCompletion.get().isDone(), is(false));

        IllegalStateException actualFailure = assertThrows(IllegalStateException.class,
                                                           () -> response.entity().inputStream().read());

        assertThat(actualFailure, sameInstance(readFailure));
        CompletionException rawLifecycleFailure = assertThrows(CompletionException.class, rawCompletion::join);
        CompletionException outerLifecycleFailure = assertThrows(CompletionException.class,
                                                                 outerCompletion.get()::join);
        assertThat(rawLifecycleFailure.getCause(), sameInstance(readFailure));
        assertThat(outerLifecycleFailure.getCause(), sameInstance(readFailure));
        assertThat(rawCompletionCount.get(), is(1));
        assertThat(outerCompletionCount.get(), is(1));

        response.close();
        assertThat(rawCompletionCount.get(), is(1));
        assertThat(outerCompletionCount.get(), is(1));
    }

    @Test
    void shouldCompleteRawResponseLifecycleOnlyAfterOuterEntityConsumption() throws Exception {
        AtomicInteger rawCompletionCount = new AtomicInteger();
        AtomicInteger outerCompletionCount = new AtomicInteger();
        AtomicReference<CompletableFuture<WebClientServiceResponse>> outerCompletion = new AtomicReference<>();
        TestContext context = TestContext.createWithHandoffService((chain, request) -> {
            CompletableFuture<WebClientServiceResponse> outer = request.whenComplete().toCompletableFuture();
            outer.whenComplete((_, _) -> outerCompletionCount.incrementAndGet());
            outerCompletion.set(outer);
            return chain.proceed(request);
        });
        context.dynamic().support(HttpClientSpi.SupportLevel.SUPPORTED);
        context.dynamic().responseEntity("response body".getBytes(StandardCharsets.UTF_8));

        HttpClientResponse response = context.request().request();
        CompletableFuture<WebClientServiceResponse> rawCompletion = context.dynamic().responseCompletion();
        rawCompletion.whenComplete((_, _) -> rawCompletionCount.incrementAndGet());
        assertThat(rawCompletion.isDone(), is(false));
        assertThat(outerCompletion.get().isDone(), is(false));

        try (InputStream inputStream = response.entity().inputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8), is("response body"));
        }

        assertThat(rawCompletion.join(), sameInstance(context.dynamic().serviceResponse()));
        assertThat(outerCompletion.get().join(), sameInstance(context.dynamic().serviceResponse()));
        assertThat(rawCompletionCount.get(), is(1));
        assertThat(outerCompletionCount.get(), is(1));

        response.close();
        assertThat(rawCompletionCount.get(), is(1));
        assertThat(outerCompletionCount.get(), is(1));
    }

    private static MediaContext redirectWriterMediaContext(HeaderName writerHeader,
                                                            HeaderName serviceDefaultHeader,
                                                            byte[] payload) {
        return writerMediaContext(payload, requestHeaders -> {
            requestHeaders.set(writerHeader, "writer");
            requestHeaders.set(serviceDefaultHeader, "writer-final");
            requestHeaders.set(HeaderNames.AUTHORIZATION, "writer-secret");
            requestHeaders.set(HeaderNames.CONTENT_TYPE, "text/plain");
        });
    }

    private static MediaContext writerMediaContext(byte[] payload, Consumer<WritableHeaders<?>> headerMutator) {
        EntityWriter<Object> entityWriter = new EntityWriter<>() {
            @Override
            public boolean supportsInstanceWriter() {
                return true;
            }

            @Override
            public InstanceWriter instanceWriter(GenericType<Object> type,
                                                  Object object,
                                                  WritableHeaders<?> requestHeaders) {
                headerMutator.accept(requestHeaders);
                return new InstanceWriter() {
                    @Override
                    public OptionalLong contentLength() {
                        return OptionalLong.of(payload.length);
                    }

                    @Override
                    public boolean alwaysInMemory() {
                        return true;
                    }

                    @Override
                    public void write(OutputStream stream) {
                        throw new AssertionError("The materialized writer must use instanceBytes");
                    }

                    @Override
                    public byte[] instanceBytes() {
                        return payload;
                    }
                };
            }

            @Override
            public void write(GenericType<Object> type,
                              Object object,
                              OutputStream outputStream,
                              Headers requestHeaders,
                              WritableHeaders<?> responseHeaders) {
                throw new AssertionError("The materialized writer must use instanceWriter");
            }

            @Override
            public void write(GenericType<Object> type,
                              Object object,
                              OutputStream outputStream,
                              WritableHeaders<?> headers) {
                throw new AssertionError("The materialized writer must use instanceWriter");
            }
        };
        MediaSupport mediaSupport = new MediaSupport() {
            @Override
            public String name() {
                return "redirect-writer";
            }

            @Override
            public String type() {
                return "redirect-writer";
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
                return new WriterResponse<>(SupportLevel.SUPPORTED, () -> (EntityWriter<T>) entityWriter);
            }
        };
        return MediaContext.builder()
                .registerDefaults(false)
                .mediaSupportsDiscoverServices(false)
                .addMediaSupport(mediaSupport)
                .build();
    }

    private static ClientConnection explicitConnection(boolean protocolNegotiated, String protocol) {
        HelidonSocket socket = (HelidonSocket) Proxy.newProxyInstance(HelidonSocket.class.getClassLoader(),
                                                                      new Class<?>[] {HelidonSocket.class},
                                                                      (_, method, _) -> switch (method.getName()) {
                                                                          case "protocolNegotiated" -> protocolNegotiated;
                                                                          case "protocol" -> protocol;
                                                                          default -> primitiveDefault(method.getReturnType());
                                                                      });
        return (ClientConnection) Proxy.newProxyInstance(ClientConnection.class.getClassLoader(),
                                                         new Class<?>[] {ClientConnection.class},
                                                         (proxy, method, _) -> switch (method.getName()) {
                                                             case "helidonSocket" -> socket;
                                                             case "connect" -> proxy;
                                                             default -> primitiveDefault(method.getReturnType());
                                                         });
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == double.class) {
            return 0.0;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }

    private record TestContext(WebClient webClient,
                               WebClientConfig config,
                               List<LoomClient.ProtocolSpi> protocols,
                               Map<String, LoomClient.ProtocolSpi> clients,
                               LruCache<LoomClient.EndpointKey, HttpClientSpi> cache,
                               TestClientSpi dynamic,
                               TestClientSpi fallback,
                               AtomicReference<String> selected) {
        private static TestContext create() {
            WebClientConfig config = WebClientConfig.builder()
                    .baseUri("http://example.test")
                    .buildPrototype();
            return create(config);
        }

        private static TestContext createWithService() {
            WebClientConfig config = WebClientConfig.builder()
                    .baseUri("http://example.test")
                    .addService((chain, request) -> chain.proceed(request))
                    .buildPrototype();
            return create(config);
        }

        private static TestContext createWithHandoffService(WebClientService service) {
            WebClientConfig config = WebClientConfig.builder()
                    .baseUri("http://example.test")
                    .addService(service)
                    .buildPrototype();
            return create(config, true);
        }

        private static TestContext create(WebClientConfig config) {
            return create(config, false);
        }

        private static TestContext create(WebClientConfig config, boolean serviceHandoff) {
            TestWebClient webClient = new TestWebClient(config);
            AtomicReference<String> selected = new AtomicReference<>();
            TestClientSpi dynamic = new TestClientSpi("dynamic", true, serviceHandoff, selected, config);
            TestClientSpi fallback = new TestClientSpi("http/1.1", true, serviceHandoff, selected, config);
            fallback.support(HttpClientSpi.SupportLevel.COMPATIBLE);
            LoomClient.ProtocolSpi dynamicProtocol = new LoomClient.ProtocolSpi("dynamic", dynamic);
            LoomClient.ProtocolSpi fallbackProtocol = new LoomClient.ProtocolSpi("http/1.1", fallback);
            return new TestContext(webClient,
                                   config,
                                   List.of(dynamicProtocol, fallbackProtocol),
                                   Map.of("dynamic", dynamicProtocol, "http/1.1", fallbackProtocol),
                                   LruCache.create(),
                                   dynamic,
                                   fallback,
                                   selected);
        }

        private HttpClientRequest request() {
            return request(Method.GET);
        }

        private HttpClientRequest request(Method method) {
            return new HttpClientRequest(webClient,
                                         config,
                                         method,
                                         ClientUri.create(),
                                         clients,
                                         protocols,
                                         protocols,
                                         List.of("dynamic", "http/1.1"),
                                         cache);
        }
    }

    private static final class TestClientSpi implements HttpClientSpi {
        private final String id;
        private final boolean tcp;
        private final boolean serviceHandoff;
        private final AtomicReference<String> selected;
        private final WebClientConfig config;
        private final AtomicReference<SupportLevel> support = new AtomicReference<>(SupportLevel.NOT_SUPPORTED);
        private final AtomicInteger supportsInvocations = new AtomicInteger();
        private final AtomicReference<String> requestedProtocolId = new AtomicReference<>();
        private final AtomicLong tlsGeneration = new AtomicLong();
        private final AtomicReference<ClientConnection> capturedConnection = new AtomicReference<>();
        private final AtomicReference<SocketAddress> capturedAddress = new AtomicReference<>();
        private final AtomicReference<ProxyRoute> capturedSelectedProxyRoute = new AtomicReference<>();
        private final AtomicBoolean reloadTlsOnSelection = new AtomicBoolean();
        private final AtomicReference<List<Status>> redirectStatuses = new AtomicReference<>(List.of());
        private final AtomicReference<String> redirectLocation = new AtomicReference<>("/target");
        private final AtomicReference<String> responseSetCookie = new AtomicReference<>();
        private final AtomicInteger transportInvocations = new AtomicInteger();
        private final List<Object> submittedEntities = new ArrayList<>();
        private final List<ClientRequestHeaders> submittedHeaders = new ArrayList<>();
        private final AtomicReference<byte[]> responseEntity = new AtomicReference<>();
        private final AtomicReference<RuntimeException> responseEntityFailure = new AtomicReference<>();
        private final AtomicReference<RuntimeException> transportFailure = new AtomicReference<>();
        private final AtomicReference<CompletableFuture<WebClientServiceResponse>> responseCompletion =
                new AtomicReference<>();
        private final AtomicReference<WebClientServiceResponse> serviceResponse = new AtomicReference<>();
        private final AtomicInteger responseCloseCount = new AtomicInteger();
        private final AtomicReference<RuntimeException> responseCloseFailure = new AtomicReference<>();
        private final AtomicInteger serviceEntityConsumedCount = new AtomicInteger();

        private TestClientSpi(String id,
                              boolean tcp,
                              boolean serviceHandoff,
                              AtomicReference<String> selected,
                              WebClientConfig config) {
            this.id = id;
            this.tcp = tcp;
            this.serviceHandoff = serviceHandoff;
            this.selected = selected;
            this.config = config;
        }

        @Override
        public SupportLevel supports(FullClientRequest<?> clientRequest, ClientUri clientUri) {
            supportsInvocations.incrementAndGet();
            return support.get();
        }

        @Override
        public ClientRequest<?> clientRequest(FullClientRequest<?> clientRequest, ClientUri clientUri) {
            selected.set(id);
            requestedProtocolId.set(clientRequest.requestedProtocolId().orElse(null));
            tlsGeneration.set(clientRequest.tlsGeneration());
            capturedConnection.set(clientRequest.connection().orElse(null));
            capturedAddress.set(clientRequest.address().orElse(null));
            capturedSelectedProxyRoute.set(clientRequest.selectedProxyRoute().orElse(null));
            ClientRequestHeaders transportHeaders = ClientRequestHeaders.create((Headers) clientRequest.headers());
            if (reloadTlsOnSelection.compareAndSet(true, false)) {
                clientRequest.tls().reload(TlsMaterial.builder().trustAll(true).build());
            }
            AtomicReference<WebClientServiceRequest> serviceRequest = new AtomicReference<>();
            AtomicReference<Consumer<WebClientServiceResponse>> responseConsumer = new AtomicReference<>();
            AtomicReference<CompletableFuture<WebClientServiceRequest>> whenSent = new AtomicReference<>();
            return (ClientRequest<?>) Proxy.newProxyInstance(ClientRequest.class.getClassLoader(),
                                                             new Class<?>[] {ClientRequest.class},
                                                             (proxy, method, arguments) -> {
                                                                 if (method.getName()
                                                                         .equals("serviceRequestAfterServices")) {
                                                                     serviceRequest.set((WebClientServiceRequest) arguments[0]);
                                                                     responseConsumer.set(
                                                                             (Consumer<WebClientServiceResponse>) arguments[1]);
                                                                     whenSent.set(
                                                                             (CompletableFuture<WebClientServiceRequest>) arguments[2]);
                                                                     return serviceHandoff;
                                                                 }
                                                                 if (ClientRequest.class.isAssignableFrom(method.getReturnType())) {
                                                                     return proxy;
                                                                 }
                                                                 if (HttpClientResponse.class
                                                                         .isAssignableFrom(method.getReturnType())) {
                                                                     int invocation = transportInvocations.getAndIncrement();
                                                                     RuntimeException configuredTransportFailure =
                                                                             transportFailure.get();
                                                                     if (configuredTransportFailure != null) {
                                                                         throw configuredTransportFailure;
                                                                     }
                                                                     if (method.getName().equals("submit")
                                                                             && arguments != null
                                                                             && arguments.length == 1) {
                                                                         submittedEntities.add(arguments[0]);
                                                                     }
                                                                     WebClientServiceRequest request = serviceRequest.get();
                                                                     submittedHeaders.add(request == null
                                                                                                  ? transportHeaders
                                                                                                  : ClientRequestHeaders.create(
                                                                                                          (Headers) request.headers()));
                                                                     if (request != null) {
                                                                         whenSent.get().complete(request);
                                                                     }
                                                                     List<Status> configuredRedirects = redirectStatuses.get();
                                                                     Status status = invocation < configuredRedirects.size()
                                                                             ? configuredRedirects.get(invocation)
                                                                             : Status.OK_200;
                                                                     WritableHeaders<?> responseHeaderValues =
                                                                             WritableHeaders.create();
                                                                     if (status.family() == Status.Family.REDIRECTION) {
                                                                         responseHeaderValues.set(HeaderNames.LOCATION,
                                                                                                  redirectLocation.get());
                                                                     }
                                                                     if (responseSetCookie.get() != null) {
                                                                         responseHeaderValues.set(HeaderNames.SET_COOKIE,
                                                                                                  responseSetCookie.get());
                                                                     }
                                                                     ClientResponseHeaders responseHeaders =
                                                                             ClientResponseHeaders.create(
                                                                                     responseHeaderValues);
                                                                     AtomicReference<HttpClientResponse> responseRef =
                                                                             new AtomicReference<>();
                                                                     CompletableFuture<WebClientServiceResponse> completion = null;
                                                                     WebClientServiceResponse rawResponse = null;
                                                                     ReadableEntity entity = null;
                                                                     if (request != null) {
                                                                         CompletableFuture<WebClientServiceResponse> localCompletion =
                                                                                 new CompletableFuture<>();
                                                                         WebClientServiceResponse localRawResponse =
                                                                                 WebClientServiceResponse.builder()
                                                                                         .headers(responseHeaders)
                                                                                         .status(status)
                                                                                         .connection(() -> {
                                                                                         })
                                                                                         .whenComplete(localCompletion)
                                                                                         .serviceRequest(request)
                                                                                         .build();
                                                                         completion = localCompletion;
                                                                         rawResponse = localRawResponse;
                                                                         responseCompletion.set(localCompletion);
                                                                         serviceResponse.set(localRawResponse);
                                                                         responseConsumer.get().accept(localRawResponse);
                                                                         RuntimeException configuredFailure =
                                                                                 responseEntityFailure.get();
                                                                         byte[] configuredEntity = responseEntity.get();
                                                                         if (configuredFailure != null || configuredEntity != null) {
                                                                             AtomicBoolean returnedEntity = new AtomicBoolean();
                                                                             entity = ClientResponseEntity.create(_ -> {
                                                                                 if (configuredFailure != null) {
                                                                                     localCompletion.completeExceptionally(
                                                                                             configuredFailure);
                                                                                     throw configuredFailure;
                                                                                 }
                                                                                 if (returnedEntity.compareAndSet(false, true)) {
                                                                                     return BufferData.create(configuredEntity);
                                                                                 }
                                                                                 return BufferData.empty();
                                                                             },
                                                                                                                  () -> responseRef
                                                                                                                          .get()
                                                                                                                          .close(),
                                                                                                                  request.headers(),
                                                                                                                  responseHeaders,
                                                                                                                  config.mediaContext(),
                                                                                                                  config.maxInMemoryEntity());
                                                                         }
                                                                     }
                                                                     CompletableFuture<WebClientServiceResponse> responseCompletion =
                                                                             completion;
                                                                     WebClientServiceResponse transportServiceResponse = rawResponse;
                                                                     ReadableEntity responseEntity = entity;
                                                                     AtomicBoolean responseClosed = new AtomicBoolean();
                                                                     HttpClientResponse result = (HttpClientResponse) Proxy.newProxyInstance(
                                                                             HttpClientResponse.class.getClassLoader(),
                                                                             new Class<?>[] {HttpClientResponse.class},
                                                                             (_, responseMethod, _) -> {
                                                                                 if (responseMethod.getName().equals("close")) {
                                                                                     if (responseClosed.compareAndSet(false, true)) {
                                                                                         responseCloseCount.incrementAndGet();
                                                                                         RuntimeException closeFailure =
                                                                                                 responseCloseFailure.get();
                                                                                         if (responseCompletion != null) {
                                                                                             if (closeFailure == null) {
                                                                                                 responseCompletion.complete(
                                                                                                         transportServiceResponse);
                                                                                             } else {
                                                                                                 responseCompletion.completeExceptionally(
                                                                                                         closeFailure);
                                                                                             }
                                                                                         }
                                                                                         if (closeFailure != null) {
                                                                                             throw closeFailure;
                                                                                         }
                                                                                     }
                                                                                     return null;
                                                                                 }
                                                                                 if (responseMethod.getName()
                                                                                         .equals("protocolId")) {
                                                                                     return id;
                                                                                 }
                                                                                 if (responseMethod.getName()
                                                                                         .equals("lastEndpointUri")) {
                                                                                     return clientUri;
                                                                                 }
                                                                                 if (responseMethod.getName()
                                                                                         .equals("status")) {
                                                                                     return status;
                                                                                 }
                                                                                 if (responseMethod.getName()
                                                                                         .equals("headers")) {
                                                                                     return responseHeaders;
                                                                                 }
                                                                                 if (responseMethod.getName().equals("entity")) {
                                                                                     return responseEntity;
                                                                                 }
                                                                                 if (responseMethod.getName()
                                                                                         .equals("serviceEntityConsumed")) {
                                                                                     serviceEntityConsumedCount.incrementAndGet();
                                                                                     return null;
                                                                                 }
                                                                                 if (responseMethod.getReturnType()
                                                                                         == boolean.class) {
                                                                                     return false;
                                                                                 }
                                                                                 if (responseMethod.getReturnType()
                                                                                         == long.class) {
                                                                                     return Long.MAX_VALUE;
                                                                                 }
                                                                                 if (responseMethod.getReturnType()
                                                                                         == int.class) {
                                                                                     return 0;
                                                                                 }
                                                                                 return null;
                                                                             });
                                                                     responseRef.set(result);
                                                                     return result;
                                                                 }
                                                                 if (method.getReturnType() == boolean.class) {
                                                                     return false;
                                                                 }
                                                                 if (method.getReturnType() == int.class) {
                                                                     return 0;
                                                                 }
                                                                 return null;
                                                             });
        }

        @Override
        public boolean isTcp() {
            return tcp;
        }

        @Override
        public boolean supportsServiceHandoff() {
            return serviceHandoff;
        }

        @Override
        public void closeResource() {
        }

        private void support(SupportLevel support) {
            this.support.set(support);
        }

        private String requestedProtocolId() {
            return requestedProtocolId.get();
        }

        private void reloadTlsOnSelection() {
            reloadTlsOnSelection.set(true);
        }

        private long tlsGeneration() {
            return tlsGeneration.get();
        }

        private ClientConnection capturedConnection() {
            return capturedConnection.get();
        }

        private SocketAddress capturedAddress() {
            return capturedAddress.get();
        }

        private ProxyRoute capturedSelectedProxyRoute() {
            return capturedSelectedProxyRoute.get();
        }

        private void redirectOnce(Status status) {
            redirects(status);
        }

        private void redirects(Status... statuses) {
            redirectStatuses.set(List.of(statuses));
        }

        private int transportInvocations() {
            return transportInvocations.get();
        }

        private List<Object> submittedEntities() {
            return List.copyOf(submittedEntities);
        }

        private List<ClientRequestHeaders> submittedHeaders() {
            return List.copyOf(submittedHeaders);
        }

        private void responseEntity(byte[] entity) {
            responseEntity.set(entity);
        }

        private void responseSetCookie(String cookie) {
            responseSetCookie.set(cookie);
        }

        private void responseEntityFailure(RuntimeException failure) {
            responseEntityFailure.set(failure);
        }

        private void transportFailure(RuntimeException failure) {
            transportFailure.set(failure);
        }

        private CompletableFuture<WebClientServiceResponse> responseCompletion() {
            return responseCompletion.get();
        }

        private WebClientServiceResponse serviceResponse() {
            return serviceResponse.get();
        }

        private int responseCloseCount() {
            return responseCloseCount.get();
        }

        private void responseCloseFailure(RuntimeException failure) {
            responseCloseFailure.set(failure);
        }

        private int serviceEntityConsumedCount() {
            return serviceEntityConsumedCount.get();
        }

        private int supportsInvocations() {
            return supportsInvocations.get();
        }
    }

    private static final class TestClientRequest extends ClientRequestBase<TestClientRequest, HttpClientResponse> {
        private TestClientRequest(HttpClientConfig clientConfig,
                                  FullClientRequest<?> clientRequest,
                                  ClientUri clientUri) {
            super(clientConfig,
                  WebClientCookieManager.builder().build(),
                  "test",
                  clientRequest.method(),
                  clientUri,
                  clientRequest.properties());
        }

        @Override
        protected HttpClientResponse doSubmit(Object entity) {
            return new TestClientResponse(resolvedUri());
        }

        @Override
        protected HttpClientResponse doOutputStream(OutputStreamHandler outputStreamHandler) {
            return new TestClientResponse(resolvedUri());
        }
    }

    private static final class TestClientResponse implements HttpClientResponse {
        private final ClientUri endpointUri;

        private TestClientResponse(ClientUri endpointUri) {
            this.endpointUri = endpointUri;
        }

        @Override
        public Status status() {
            return Status.OK_200;
        }

        @Override
        public ClientResponseHeaders headers() {
            return ClientResponseHeaders.create(WritableHeaders.create());
        }

        @Override
        public ClientResponseTrailers trailers() {
            return ClientResponseTrailers.create();
        }

        @Override
        public ClientUri lastEndpointUri() {
            return endpointUri;
        }

        @Override
        public ReadableEntity entity() {
            return ReadableEntityBase.empty();
        }

        @Override
        public void close() {
        }
    }

    private static final class TestWebClient implements WebClient {
        private final WebClientConfig config;
        private final WebClientCookieManager cookieManager = WebClientCookieManager.builder()
                .automaticStoreEnabled(true)
                .build();

        private TestWebClient(WebClientConfig config) {
            this.config = config;
        }

        @Override
        public HttpClientRequest method(Method method) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T, C extends ProtocolConfig> T client(Protocol<T, C> protocol, C protocolConfig) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T, C extends ProtocolConfig> T client(Protocol<T, C> protocol) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExecutorService executor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WebClientCookieManager cookieManager() {
            return cookieManager;
        }

        @Override
        public WebClientConfig prototype() {
            return config;
        }

        @Override
        public void closeResource() {
        }
    }
}
