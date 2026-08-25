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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.Value;
import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsConfig;
import io.helidon.common.tls.TlsManager;
import io.helidon.common.uri.UriAuthority;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebClientProtocolResponseTest {
    private static final Instant RECEIVED_AT = Instant.parse("2026-08-23T00:01:30Z");

    @Test
    void retainsExactContextAndCopiesHeaders() {
        ResolvedClientTarget target = resolvedTarget("https://origin.example/path", null, null);
        WritableHeaders<?> mutableHeaders = WritableHeaders.create();
        mutableHeaders.add(HeaderValues.create(HeaderNames.ALT_SVC, "h3=\":443\""));
        ClientResponseHeaders responseHeaders = ClientResponseHeaders.create(mutableHeaders);

        WebClientProtocolResponse response = WebClientProtocolResponse.create(target,
                                                                              false,
                                                                              "h2",
                                                                              Status.OK_200,
                                                                              responseHeaders,
                                                                              RECEIVED_AT);
        mutableHeaders.set(HeaderValues.create(HeaderNames.ALT_SVC, "clear"));

        assertThat(response.target(), sameInstance(target));
        assertThat(response.explicitConnection(), is(false));
        assertThat(response.protocolId(), is("h2"));
        assertThat(response.status(), sameInstance(Status.OK_200));
        assertThat(response.headers().first(HeaderNames.ALT_SVC).orElseThrow(), is("h3=\":443\""));
        assertThat(response.receivedAt(), is(RECEIVED_AT));
        assertThat(response.alternativeAuthority().isEmpty(), is(true));
        assertThat(response.secure(), is(true));
    }

    @Test
    void snapshotsAllHeadersAndTheirState() {
        HeaderName customName = HeaderNames.create("X-Custom-Response");
        HeaderName customLookup = HeaderNames.create("x-CUSTOM-response");
        WritableHeaders<?> mutableHeaders = WritableHeaders.create();
        mutableHeaders.add(HeaderValues.create(HeaderNames.ACCEPT,
                                               "text/plain;q=0.5, application/json"));
        mutableHeaders.add(HeaderValues.create(HeaderNames.CACHE_CONTROL,
                                               true,
                                               false,
                                               "no-cache"));
        mutableHeaders.add(HeaderValues.create(customName, false, true, "first"));
        mutableHeaders.add(HeaderValues.create(customName, false, true, "second"));
        ClientResponseHeaders original = ClientResponseHeaders.create(mutableHeaders);
        List<String> originalIteration = original.stream().map(Header::name).toList();

        ClientResponseHeaders snapshot = WebClientProtocolResponse.create(
                        resolvedTarget("https://origin.example/path", null, null),
                        false,
                        "h2",
                        Status.OK_200,
                        original,
                        RECEIVED_AT)
                .headers();

        mutableHeaders.remove(HeaderNames.ACCEPT);
        mutableHeaders.set(HeaderValues.create(HeaderNames.CACHE_CONTROL, "public"));
        mutableHeaders.add(HeaderValues.create(customName, "third"));

        assertThat(snapshot.size(), is(3));
        assertThat(snapshot.stream().map(Header::name).toList(), is(originalIteration));
        assertThat(snapshot.contains(HeaderNames.ACCEPT), is(true));
        assertThat(snapshot.get(HeaderNames.CACHE_CONTROL).get(), is("no-cache"));
        assertThat(snapshot.get(HeaderNames.CACHE_CONTROL).changing(), is(true));
        assertThat(snapshot.get(HeaderNames.CACHE_CONTROL).sensitive(), is(false));

        Header custom = snapshot.get(customLookup);
        assertThat(custom.name(), is("X-Custom-Response"));
        assertThat(custom.headerName().defaultCase(), is("X-Custom-Response"));
        assertThat(custom.get(), is("first"));
        assertThat(custom.allValues(), contains("first", "second"));
        assertThat(custom.values(), is("first, second"));
        assertThat(snapshot.value(customLookup).orElseThrow(), is("first,second"));
        assertThat(custom.changing(), is(false));
        assertThat(custom.sensitive(), is(true));
        assertThat(snapshot.contains(HeaderValues.create(customLookup, "first", "second")), is(true));
        assertThat(snapshot.contains(HeaderValues.create(customLookup, "second", "first")), is(false));

        Iterator<Header> exhausted = snapshot.iterator();
        exhausted.forEachRemaining(_ -> { });
        assertThrows(NoSuchElementException.class, exhausted::next);

        List<HttpMediaType> acceptedTypes = snapshot.acceptedTypes();
        assertThat(acceptedTypes.stream().map(HttpMediaType::text).toList(),
                   contains("application/json", "text/plain; q=0.5"));
        assertThrows(UnsupportedOperationException.class, () -> custom.allValues().add("fourth"));
        assertThrows(UnsupportedOperationException.class,
                     () -> snapshot.all(customLookup, List::of).add("fourth"));
        assertThrows(UnsupportedOperationException.class,
                     () -> acceptedTypes.add(HttpMediaType.create("application/xml")));
    }

    @Test
    void snapshotsSingleValueWithoutReadingItsValueList() {
        HeaderName name = HeaderNames.create("X-Single-Value");
        Header source = new ThrowingAllValuesHeader(HeaderValues.create(name, true, true, "single"));
        WritableHeaders<?> mutableHeaders = WritableHeaders.create();
        mutableHeaders.set(source);

        Header snapshot = WebClientProtocolResponse.create(
                        resolvedTarget("https://origin.example/path", null, null),
                        false,
                        "h2",
                        Status.OK_200,
                        ClientResponseHeaders.create(mutableHeaders),
                        RECEIVED_AT)
                .headers()
                .get(name);

        assertThat(snapshot.get(), is("single"));
        assertThat(snapshot.allValues(), contains("single"));
        assertThat(snapshot.valueCount(), is(1));
        assertThat(snapshot.changing(), is(true));
        assertThat(snapshot.sensitive(), is(true));
    }

    @Test
    void missingHeaderUsesTheExpectedContract() {
        ClientResponseHeaders headers = response(resolvedTarget("https://origin.example/path", null, null)).headers();
        HeaderName missing = HeaderNames.create("X-Missing");

        assertThat(headers.contains(missing), is(false));
        assertThat(headers.all(missing, () -> List.of("default")), contains("default"));
        NoSuchElementException exception = assertThrows(NoSuchElementException.class, () -> headers.get(missing));
        assertThat(exception.getMessage(), is("Header " + missing + " is not present in these headers"));
    }

    @Test
    void carriesTheAlternativeAuthorityUsedForTheRequest() {
        ResolvedClientTarget target = resolvedTarget("https://origin.example/path", null, null);
        UriAuthority alternative = UriAuthority.create("alt.example:8443");

        WebClientProtocolResponse response = WebClientProtocolResponse.createAlternative(
                target,
                true,
                "h3",
                Status.OK_200,
                ClientResponseHeaders.create(WritableHeaders.create()),
                RECEIVED_AT,
                alternative);

        assertThat(response.alternativeAuthority().orElseThrow(), sameInstance(alternative));
        assertThat(response.explicitConnection(), is(true));
    }

    @Test
    void secureUsesHttpsAndConfiguredTlsOnly() {
        ResolvedClientTarget differentOrigin = resolvedTarget("https://route.example/path", "origin.example", null);
        ResolvedClientTarget staleTls = resolvedTarget("https://origin.example/path", null, 1L);
        ResolvedClientTarget clearText = resolvedTarget("http://origin.example/path", null, null);
        Tls disabledTls = Tls.builder().enabled(false).build();

        assertThat(response(differentOrigin).secure(), is(true));
        assertThat(response(staleTls).secure(), is(true));
        assertThat(response(clearText).secure(), is(false));
        assertThat(response(resolvedTarget("https://origin.example/path", disabledTls)).secure(), is(false));
    }

    @Test
    void secureHonorsUserConfiguredTlsPolicy() {
        Tls trustAll = Tls.builder().trustAll(true).build();
        Tls endpointIdentificationDisabled = Tls.builder()
                .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_NONE)
                .build();
        Tls explicitContext = Tls.builder()
                .sslContext(defaultSslContext())
                .build();
        Tls customManager = Tls.builder()
                .manager(new TestTlsManager())
                .build();

        assertThat(response(resolvedTarget("https://origin.example/path", trustAll)).secure(), is(true));
        assertThat(response(resolvedTarget("https://origin.example/path", endpointIdentificationDisabled)).secure(),
                   is(true));
        assertThat(response(resolvedTarget("https://origin.example/path", explicitContext)).secure(), is(true));
        assertThat(response(resolvedTarget("https://origin.example/path", customManager)).secure(), is(true));
    }

    private static WebClientProtocolResponse response(ResolvedClientTarget target) {
        return WebClientProtocolResponse.create(target,
                                                false,
                                                "h2",
                                                Status.OK_200,
                                                ClientResponseHeaders.create(WritableHeaders.create()),
                                                RECEIVED_AT);
    }

    private static ResolvedClientTarget resolvedTarget(String uriText,
                                                       String originHost,
                                                       Long tlsGeneration) {
        ClientUri uri = ClientUri.create(URI.create(uriText));
        Tls tls = Tls.builder().enabled("https".equals(uri.scheme())).build();
        return resolvedTarget(uri, tls, originHost, tlsGeneration);
    }

    private static ResolvedClientTarget resolvedTarget(String uriText, Tls tls) {
        return resolvedTarget(ClientUri.create(URI.create(uriText)), tls, null, null);
    }

    private static ResolvedClientTarget resolvedTarget(ClientUri uri,
                                                       Tls tls,
                                                       String originHost,
                                                       Long tlsGeneration) {
        ConnectionKey connectionKey = ConnectionKey.create(uri,
                                                           tls,
                                                           (_, _) -> InetAddress.getLoopbackAddress(),
                                                           DnsAddressLookup.IPV4,
                                                           Proxy.noProxy());
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        if (originHost != null) {
            headers.set(HeaderNames.HOST, originHost);
        }
        ClientConnectionTarget logicalTarget = ClientConnectionTarget.create(connectionKey, uri, headers);
        if (tlsGeneration != null) {
            logicalTarget = ClientConnectionTarget.create(connectionKey,
                                                          logicalTarget.scheme(),
                                                          logicalTarget.originAuthority(),
                                                          logicalTarget.proxyRoute(),
                                                          tlsGeneration);
        }
        return ResolvedClientTarget.direct(logicalTarget,
                                           logicalTarget.originAuthority(),
                                           new InetSocketAddress(InetAddress.getLoopbackAddress(), uri.port()),
                                           0);
    }

    private static SSLContext defaultSslContext() {
        try {
            return SSLContext.getDefault();
        } catch (GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }

    private static final class ThrowingAllValuesHeader implements Header {
        private final Header delegate;

        private ThrowingAllValuesHeader(Header delegate) {
            this.delegate = delegate;
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public HeaderName headerName() {
            return delegate.headerName();
        }

        @Override
        public String get() {
            return delegate.get();
        }

        @Override
        public List<String> allValues() {
            throw new AssertionError("Single-value snapshot must not read allValues()");
        }

        @Override
        public int valueCount() {
            return delegate.valueCount();
        }

        @Override
        public boolean sensitive() {
            return delegate.sensitive();
        }

        @Override
        public boolean changing() {
            return delegate.changing();
        }

        @Override
        public <N> Value<N> as(Class<N> type) {
            return delegate.as(type);
        }

        @Override
        public <N> Value<N> as(GenericType<N> type) {
            return delegate.as(type);
        }

        @Override
        public <N> Value<N> as(Function<? super String, ? extends N> mapper) {
            return delegate.as(mapper);
        }

        @Override
        public Optional<String> asOptional() {
            return delegate.asOptional();
        }

        @Override
        public Value<Boolean> asBoolean() {
            return delegate.asBoolean();
        }

        @Override
        public Value<String> asString() {
            return delegate.asString();
        }

        @Override
        public Value<Integer> asInt() {
            return delegate.asInt();
        }

        @Override
        public Value<Long> asLong() {
            return delegate.asLong();
        }

        @Override
        public Value<Double> asDouble() {
            return delegate.asDouble();
        }
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
}
