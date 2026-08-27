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

package io.helidon.webclient.http2;

import java.net.InetAddress;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsConfig;
import io.helidon.common.tls.TlsManager;
import io.helidon.common.uri.UriAuthority;
import io.helidon.common.uri.UriHost;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.ClientAltSvcConfig;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.ResolvedClientTarget;
import io.helidon.webclient.api.SniConfig;
import io.helidon.webclient.api.SniMode;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientProtocolResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.spi.HttpClientSpi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http2AltSvcClientTest {
    @Test
    void ignoresAdvertisementWhenAltSvcIsAbsent() {
        try (TestContext context = TestContext.create()) {
            context.client.responseReceived(context.directResponse(Http1Client.PROTOCOL_ID, false, Status.OK_200));

            assertThat(context.client.supports(context.request, context.uri),
                       is(HttpClientSpi.SupportLevel.NOT_SUPPORTED));
        }
    }

    @Test
    void noEntrySkipsFinalTargetResolution() {
        try (TestContext context = TestContext.create(ClientAltSvcConfig.create())) {
            clearInvocations((Object) context.request);

            assertThat(context.client.supports(context.request, context.uri),
                       is(HttpClientSpi.SupportLevel.NOT_SUPPORTED));
            verify(context.request, never()).headers();
            verify(context.request, never()).selectedProxyRoute();
        }
    }

    @Test
    void explicitConnectionSkipsAdvertisedHost() {
        try (TestContext context = TestContext.create(ClientAltSvcConfig.create())) {
            context.client.responseReceived(context.directResponse(Http1Client.PROTOCOL_ID, false, Status.OK_200));
            when(context.request.connection()).thenReturn(Optional.of(mock(ClientConnection.class)));
            clearInvocations((Object) context.request);

            assertThat(context.client.supports(context.request, context.uri),
                       is(HttpClientSpi.SupportLevel.NOT_SUPPORTED));
            verify(context.request, never()).headers();
            verify(context.request, never()).selectedProxyRoute();
        }
    }

    @ParameterizedTest
    @MethodSource("tcpProtocolIds")
    void learnsH2AlternativeFromHttpsResponse(String protocolId) {
        try (TestContext context = TestContext.create(ClientAltSvcConfig.create())) {
            assertThat(context.client.connectionCache().supports(context.connectionKey), is(false));

            context.client.responseReceived(context.directResponse(protocolId, false, Status.OK_200));

            assertThat(context.client.connectionCache().supports(context.connectionKey), is(false));
            assertThat(context.client.supports(context.request, context.uri), is(HttpClientSpi.SupportLevel.SUPPORTED));

            when(context.request.selectedProxyRoute()).thenReturn(Optional.of(context.target.proxyRoute()));
            assertThat(context.client.supports(context.request, context.uri), is(HttpClientSpi.SupportLevel.SUPPORTED));
        }
    }

    @Test
    void ignoresAdvertisementFromPlainHttpOrigin() {
        try (TestContext context = TestContext.createPlain(ClientAltSvcConfig.create())) {
            context.client.responseReceived(context.directResponse(Http1Client.PROTOCOL_ID, false, Status.OK_200));

            assertThat(context.client.supports(context.request, context.uri),
                       is(HttpClientSpi.SupportLevel.NOT_SUPPORTED));
        }
    }

    @Test
    void ignoresAdvertisementWhenDisabled() {
        ClientAltSvcConfig altSvc = ClientAltSvcConfig.builder()
                .enabled(false)
                .build();
        try (TestContext context = TestContext.create(altSvc)) {
            context.client.responseReceived(context.directResponse(Http1Client.PROTOCOL_ID, false, Status.OK_200));

            assertThat(context.client.supports(context.request, context.uri),
                       is(HttpClientSpi.SupportLevel.NOT_SUPPORTED));
        }
    }

    @ParameterizedTest
    @MethodSource("disallowedProtocols")
    void ignoresAdvertisementWhenH2IsNotAllowed(String protocol) {
        ClientAltSvcConfig altSvc = ClientAltSvcConfig.builder()
                .addProtocol(protocol)
                .build();
        try (TestContext context = TestContext.create(altSvc)) {
            context.client.responseReceived(context.directResponse(Http1Client.PROTOCOL_ID, false, Status.OK_200));

            assertThat(context.client.supports(context.request, context.uri),
                       is(HttpClientSpi.SupportLevel.NOT_SUPPORTED));
        }
    }

    @Test
    void learnsAdvertisementWhenH2IsAllowed() {
        ClientAltSvcConfig altSvc = ClientAltSvcConfig.builder()
                .addProtocol(Http2Client.PROTOCOL_ID)
                .build();
        try (TestContext context = TestContext.create(altSvc)) {
            context.client.responseReceived(context.directResponse(Http1Client.PROTOCOL_ID, false, Status.OK_200));

            assertThat(context.client.supports(context.request, context.uri),
                       is(HttpClientSpi.SupportLevel.SUPPORTED));
        }
    }

    @Test
    void supportsAlternativeWithHostHeaderSni() {
        try (TestContext context = TestContext.createHostHeaderSni(ClientAltSvcConfig.create())) {
            context.client.responseReceived(context.directResponse(Http1Client.PROTOCOL_ID, false, Status.OK_200));

            assertThat(context.client.supports(context.request, context.uri), is(HttpClientSpi.SupportLevel.SUPPORTED));
        }
    }

    @Test
    void responseObservationTimeOrdersDelayedCallbacks() {
        try (TestContext context = TestContext.create(ClientAltSvcConfig.create())) {
            Instant newer = Instant.now();
            Instant older = newer.minusSeconds(1);

            context.client.responseReceived(context.directResponse(Http1Client.PROTOCOL_ID,
                                                                    false,
                                                                    Status.OK_200,
                                                                    responseHeaders("h2=\":9443\"; ma=3600"),
                                                                    newer));
            context.client.responseReceived(context.directResponse(Http1Client.PROTOCOL_ID,
                                                                    false,
                                                                    Status.OK_200,
                                                                    responseHeaders("h2=\":8443\"; ma=3600"),
                                                                    older));

            Http2AltSvcCache.Selection selection = context.client.connectionCache()
                    .currentAlternative(context.target, false);
            assertThat(selection, notNullValue());
            assertThat(selection.port(), is(9443));
        }
    }

    @Test
    void clearAfterEmptyElementBoundInvalidatesAlternative() {
        try (TestContext context = TestContext.create(ClientAltSvcConfig.create())) {
            Instant advertisementTime = Instant.now();
            context.client.responseReceived(context.directResponse(Http1Client.PROTOCOL_ID,
                                                                    false,
                                                                    Status.OK_200,
                                                                    responseHeaders(),
                                                                    advertisementTime));
            assertThat(context.client.supports(context.request, context.uri), is(HttpClientSpi.SupportLevel.SUPPORTED));

            context.client.responseReceived(context.directResponse(Http1Client.PROTOCOL_ID,
                                                                    false,
                                                                    Status.OK_200,
                                                                    responseHeaders(",".repeat(33) + "clear"),
                                                                    advertisementTime.plusNanos(1)));

            assertThat(context.client.supports(context.request, context.uri),
                       is(HttpClientSpi.SupportLevel.NOT_SUPPORTED));
        }
    }

    @Test
    void ignoresAdvertisementFromExplicitConnection() {
        try (TestContext context = TestContext.create(ClientAltSvcConfig.create())) {
            context.client.responseReceived(context.directResponse(Http2Client.PROTOCOL_ID, true, Status.OK_200));

            assertThat(context.client.supports(context.request, context.uri),
                       is(HttpClientSpi.SupportLevel.NOT_SUPPORTED));
        }
    }

    @Test
    void ignoresAdvertisementFromUnsupportedResponseProtocol() {
        try (TestContext context = TestContext.create(ClientAltSvcConfig.create())) {
            context.client.responseReceived(context.directResponse("h3", false, Status.OK_200));

            assertThat(context.client.supports(context.request, context.uri),
                       is(HttpClientSpi.SupportLevel.NOT_SUPPORTED));
        }
    }

    @ParameterizedTest
    @MethodSource("userConfiguredTls")
    void learnsAdvertisementWithUserConfiguredTls(Tls tls) {
        try (TestContext context = TestContext.create(ClientAltSvcConfig.create(), tls)) {
            context.client.responseReceived(context.directResponse(Http1Client.PROTOCOL_ID, false, Status.OK_200));

            assertThat(context.client.supports(context.request, context.uri),
                       is(HttpClientSpi.SupportLevel.SUPPORTED));
        }
    }

    @Test
    void misdirectedDirectResponseDoesNotInstallAlternative() {
        try (TestContext context = TestContext.create(ClientAltSvcConfig.create())) {
            context.client.responseReceived(context.directResponse(Http1Client.PROTOCOL_ID,
                                                                    false,
                                                                    Status.MISDIRECTED_REQUEST_421));

            assertThat(context.client.supports(context.request, context.uri),
                       is(HttpClientSpi.SupportLevel.NOT_SUPPORTED));
        }
    }

    @Test
    void misdirectedDirectResponseDoesNotReplaceOrClearAlternative() {
        try (TestContext context = TestContext.create(ClientAltSvcConfig.create())) {
            Instant observation = Instant.now();
            context.client.responseReceived(context.directResponse(Http1Client.PROTOCOL_ID,
                                                                    false,
                                                                    Status.OK_200,
                                                                    responseHeaders(),
                                                                    observation));

            context.client.responseReceived(context.directResponse(Http1Client.PROTOCOL_ID,
                                                                    false,
                                                                    Status.MISDIRECTED_REQUEST_421,
                                                                    responseHeaders("h2=\":9443\"; ma=3600"),
                                                                    observation.plusNanos(1)));
            Http2AltSvcCache.Selection selection = context.client.connectionCache()
                    .currentAlternative(context.target, false);
            assertThat(selection, notNullValue());
            assertThat(selection.port(), is(8443));

            context.client.responseReceived(context.directResponse(Http1Client.PROTOCOL_ID,
                                                                    false,
                                                                    Status.MISDIRECTED_REQUEST_421,
                                                                    responseHeaders("clear"),
                                                                    observation.plusNanos(2)));
            assertThat(context.client.connectionCache().currentAlternative(context.target, false),
                       sameInstance(selection));
        }
    }

    @Test
    void misdirectedAlternativeDoesNotReadvertiseItself() {
        try (TestContext context = TestContext.create(ClientAltSvcConfig.create())) {
            context.client.responseReceived(context.directResponse(Http1Client.PROTOCOL_ID, false, Status.OK_200));
            Http2AltSvcCache.Selection selection = context.client.connectionCache()
                    .currentAlternative(context.target, false);
            assertThat(selection, notNullValue());
            context.client.connectionCache().recordAlternativeMisdirected(selection);

            ResolvedClientTarget alternativeTarget = context.target.resolve(selection.host(), selection.port(), 0);
            WebClientProtocolResponse response = WebClientProtocolResponse.createAlternative(
                    alternativeTarget,
                    false,
                    Http2Client.PROTOCOL_ID,
                    Status.MISDIRECTED_REQUEST_421,
                    responseHeaders(),
                    Instant.now(),
                    UriAuthority.create(UriHost.create(selection.host()), selection.port()));

            context.client.responseReceived(response);

            assertThat(context.client.supports(context.request, context.uri),
                       is(HttpClientSpi.SupportLevel.NOT_SUPPORTED));
        }
    }

    private static Stream<String> tcpProtocolIds() {
        return Stream.of(Http1Client.PROTOCOL_ID, Http2Client.PROTOCOL_ID);
    }

    private static Stream<String> disallowedProtocols() {
        return Stream.of("H2", "h3");
    }

    private static Stream<Tls> userConfiguredTls() {
        return Stream.of(Tls.builder().trustAll(true).build(),
                         Tls.builder()
                                 .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_NONE)
                                 .build(),
                         Tls.builder().sslContext(defaultSslContext()).build(),
                         Tls.builder().manager(new TestTlsManager()).build());
    }

    private static SSLContext defaultSslContext() {
        try {
            return SSLContext.getDefault();
        } catch (GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }

    private static ClientResponseHeaders responseHeaders() {
        return responseHeaders("h2=\":8443\"; ma=3600");
    }

    private static ClientResponseHeaders responseHeaders(String value) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.set(HeaderNames.ALT_SVC, value);
        return ClientResponseHeaders.create(headers);
    }

    private static final class TestTlsManager implements TlsManager {
        private final SSLContext sslContext = defaultSslContext();

        @Override
        public void init(TlsConfig tls) {
        }

        @Override
        public SSLContext sslContext() {
            return sslContext;
        }

        @Override
        public Optional<X509KeyManager> keyManager() {
            return Optional.empty();
        }

        @Override
        public Optional<X509TrustManager> trustManager() {
            return Optional.empty();
        }

        @Override
        public String name() {
            return "test";
        }

        @Override
        public String type() {
            return "test";
        }
    }

    private static final class TestContext implements AutoCloseable {
        private final Http2ClientImpl client;
        private final FullClientRequest<?> request;
        private final ClientUri uri;
        private final ConnectionKey connectionKey;
        private final ClientConnectionTarget target;
        private final ResolvedClientTarget resolvedTarget;

        private TestContext(Optional<ClientAltSvcConfig> altSvc) {
            this(altSvc, Tls.builder().build(), "https");
        }

        private TestContext(Optional<ClientAltSvcConfig> altSvc, Tls tls) {
            this(altSvc, tls, "https");
        }

        private TestContext(Optional<ClientAltSvcConfig> altSvc, Tls tls, String scheme) {
            this(altSvc,
                 tls,
                 scheme,
                 Optional.empty(),
                 ClientRequestHeaders.create(WritableHeaders.create()));
        }

        private TestContext(Optional<ClientAltSvcConfig> altSvc,
                            Tls tls,
                            String scheme,
                            Optional<SniConfig> sni,
                            ClientRequestHeaders requestHeaders) {
            Http2ClientConfig.Builder configBuilder = Http2ClientConfig.builder()
                    .shareConnectionCache(false)
                    .dnsResolver((_, _) -> InetAddress.getLoopbackAddress())
                    .tls(tls)
                    .protocolConfig(Http2ClientProtocolConfig.create());
            altSvc.ifPresent(configBuilder::altSvc);
            Http2ClientConfig clientConfig = configBuilder.buildPrototype();
            client = new Http2ClientImpl(mock(WebClient.class), clientConfig);
            uri = ClientUri.create(URI.create(scheme + "://origin.example/resource"));
            request = mock(FullClientRequest.class);
            when(request.address()).thenReturn(Optional.empty());
            when(request.sni()).thenReturn(sni);
            when(request.tls()).thenReturn(tls);
            when(request.proxy()).thenReturn(Proxy.noProxy());
            when(request.headers()).thenReturn(requestHeaders);
            when(request.connection()).thenReturn(Optional.empty());
            when(request.selectedProxyRoute()).thenReturn(Optional.empty());
            connectionKey = Http2ConnectionKeys.create(uri, request, clientConfig, requestHeaders);
            target = ClientConnectionTarget.create(connectionKey, uri, requestHeaders);
            resolvedTarget = target.resolve();
        }

        private static TestContext create() {
            return new TestContext(Optional.empty());
        }

        private static TestContext create(ClientAltSvcConfig altSvc) {
            return new TestContext(Optional.of(altSvc));
        }

        private static TestContext create(ClientAltSvcConfig altSvc, Tls tls) {
            return new TestContext(Optional.of(altSvc), tls);
        }

        private static TestContext createPlain(ClientAltSvcConfig altSvc) {
            return new TestContext(Optional.of(altSvc), Tls.builder().build(), "http");
        }

        private static TestContext createHostHeaderSni(ClientAltSvcConfig altSvc) {
            WritableHeaders<?> headers = WritableHeaders.create();
            headers.set(HeaderNames.HOST, "service.example");
            SniConfig sni = SniConfig.builder()
                    .mode(SniMode.HOST_HEADER)
                    .build();
            return new TestContext(Optional.of(altSvc),
                                   Tls.builder().build(),
                                   "https",
                                   Optional.of(sni),
                                   ClientRequestHeaders.create(headers));
        }

        private WebClientProtocolResponse directResponse(String protocolId,
                                                         boolean explicitConnection,
                                                         Status status) {
            return directResponse(protocolId,
                                  explicitConnection,
                                  status,
                                  responseHeaders(),
                                  Instant.now());
        }

        private WebClientProtocolResponse directResponse(String protocolId,
                                                         boolean explicitConnection,
                                                         Status status,
                                                         ClientResponseHeaders headers,
                                                         Instant receivedAt) {
            return WebClientProtocolResponse.create(resolvedTarget,
                                                    explicitConnection,
                                                    protocolId,
                                                    status,
                                                    headers,
                                                    receivedAt);
        }

        @Override
        public void close() {
            client.closeResource();
        }
    }
}
