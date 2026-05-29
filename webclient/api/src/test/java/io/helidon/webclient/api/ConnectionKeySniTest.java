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
import java.util.List;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;

import io.helidon.common.tls.Tls;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.spi.DnsResolver;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectionKeySniTest {
    private static final Tls TLS = Tls.builder().build();
    private static final Tls NO_TLS = Tls.builder().enabled(false).build();
    private static final DnsResolver DNS_RESOLVER = DefaultDnsResolver.create();
    private static final DnsAddressLookup DNS_LOOKUP = DnsAddressLookup.defaultLookup();
    private static final Proxy NO_PROXY = Proxy.noProxy();

    @Test
    void uriHostModeUsesNormalizedDnsHost() {
        ConnectionKey key = key("https://Example.COM:9443/path",
                                SniConfig.create(),
                                TLS,
                                emptyHeaders());

        assertThat(key.tlsPeerHost(), is("example.com"));
        assertThat(key.tlsPeerPort(), is(9443));
        assertThat(serverName(key), is("example.com"));
    }

    @Test
    void uriHostModeSuppressesSniForIpLiteral() {
        ConnectionKey key = key("https://127.0.0.1:9443/path",
                                SniConfig.create(),
                                TLS,
                                emptyHeaders());

        assertThat(key.tlsPeerHost(), is("127.0.0.1"));
        assertThat(key.serverNamesOverride(), is(empty()));
    }

    @Test
    void uriHostModeSuppressesSniForBracketedIpv6Literal() {
        ConnectionKey key = key("https://[::1]:9443/path",
                                SniConfig.create(),
                                TLS,
                                emptyHeaders());

        assertThat(key.tlsPeerHost(), is("::1"));
        assertThat(key.serverNamesOverride(), is(empty()));
    }

    @Test
    void defaultModePreservesTlsSocketDefaults() {
        ConnectionKey key = key("https://service.example:443", null, TLS, emptyHeaders());

        assertThat(key.tlsPeerHost(), is("service.example"));
        assertThat(key.serverNamesOverride(), is(nullValue()));
    }

    @Test
    void defaultModeDoesNotNormalizeBracketedIpv6Host() {
        ConnectionKey key = key("https://[::1]:443", null, TLS, emptyHeaders());

        assertThat(key.tlsPeerHost(), is("[::1]"));
        assertThat(key.serverNamesOverride(), is(nullValue()));
    }

    @Test
    void hostHeaderModeUsesFinalAuthority() {
        ClientRequestHeaders headers = emptyHeaders();
        headers.set(HeaderNames.HOST, "Backend.EXAMPLE:9444");
        SniConfig hostHeaderSni = SniConfig.builder()
                .mode(SniMode.HOST_HEADER)
                .build();

        ConnectionKey key = key("https://service.example:443", hostHeaderSni, TLS, headers);
        ConnectionKey uriHostKey = key("https://service.example:443",
                                       SniConfig.create(),
                                       TLS,
                                       headers);

        assertThat(key, is(not(uriHostKey)));
        assertThat(key.tlsPeerHost(), is("backend.example"));
        assertThat(key.tlsPeerPort(), is(9444));
        assertThat(serverName(key), is("backend.example"));
    }

    @Test
    void hostHeaderModeSuppressesSniForIpLiteral() {
        ClientRequestHeaders headers = emptyHeaders();
        headers.set(HeaderNames.HOST, "127.0.0.1:9444");
        SniConfig hostHeaderSni = SniConfig.builder()
                .mode(SniMode.HOST_HEADER)
                .build();

        ConnectionKey key = key("https://service.example:443", hostHeaderSni, TLS, headers);

        assertThat(key.tlsPeerHost(), is("127.0.0.1"));
        assertThat(key.tlsPeerPort(), is(9444));
        assertThat(key.serverNamesOverride(), is(empty()));
    }

    @Test
    void hostHeaderModeSuppressesSniForBracketedIpv6Literal() {
        ClientRequestHeaders headers = emptyHeaders();
        headers.set(HeaderNames.HOST, "[::1]:9444");
        SniConfig hostHeaderSni = SniConfig.builder()
                .mode(SniMode.HOST_HEADER)
                .build();

        ConnectionKey key = key("https://service.example:443", hostHeaderSni, TLS, headers);

        assertThat(key.tlsPeerHost(), is("::1"));
        assertThat(key.tlsPeerPort(), is(9444));
        assertThat(key.serverNamesOverride(), is(empty()));
    }

    @Test
    void hostHeaderModeFallsBackToUriAuthorityWhenHostIsAbsent() {
        ClientRequestHeaders headers = emptyHeaders();
        SniConfig hostHeaderSni = SniConfig.builder()
                .mode(SniMode.HOST_HEADER)
                .build();

        ConnectionKey key = key("https://Service.EXAMPLE:9445", hostHeaderSni, TLS, headers);

        assertThat(key.tlsPeerHost(), is("service.example"));
        assertThat(key.tlsPeerPort(), is(9445));
        assertThat(serverName(key), is("service.example"));
    }

    @Test
    void hostHeaderModeRejectsMultipleHostValues() {
        ClientRequestHeaders headers = emptyHeaders();
        headers.add(HeaderValues.create(HeaderNames.HOST, "first.example", "second.example"));
        SniConfig hostHeaderSni = SniConfig.builder()
                .mode(SniMode.HOST_HEADER)
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                         () -> key("https://service.example:443",
                                                                   hostHeaderSni,
                                                                   TLS,
                                                                   headers));

        assertThat(exception.getMessage(), containsString("Request Host header must be single-valued"));
    }

    @Test
    void equivalentDnsSelectionsShareConnectionKey() {
        ClientRequestHeaders headers = emptyHeaders();
        headers.set(HeaderNames.HOST, "service.example:443");
        ConnectionKey uriHost = key("https://service.example:443",
                                    SniConfig.create(),
                                    TLS,
                                    headers);
        ConnectionKey hostHeader = key("https://service.example:443",
                                       SniConfig.builder().mode(SniMode.HOST_HEADER).build(),
                                       TLS,
                                       headers);
        ConnectionKey explicit = key("https://service.example:443",
                                     explicit("service.example"),
                                     TLS,
                                     headers);

        assertThat(hostHeader, is(uriHost));
        assertThat(explicit, is(uriHost));
    }

    @Test
    void disabledModeClearsServerNames() {
        SniConfig disabled = SniConfig.builder()
                .mode(SniMode.DISABLED)
                .build();

        ConnectionKey key = key("https://service.example:443", disabled, TLS, emptyHeaders());
        ConnectionKey uriHostKey = key("https://service.example:443",
                                       SniConfig.create(),
                                       TLS,
                                       emptyHeaders());

        assertThat(key, is(not(uriHostKey)));
        assertThat(key.tlsPeerHost(), is("service.example"));
        assertThat(key.serverNamesOverride(), is(empty()));
    }

    @Test
    void explicitModeSuppressesSniForIpLiteral() {
        ConnectionKey key = key("https://service.example:9443",
                                explicit("127.0.0.1"),
                                TLS,
                                emptyHeaders());

        assertThat(key.tlsPeerHost(), is("127.0.0.1"));
        assertThat(key.tlsPeerPort(), is(9443));
        assertThat(key.serverNamesOverride(), is(empty()));
    }

    @Test
    void explicitModeSuppressesSniForBracketedIpv6Literal() {
        ConnectionKey key = key("https://service.example:9443",
                                explicit("[::1]"),
                                TLS,
                                emptyHeaders());

        assertThat(key.tlsPeerHost(), is("::1"));
        assertThat(key.tlsPeerPort(), is(9443));
        assertThat(key.serverNamesOverride(), is(empty()));
    }

    @Test
    void rawTlsServerNamesRemainConfiguredWhenNoSniConfigIsPresent() {
        SSLParameters parameters = new SSLParameters();
        parameters.setServerNames(List.of(new SNIHostName("configured.example")));
        Tls tls = Tls.builder()
                .sslParameters(parameters)
                .build();

        ConnectionKey key = key("https://service.example:443", null, tls, emptyHeaders());

        assertThat(key.tlsPeerHost(), is("service.example"));
        assertThat(key.serverNamesOverride(), is(nullValue()));
    }

    @Test
    void legacyFactoryPreservesTlsSocketDefaults() {
        ConnectionKey key = ConnectionKey.create("https", "service.example", 443, TLS, DNS_RESOLVER, DNS_LOOKUP, NO_PROXY);

        assertThat(key.tlsPeerHost(), is("service.example"));
        assertThat(key.serverNamesOverride(), is(nullValue()));
    }

    @Test
    void firstClassSniOverridesRawTlsServerNames() {
        SSLParameters parameters = new SSLParameters();
        parameters.setServerNames(List.of(new SNIHostName("configured.example")));
        Tls tls = Tls.builder()
                .sslParameters(parameters)
                .build();

        ConnectionKey key = key("https://service.example:443",
                                explicit("explicit.example"),
                                tls,
                                emptyHeaders());

        assertThat(key.tlsPeerHost(), is("explicit.example"));
        assertThat(serverName(key), is("explicit.example"));
    }

    @Test
    void cleartextConnectionsIgnoreSniConfigForCacheKey() {
        ConnectionKey explicit = key("http://service.example:8080",
                                     explicit("explicit.example"),
                                     NO_TLS,
                                     emptyHeaders());
        ConnectionKey hostHeader = key("http://service.example:8080",
                                       SniConfig.builder().mode(SniMode.HOST_HEADER).build(),
                                       NO_TLS,
                                       emptyHeaders());

        assertThat(explicit, is(hostHeader));
        assertThat(explicit.tlsPeerHost(), is("service.example"));
        assertThat(explicit.serverNamesOverride(), is(empty()));
    }

    @Test
    void cleartextConnectionsDoNotNormalizeSniHost() {
        ConnectionKey key = key("http://[::1]:8080",
                                explicit("explicit.example"),
                                NO_TLS,
                                emptyHeaders());

        assertThat(key.tlsPeerHost(), is("[::1]"));
        assertThat(key.serverNamesOverride(), is(empty()));
    }

    private static ConnectionKey key(String uri,
                                     SniConfig sni,
                                     Tls tls,
                                     ClientRequestHeaders headers) {
        ClientUri clientUri = ClientUri.create(URI.create(uri));
        if (sni == null) {
            return ConnectionKey.create(clientUri,
                                        tls,
                                        DNS_RESOLVER,
                                        DNS_LOOKUP,
                                        NO_PROXY);
        }
        return ConnectionKey.create(clientUri,
                                    sni,
                                    tls,
                                    DNS_RESOLVER,
                                    DNS_LOOKUP,
                                    NO_PROXY,
                                    headers);
    }

    private static ClientRequestHeaders emptyHeaders() {
        return ClientRequestHeaders.create(WritableHeaders.create());
    }

    private static SniConfig explicit(String host) {
        return SniConfig.builder()
                .mode(SniMode.EXPLICIT)
                .host(host)
                .build();
    }

    private static String serverName(ConnectionKey key) {
        List<SNIServerName> serverNames = key.serverNamesOverride();
        assertThat(serverNames.size(), is(1));
        return ((SNIHostName) serverNames.get(0)).getAsciiName();
    }
}
