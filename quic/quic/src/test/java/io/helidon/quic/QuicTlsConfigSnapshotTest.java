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

package io.helidon.quic;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;

import javax.net.ssl.SSLParameters;

import io.helidon.common.tls.Tls;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicTlsConfigSnapshotTest {
    @Test
    void mapsTlsAndQuicConfiguration() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[] {"TLSv1.3"});
        sslParameters.setApplicationProtocols(new String[] {"h3"});
        Tls tls = Tls.builder()
                .privateKey(QuicTlsRfc8448Vectors.rsaPrivateKey())
                .privateKeyCertChain(List.of(QuicTlsRfc8448Vectors.rsaCertificate()))
                .trust(List.of(QuicTlsRfc8448Vectors.rsaCertificate()))
                .secureRandom(secureRandom)
                .sessionCacheSize(37)
                .sessionTimeout(Duration.ofMinutes(12))
                .sslParameters(sslParameters)
                .build();
        QuicConfig quicConfig = QuicConfig.builder()
                .maxHandshakeMessageSize(8192)
                .buildPrototype();
        QuicRuntimeConfig defaults = QuicRuntimeConfig.create(quicConfig);
        QuicRuntimeConfig runtimeConfig = new QuicRuntimeConfig(quicConfig,
                                                                defaults.endpoint(),
                                                                defaults.recovery(),
                                                                defaults.transportParameters(),
                                                                new QuicAeadLimits.Confidentiality(4096, 2048));

        QuicTlsConfigSnapshot snapshot = QuicTlsConfigSnapshot.create(tls, runtimeConfig);

        assertThat(snapshot.generation(), is(tls.generation()));
        assertThat(snapshot.sslContext(), sameInstance(tls.sslContext()));
        assertThat(snapshot.keyManager().orElseThrow(), sameInstance(tls.keyManager().orElseThrow()));
        assertThat(snapshot.trustManager().orElseThrow(), sameInstance(tls.trustManager().orElseThrow()));
        assertThat(snapshot.secureRandom(), sameInstance(secureRandom));
        assertThat(snapshot.sessionCacheSize(), is(37));
        assertThat(snapshot.sessionTimeout(), is(Duration.ofMinutes(12)));
        assertThat(snapshot.maxHandshakeMessageSize(), is(8192));
        assertThat(snapshot.aesGcmConfidentialityLimit(), is(4096L));
        assertThat(snapshot.chacha20Poly1305ConfidentialityLimit(), is(2048L));
        assertThat(snapshot.sslParameters().getProtocols(), arrayContaining("TLSv1.3"));
        assertThat(snapshot.sslParameters().getApplicationProtocols(), arrayContaining("h3"));
    }

    @Test
    void copiesReturnedSslParameters() {
        SSLParameters configured = new SSLParameters();
        configured.setProtocols(new String[] {"TLSv1.3"});
        configured.setApplicationProtocols(new String[] {"h3"});
        Tls tls = Tls.builder()
                .trustAll(true)
                .sslParameters(configured)
                .build();

        QuicTlsConfigSnapshot snapshot = QuicTlsConfigSnapshot.create(tls,
                                                                      QuicRuntimeConfig.create(QuicConfig.create()));
        configured.setProtocols(new String[] {"TLSv1.2"});
        SSLParameters returned = snapshot.sslParameters();
        returned.setProtocols(new String[] {"TLSv1.1"});

        assertThat(snapshot.sslParameters().getProtocols(), arrayContaining("TLSv1.3"));
        assertThat(snapshot.sslParameters().getApplicationProtocols(), arrayContaining("h3"));
    }

    @Test
    void resolvesDefaultSecureRandom() {
        Tls tls = Tls.builder().trustAll(true).build();

        QuicTlsConfigSnapshot snapshot = QuicTlsConfigSnapshot.create(tls,
                                                                      QuicRuntimeConfig.create(QuicConfig.create()));

        assertThat(snapshot.secureRandom(), notNullValue());
    }

    @Test
    void rejectsSecureRandomProviderWithoutAlgorithm() {
        Tls tls = QuicTlsTestSupport.tlsBuilder(null, null)
                .secureRandomProvider("SUN")
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> QuicTlsConfigSnapshot.create(tls, QuicRuntimeConfig.create(QuicConfig.create())));

        assertThat(exception.getMessage(),
                   is("Invalid configuration of secure random. Provider is configured to SUN, but algorithm is not specified"));
    }

    @Test
    void rejectsDisabledTls() {
        Tls tls = Tls.builder().enabled(false).build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> QuicTlsConfigSnapshot.create(tls, QuicRuntimeConfig.create(QuicConfig.create())));

        assertThat(exception.getMessage(), is("Cannot construct a QUIC TLS context with disabled TLS"));
    }
}
