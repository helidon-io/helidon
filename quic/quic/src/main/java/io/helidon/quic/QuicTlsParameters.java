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

import java.util.Arrays;
import java.util.Objects;

import javax.net.ssl.SSLParameters;

final class QuicTlsParameters {
    private static final String TLS13 = "TLSv1.3";
    private static final String ALGORITHM_CONSTRAINTS_UNSUPPORTED =
            "AlgorithmConstraints are not supported by the Helidon QUIC TLS engine";
    private static final String SNI_MATCHERS_UNSUPPORTED =
            "SNIMatchers are not supported by the Helidon QUIC TLS engine";

    private QuicTlsParameters() {
    }

    static SSLParameters defaultParameters() {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[] {TLS13});
        return sslParameters;
    }

    static SSLParameters copy(SSLParameters source) {
        Objects.requireNonNull(source, "source");
        SSLParameters target = new SSLParameters();
        target.setAlgorithmConstraints(source.getAlgorithmConstraints());
        target.setApplicationProtocols(source.getApplicationProtocols());
        target.setCipherSuites(source.getCipherSuites());
        target.setEnableRetransmissions(source.getEnableRetransmissions());
        target.setEndpointIdentificationAlgorithm(source.getEndpointIdentificationAlgorithm());
        target.setMaximumPacketSize(source.getMaximumPacketSize());
        target.setNamedGroups(source.getNamedGroups());
        target.setProtocols(source.getProtocols());
        target.setServerNames(source.getServerNames());
        target.setSignatureSchemes(source.getSignatureSchemes());
        target.setSNIMatchers(source.getSNIMatchers());
        target.setUseCipherSuitesOrder(source.getUseCipherSuitesOrder());
        if (source.getNeedClientAuth()) {
            target.setNeedClientAuth(true);
        } else if (source.getWantClientAuth()) {
            target.setWantClientAuth(true);
        }
        return target;
    }

    static boolean allowsTls13(SSLParameters parameters) {
        String[] protocols = parameters.getProtocols();
        return protocols == null || protocols.length == 0 || Arrays.asList(protocols).contains(TLS13);
    }

    static void validateSupported(SSLParameters parameters) {
        Objects.requireNonNull(parameters, "parameters");
        if (parameters.getAlgorithmConstraints() != null) {
            throw new IllegalArgumentException(ALGORITHM_CONSTRAINTS_UNSUPPORTED);
        }
        if (parameters.getSNIMatchers() != null && !parameters.getSNIMatchers().isEmpty()) {
            throw new IllegalArgumentException(SNI_MATCHERS_UNSUPPORTED);
        }
    }
}
