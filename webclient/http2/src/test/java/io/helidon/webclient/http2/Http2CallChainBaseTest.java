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
import java.util.List;

import io.helidon.common.tls.Tls;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.api.Proxy;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
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
