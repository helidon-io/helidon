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
import java.net.UnixDomainSocketAddress;
import java.util.List;

import io.helidon.common.tls.Tls;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.ClientRequestOrigin;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.Proxy;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class Http2CallChainBaseTest {
    @Test
    void authorityOverridesHostBeforeKeying() {
        ClientRequestHeaders headers = emptyHeaders();
        headers.set(HeaderValues.create(HeaderNames.HOST, "host.example:443"));
        headers.set(HeaderValues.create(Http2Headers.AUTHORITY_NAME, "authority.example:9443"));

        Http2CallChainBase.alignHostHeader(uri(), headers);

        assertThat(headers.first(HeaderNames.HOST).orElseThrow(), is("authority.example:9443"));
        assertThat(headers.contains(Http2Headers.AUTHORITY_NAME), is(false));
    }

    @Test
    void normalizationPreservesHeaderMetadataWithoutChangingConfiguredHeaders() {
        ClientRequestHeaders headers = emptyHeaders();
        headers.set(HeaderValues.create(HeaderNames.HOST, "host.example:443"));
        headers.set(HeaderValues.create(Http2Headers.AUTHORITY_NAME, false, true, "authority.example:9443"));
        headers.set(HeaderValues.create(HeaderNames.ACCEPT, true, false, "text/plain"));

        ClientRequestHeaders normalized = Http2RequestHeaders.normalizedRequestHeaders(headers);

        assertThat(normalized.contains(Http2Headers.AUTHORITY_NAME), is(false));
        assertThat(normalized.get(HeaderNames.HOST).get(), is("authority.example:9443"));
        assertThat(normalized.get(HeaderNames.HOST).changing(), is(false));
        assertThat(normalized.get(HeaderNames.HOST).sensitive(), is(true));
        assertThat(headers.get(Http2Headers.AUTHORITY_NAME).get(), is("authority.example:9443"));
        assertThat(headers.get(HeaderNames.HOST).get(), is("host.example:443"));
        normalized.add(HeaderNames.ACCEPT, "text/html");
        assertThat(headers.get(HeaderNames.ACCEPT).allValues(), is(List.of("text/plain")));
        headers.add(HeaderNames.ACCEPT, "application/json");
        assertThat(normalized.get(HeaderNames.ACCEPT).allValues(), is(List.of("text/plain", "text/html")));
        assertThat(Http2RequestHeaders.normalizedRequestHeaders(normalized), sameInstance(normalized));
    }

    @Test
    void normalizationReturnsOriginalHeadersWhenAuthorityIsAbsent() {
        ClientRequestHeaders headers = emptyHeaders();
        headers.set(HeaderValues.create(HeaderNames.HOST, "host.example:443"));

        assertThat(Http2RequestHeaders.normalizedRequestHeaders(headers), sameInstance(headers));
    }

    @Test
    void duplicateAuthorityValuesAreRejectedBeforeKeying() {
        ClientRequestHeaders headers = emptyHeaders();
        headers.set(HeaderValues.create(HeaderNames.HOST, "host.example:443"));
        headers.set(HeaderValues.create(Http2Headers.AUTHORITY_NAME, "one.example", "two.example"));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                                        () -> Http2CallChainBase.alignHostHeader(uri(), headers));

        assertThat(failure.getMessage(), is("Request :authority must contain exactly one value"));
        assertThat(headers.get(HeaderNames.HOST).get(), is("host.example:443"));
        assertThat(headers.get(Http2Headers.AUTHORITY_NAME).allValues(), is(List.of("one.example", "two.example")));
    }

    @Test
    void protocolHandoffRetainsBindingsForNormalizedAuthority() {
        Http2ClientImpl client = (Http2ClientImpl) Http2Client.builder()
                .servicesDiscoverServices(false)
                .shareConnectionCache(false)
                .baseUri(uri().toUri())
                .build();
        try {
            FullClientRequest<?> source = (FullClientRequest<?>) client.get()
                    .header(Http2Headers.AUTHORITY_NAME, "authority.example:9443");
            ClientRequestOrigin origin = ClientRequestOrigin.create(
                    uri(),
                    Http2RequestHeaders.normalizedRequestHeaders(source.headers()));
            ClientConnection connection = mock(ClientConnection.class);
            UnixDomainSocketAddress address = UnixDomainSocketAddress.of("authority.sock");
            source.inheritedConnection(connection, origin);
            source.inheritedAddress(address, origin);

            FullClientRequest<?> target = (FullClientRequest<?>) client.clientRequest(source, uri());

            assertThat(target.connection().orElseThrow(), sameInstance(connection));
            assertThat(target.inheritedConnectionOrigin().orElseThrow(), is(origin));
            assertThat(target.address().orElseThrow(), is(address));
            assertThat(target.inheritedAddressOrigin().orElseThrow(), is(origin));
            assertThat(target.headers().get(Http2Headers.AUTHORITY_NAME).get(), is("authority.example:9443"));
            assertThat(target.headers().contains(HeaderNames.HOST), is(false));
            assertThat(source.headers().contains(HeaderNames.HOST), is(false));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void hostIsPreservedWhenAuthorityIsAbsent() {
        ClientRequestHeaders headers = emptyHeaders();
        headers.set(HeaderValues.create(HeaderNames.HOST, "host.example:443"));

        Http2CallChainBase.alignHostHeader(uri(), headers);

        assertThat(headers.first(HeaderNames.HOST).orElseThrow(), is("host.example:443"));
    }

    @Test
    void uriAuthorityIsUsedWhenHeadersAreAbsent() {
        ClientRequestHeaders headers = emptyHeaders();

        Http2CallChainBase.alignHostHeader(uri(), headers);

        assertThat(headers.first(HeaderNames.HOST).orElseThrow(), is("service.example:8443"));
    }

    @Test
    void ordinaryConnectOmitsSchemeAndPath() {
        ClientRequestHeaders headers = emptyHeaders();
        ClientUri uri = uri();
        Http2CallChainBase.alignHostHeader(uri, headers);

        Http2Headers http2Headers = Http2CallChainBase.prepareHeaders(Method.CONNECT, headers, uri);

        http2Headers.validateRequest();
        assertThat(http2Headers.method(), is(Method.CONNECT));
        assertThat(http2Headers.authority(), is("service.example:8443"));
        assertThat(http2Headers.scheme(), is(nullValue()));
        assertThat(http2Headers.path(), is(nullValue()));
    }

    @Test
    void ordinaryRequestKeepsSchemeAndPath() {
        ClientRequestHeaders headers = emptyHeaders();
        ClientUri uri = uri();
        Http2CallChainBase.alignHostHeader(uri, headers);

        Http2Headers http2Headers = Http2CallChainBase.prepareHeaders(Method.GET, headers, uri);

        http2Headers.validateRequest();
        assertThat(http2Headers.scheme(), is("https"));
        assertThat(http2Headers.path(), is("/path"));
    }

    @Test
    void preparingHttp2HeadersDoesNotMutateHost() {
        ClientRequestHeaders headers = emptyHeaders();
        headers.set(HeaderValues.create(HeaderNames.HOST, "virtual.example:8443"));

        Http2Headers prepared = Http2CallChainBase.prepareHeaders(
                Method.GET,
                headers,
                ClientUri.create(URI.create("https://service.example:8443/path?query=value#client-fragment")));

        assertThat(headers.first(HeaderNames.HOST).orElseThrow(), is("virtual.example:8443"));
        assertThat(prepared.authority(), is("virtual.example:8443"));
        assertThat(prepared.path(), is("/path?query=value"));
    }

    @Test
    void failedPostBodyRedirectCancelsAndClosesCurrentStream() {
        Http2ClientStream stream = mock(Http2ClientStream.class);
        IllegalStateException failure = new IllegalStateException("redirect cannot be replayed");
        IllegalStateException cancelFailure = new IllegalStateException("cancel failed");
        IllegalStateException closeFailure = new IllegalStateException("close failed");
        doThrow(cancelFailure).when(stream).cancel();
        doThrow(closeFailure).when(stream).close();

        Http2CallOutputStreamChain.closeRedirectStream(stream, failure);

        verify(stream).cancel();
        verify(stream).close();
        assertThat(List.of(failure.getSuppressed()), is(List.of(cancelFailure, closeFailure)));
    }

    @Test
    void entityWriterErrorCancelsAndClosesAllocatedStreamWithoutMaskingPrimaryFailure() {
        Http2ClientStream stream = mock(Http2ClientStream.class);
        AssertionError failure = new AssertionError("writer failed");
        AssertionError cancelFailure = new AssertionError("cancel failed");
        AssertionError closeFailure = new AssertionError("close failed");
        doThrow(cancelFailure).when(stream).cancel();
        doThrow(closeFailure).when(stream).close();
        Http2ConnectionAttemptResult result = new Http2ConnectionAttemptResult(
                Http2ConnectionAttemptResult.Result.HTTP_2,
                stream,
                null,
                null,
                connectionTarget());

        Http2CallChainBase.closeFailedStream(result, failure);

        verify(stream).cancel();
        verify(stream).close();
        assertThat(List.of(failure.getSuppressed()), is(List.of(cancelFailure, closeFailure)));
    }

    private static ClientRequestHeaders emptyHeaders() {
        return ClientRequestHeaders.create(WritableHeaders.create());
    }

    private static ClientUri uri() {
        return ClientUri.create(URI.create("https://service.example:8443/path"));
    }

    private static ClientConnectionTarget connectionTarget() {
        ConnectionKey connectionKey = ConnectionKey.create("http",
                                                           "service.example",
                                                           8443,
                                                           Tls.builder().enabled(false).build(),
                                                           (_, _) -> InetAddress.getLoopbackAddress(),
                                                           DnsAddressLookup.IPV4,
                                                           Proxy.noProxy());
        return ClientConnectionTarget.create(connectionKey, "http");
    }
}
