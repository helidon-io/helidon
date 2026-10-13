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

package io.helidon.common.tls;

import java.security.AlgorithmConstraints;
import java.security.AlgorithmParameters;
import java.security.CryptoPrimitive;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TlsHashCodeTest {
    @Test
    void eachScalarAndArrayStateMatchesPublicValueOracle() {
        List<Consumer<SSLParameters>> cases = List.of(
                _ -> {
                },
                p -> p.setCipherSuites(new String[0]),
                p -> p.setCipherSuites(new String[] {"TLS_AES_128_GCM_SHA256"}),
                p -> p.setCipherSuites(new String[] {"TLS_AES_256_GCM_SHA384", "TLS_AES_128_GCM_SHA256"}),
                p -> p.setProtocols(new String[0]),
                p -> p.setProtocols(new String[] {"TLSv1.3"}),
                p -> p.setProtocols(new String[] {"TLSv1.2", "TLSv1.3"}),
                p -> p.setApplicationProtocols(new String[] {"h3", "h2"}),
                p -> p.setEndpointIdentificationAlgorithm(""),
                p -> p.setEndpointIdentificationAlgorithm("HTTPS"),
                p -> p.setEnableRetransmissions(false),
                p -> p.setMaximumPacketSize(1232),
                p -> p.setNeedClientAuth(true),
                p -> p.setWantClientAuth(true),
                p -> p.setUseCipherSuitesOrder(true));
        var context = context();
        for (var configure : cases) {
            var parameters = new SSLParameters();
            configure.accept(parameters);
            var tls = create(context, parameters);
            assertHash(tls);
        }
    }

    @Test
    void orderedArraysRetainTheirDistinctHashContributions() {
        var context = context();
        var first = new SSLParameters(new String[] {"a", "b"}, new String[] {"one", "two"});
        first.setApplicationProtocols(new String[] {"h3", "h2"});
        var second = new SSLParameters(new String[] {"b", "a"}, new String[] {"two", "one"});
        second.setApplicationProtocols(new String[] {"h2", "h3"});
        var firstTls = create(context, first);
        var secondTls = create(context, second);
        assertHash(firstTls);
        assertHash(secondTls);
        assertThat(firstTls.hashCode(), not(secondTls.hashCode()));
    }

    @Test
    void legalNullEmptyAndOrderedSniStatesMatchOracle() {
        var context = context();
        var empty = new SSLParameters();
        empty.setServerNames(List.of());
        empty.setSNIMatchers(List.of());
        assertHash(create(context, empty));
        var names = new SSLParameters();
        names.setServerNames(List.of(new SNIHostName("example.com"), new OtherServerName(7)));
        var trace = new ArrayList<String>();
        names.setSNIMatchers(List.of(new TrackingMatcher(0, "first", trace), new TrackingMatcher(7, "second", trace)));
        assertHash(create(context, names));
        var reordered = new SSLParameters();
        reordered.setServerNames(List.of(new OtherServerName(7), new SNIHostName("example.com")));
        reordered.setSNIMatchers(List.of(new TrackingMatcher(7, "second", trace), new TrackingMatcher(0, "first", trace)));
        assertHash(create(context, reordered));
    }

    @Test
    void policyHashOrderMutableSeedAndOverflowRemainObservable() {
        var trace = new ArrayList<String>();
        var constraints = new TrackingConstraints(trace);
        var matcher = new TrackingMatcher(0, "matcher", trace);
        var parameters = new SSLParameters();
        parameters.setAlgorithmConstraints(constraints);
        parameters.setSNIMatchers(List.of(matcher));
        var tls = create(context(), parameters);
        trace.clear();
        int before = tls.hashCode();
        assertThat(trace, is(List.of("constraints", "matcher")));
        assertHash(tls);
        constraints.seed = Integer.MIN_VALUE;
        trace.clear();
        int after = tls.hashCode();
        assertThat(trace, is(List.of("constraints", "matcher")));
        assertThat(after, not(before));
        assertHash(tls);
    }

    @Test
    void policyHashFailurePreservesExactMarkerAndOrdering() {
        var trace = new ArrayList<String>();
        var constraints = new TrackingConstraints(trace);
        var parameters = new SSLParameters();
        parameters.setAlgorithmConstraints(constraints);
        parameters.setSNIMatchers(List.of(new TrackingMatcher(0, "matcher", trace)));
        var tls = create(context(), parameters);
        var marker = new IllegalStateException("policy marker");
        constraints.failure = marker;
        trace.clear();
        assertThat(assertThrows(IllegalStateException.class, tls::hashCode), sameInstance(marker));
        assertThat(trace, is(List.of("constraints")));
    }

    @Test
    void disabledHashDoesNotReadPolicyObjects() {
        var trace = new ArrayList<String>();
        var constraints = new TrackingConstraints(trace);
        var parameters = new SSLParameters();
        parameters.setAlgorithmConstraints(constraints);
        var tls = Tls.create(it -> it.enabled(false).sslParameters(parameters));
        constraints.failure = new IllegalStateException("disabled must not hash policy");
        trace.clear();
        assertThat(tls.hashCode(), is(Objects.hash(Tls.class)));
        assertThat(trace, empty());
    }

    @Test
    void mutableMatcherHashesAndOverflowAreNotCached() {
        var trace = new ArrayList<String>();
        var constraints = new TrackingConstraints(trace);
        var matcher = new TrackingMatcher(0, "matcher", trace);
        var parameters = new SSLParameters();
        parameters.setAlgorithmConstraints(constraints);
        parameters.setSNIMatchers(List.of(matcher));
        var tls = create(context(), parameters);

        for (int seed : new int[] {0, -31, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            constraints.seed = seed;
            matcher.seed = seed;
            assertHash(tls);
            trace.clear();
            tls.hashCode();
            assertThat("callbacks for seed " + seed, trace, is(List.of("constraints", "matcher")));
        }
    }

    @Test
    void matcherHashFailureRetainsMarkerAndSuppressesLaterCallbacks() {
        var trace = new ArrayList<String>();
        var parameters = new SSLParameters();
        parameters.setAlgorithmConstraints(new TrackingConstraints(trace));
        var first = new TrackingMatcher(0, "first", trace);
        parameters.setSNIMatchers(List.of(first, new TrackingMatcher(7, "second", trace)));
        var tls = create(context(), parameters);
        var marker = new IllegalStateException("matcher marker");
        first.failure = marker;
        trace.clear();

        assertThat(assertThrows(IllegalStateException.class, tls::hashCode), sameInstance(marker));
        assertThat(trace, is(List.of("constraints", "first")));
    }

    @Test
    void configuredAndReturnedCopiesCannotChangeInternalHash() {
        var parameters = new SSLParameters(new String[] {"cipher"}, new String[] {"protocol"});
        parameters.setApplicationProtocols(new String[] {"h3"});
        parameters.setServerNames(List.of(new SNIHostName("example.com")));
        var tls = create(context(), parameters);
        int originalHash = tls.hashCode();
        parameters.setProtocols(new String[] {"changed"});
        parameters.setCipherSuites(new String[] {"changed"});
        tls.prototype().sslParameters().orElseThrow().setApplicationProtocols(new String[] {"changed"});
        var copy = tls.sslParameters();
        copy.getProtocols()[0] = "array mutation";
        copy.getCipherSuites()[0] = "array mutation";
        copy.getApplicationProtocols()[0] = "array mutation";
        copy.setApplicationProtocols(new String[] {"replaced"});
        copy.setServerNames(List.of(new SNIHostName("different.example")));
        assertThrows(UnsupportedOperationException.class, () -> tls.sslParameters().getServerNames().clear());
        assertThat(tls.sslParameters().getProtocols(), is(new String[] {"protocol"}));
        assertThat(tls.sslParameters().getCipherSuites(), is(new String[] {"cipher"}));
        assertThat(tls.hashCode(), is(originalHash));
        assertHash(tls);
    }

    @Test
    void sourceArraysAreDefensivelyCopiedBeforeTlsConstruction() {
        var ciphers = new String[] {"cipher"};
        var protocols = new String[] {"protocol"};
        var alpn = new String[] {"h3"};
        var parameters = new SSLParameters(ciphers, protocols);
        parameters.setApplicationProtocols(alpn);
        ciphers[0] = protocols[0] = alpn[0] = "mutated";
        var tls = create(context(), parameters);
        assertThat(tls.sslParameters().getCipherSuites(), is(new String[] {"cipher"}));
        assertThat(tls.sslParameters().getProtocols(), is(new String[] {"protocol"}));
        assertThat(tls.sslParameters().getApplicationProtocols(), is(new String[] {"h3"}));
        assertHash(tls);
    }

    @Test
    void customParameterGettersAreNormalizedOnlyAtConstruction() {
        var parameters = new RecordingParameters();
        var tls = create(context(), parameters);
        assertThat(parameters.trace, is(List.of("cipher", "alpn", "packet", "protocols", "need", "need", "want", "want")));
        assertThat(tls.sslParameters().getNeedClientAuth(), is(false));
        assertThat(tls.sslParameters().getWantClientAuth(), is(true));
        parameters.trace.clear();
        assertHash(tls);
        tls.hashCode();
        assertThat(parameters.trace, empty());
    }

    @Test
    void thrownConstructionGetterStopsAtSameBoundaryEvenWhenDisabled() {
        for (boolean enabled : List.of(false, true)) {
            var parameters = new RecordingParameters();
            var marker = new IllegalStateException("constructor marker");
            parameters.protocolFailure = marker;
            var failure = assertThrows(IllegalStateException.class,
                                       () -> Tls.create(it -> it.enabled(enabled).sslParameters(parameters)));
            assertThat(failure, sameInstance(marker));
            assertThat(parameters.trace, is(List.of("cipher", "alpn", "packet", "protocols")));
        }
    }

    @Test
    void negativePacketAndNullAlpnFailuresStayAtConstruction() {
        var negative = new RecordingParameters();
        negative.packetSize = -1;
        assertThrows(IllegalArgumentException.class, () -> create(context(), negative));
        assertThat(negative.trace, is(List.of("cipher", "alpn", "packet")));
        var nullAlpn = new RecordingParameters();
        nullAlpn.nullAlpn = true;
        assertThrows(IllegalArgumentException.class, () -> create(context(), nullAlpn));
        assertThat(nullAlpn.trace, is(List.of("cipher", "alpn")));
    }

    @Test
    void changingAuthGetterResultsRetainRepeatedReadNormalization() {
        var parameters = new RecordingParameters();
        parameters.changingNeed = true;
        parameters.want = false;
        var tls = create(context(), parameters);
        assertThat(parameters.trace, is(List.of("cipher", "alpn", "packet", "protocols", "need", "need", "want")));
        assertThat(tls.sslParameters().getNeedClientAuth(), is(false));
        assertThat(tls.sslParameters().getWantClientAuth(), is(false));
        assertHash(tls);
    }

    @Test
    void equalInstancesWithSharedContextRetainEqualIntegers() {
        var context = context();
        var first = create(context, new SSLParameters(new String[] {"cipher"}, new String[] {"protocol"}));
        var second = create(context, new SSLParameters(new String[] {"cipher"}, new String[] {"protocol"}));
        assertThat(first.equals(second), is(true));
        assertThat(second.equals(first), is(true));
        assertThat(first.hashCode(), is(second.hashCode()));
        assertHash(first);
        assertHash(second);
        var disabled = Tls.builder().enabled(false).build();
        assertThat(disabled.equals(Tls.builder().enabled(false).build()), is(true));
        assertThat(disabled.hashCode(), is(Objects.hash(Tls.class)));
    }

    @Test
    void copiedButUnhashedFieldsRetainTheirExistingContract() {
        var context = context();
        var parameters = new SSLParameters();
        parameters.setNamedGroups(new String[] {"x25519"});
        parameters.setSignatureSchemes(new String[] {"ecdsa_secp256r1_sha256"});
        var first = create(context, parameters);
        var second = create(context, new SSLParameters());
        assertThat(first.sslParameters().getNamedGroups(), is(new String[] {"x25519"}));
        assertThat(first.sslParameters().getSignatureSchemes(), is(new String[] {"ecdsa_secp256r1_sha256"}));
        assertThat(first.equals(second), is(true));
        assertThat(first.hashCode(), is(second.hashCode()));
        assertHash(first);
    }

    @Test
    void actualBuiltInReloadRetainsHashOracleAndParameterCopies() {
        var tls = Tls.create(it -> it.trustAll(true).addApplicationProtocol("h3"));
        var context = tls.sslContext();
        assertHash(tls);
        tls.reload(TlsMaterial.builder().trustAll(true).build());
        assertThat(tls.generation(), is(1L));
        assertThat(tls.sslContext(), sameInstance(context));
        assertHash(tls);
        tls.reload(Tls.create(it -> it.trustAll(true)));
        assertThat(tls.generation(), is(2L));
        assertThat(tls.newEngine().getSSLParameters().getApplicationProtocols(), is(new String[] {"h3"}));
        assertHash(tls);
    }

    private static Tls create(SSLContext context, SSLParameters parameters) {
        return Tls.create(it -> it.sslContext(context).sslParameters(parameters));
    }

    private static SSLContext context() {
        try {
            var context = SSLContext.getInstance("TLS");
            context.init(null, null, null);
            return context;
        } catch (GeneralSecurityException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void assertHash(Tls tls) {
        var parameters = tls.sslParameters();
        int hash = Objects.hash(parameters.getAlgorithmConstraints(), parameters.getEnableRetransmissions(),
                parameters.getEndpointIdentificationAlgorithm(), parameters.getMaximumPacketSize(),
                parameters.getNeedClientAuth(), parameters.getUseCipherSuitesOrder(), parameters.getWantClientAuth(),
                parameters.getServerNames(), parameters.getSNIMatchers());
        hash = 31 * hash + Arrays.hashCode(parameters.getApplicationProtocols());
        hash = 31 * hash + Arrays.hashCode(parameters.getCipherSuites());
        hash = 31 * hash + Arrays.hashCode(parameters.getProtocols());
        assertThat(tls.hashCode(), is(31 * Objects.hash(tls.sslContext()) + hash));
    }

    private static final class TrackingConstraints implements AlgorithmConstraints {
        private final List<String> trace;
        private int seed = -31;
        private IllegalStateException failure;

        private TrackingConstraints(List<String> trace) {
            this.trace = trace;
        }

        @Override
        public boolean permits(Set<CryptoPrimitive> primitives, String algorithm, AlgorithmParameters parameters) {
            return true;
        }

        @Override
        public boolean permits(Set<CryptoPrimitive> primitives, Key key) {
            return true;
        }

        @Override
        public boolean permits(Set<CryptoPrimitive> primitives, String algorithm, Key key, AlgorithmParameters parameters) {
            return true;
        }

        @Override
        public int hashCode() {
            trace.add("constraints");
            if (failure != null) {
                throw failure;
            }
            return seed;
        }
    }

    private static final class TrackingMatcher extends SNIMatcher {
        private final String label;
        private final List<String> trace;
        private int seed = -17;
        private IllegalStateException failure;

        private TrackingMatcher(int type, String label, List<String> trace) {
            super(type);
            this.label = label;
            this.trace = trace;
        }

        @Override
        public boolean matches(SNIServerName serverName) {
            return true;
        }

        @Override
        public int hashCode() {
            trace.add(label);
            if (failure != null) {
                throw failure;
            }
            return seed;
        }
    }

    private static final class OtherServerName extends SNIServerName {
        private OtherServerName(int type) {
            super(type, new byte[] {1, 2});
        }
    }

    private static final class RecordingParameters extends SSLParameters {
        private final List<String> trace = new ArrayList<>();
        private int packetSize;
        private int needReads;
        private boolean changingNeed;
        private boolean want = true;
        private boolean nullAlpn;
        private IllegalStateException protocolFailure;

        @Override
        public String[] getCipherSuites() {
            trace.add("cipher");
            return new String[] {"TLS_AES_128_GCM_SHA256"};
        }

        @Override
        public String[] getApplicationProtocols() {
            trace.add("alpn");
            return nullAlpn ? null : new String[] {"h3"};
        }

        @Override
        public int getMaximumPacketSize() {
            trace.add("packet");
            return packetSize;
        }

        @Override
        public String[] getProtocols() {
            trace.add("protocols");
            if (protocolFailure != null) {
                throw protocolFailure;
            }
            return new String[] {"TLSv1.3"};
        }

        @Override
        public boolean getNeedClientAuth() {
            trace.add("need");
            return !changingNeed || needReads++ == 0;
        }

        @Override
        public boolean getWantClientAuth() {
            trace.add("want");
            return want;
        }
    }
}
