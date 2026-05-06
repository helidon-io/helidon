/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.security.AlgorithmConstraints;
import java.security.AlgorithmParameters;
import java.security.CryptoPrimitive;
import java.security.Key;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;

public class TlsTest {
    @Test
    public void testTlsEquals() {
        SSLParameters first = new SSLParameters();
        SSLParameters second = new SSLParameters();

        assertThat(Tls.equals(first, second), is(true));

        AlgorithmConstraints constraints = new AlgorithmConstraints() {
            @Override
            public boolean permits(Set<CryptoPrimitive> primitives, String algorithm, AlgorithmParameters parameters) {
                return false;
            }

            @Override
            public boolean permits(Set<CryptoPrimitive> primitives, Key key) {
                return false;
            }

            @Override
            public boolean permits(Set<CryptoPrimitive> primitives, String algorithm, Key key, AlgorithmParameters parameters) {
                return false;
            }
        };

        first.setAlgorithmConstraints(constraints);
        second.setAlgorithmConstraints(constraints);

        assertThat(Tls.equals(first, second), is(true));

        first.setServerNames(List.of());
        second.setServerNames(List.of());

        assertThat(Tls.equals(first, second), is(true));

        first.setSNIMatchers(List.of());
        second.setSNIMatchers(List.of());
    }

    @Test
    public void testSslParametersCannotMutateTls() throws IOException {
        Tls tls = Tls.create(it -> it.addApplicationProtocol("h2")
                .endpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_HTTPS));

        SSLParameters parameters = tls.sslParameters();
        parameters.setApplicationProtocols(new String[] {"http/1.1"});
        parameters.setEndpointIdentificationAlgorithm("");

        SSLParameters freshParameters = tls.sslParameters();
        assertThat(freshParameters.getApplicationProtocols(), arrayContaining("h2"));
        assertThat(freshParameters.getEndpointIdentificationAlgorithm(), is(Tls.ENDPOINT_IDENTIFICATION_HTTPS));

        SSLParameters engineParameters = tls.newEngine().getSSLParameters();
        assertThat(engineParameters.getApplicationProtocols(), arrayContaining("h2"));
        assertThat(engineParameters.getEndpointIdentificationAlgorithm(), is(Tls.ENDPOINT_IDENTIFICATION_HTTPS));

        try (SSLServerSocket serverSocket = tls.createServerSocket()) {
            SSLParameters serverParameters = serverSocket.getSSLParameters();
            assertThat(serverParameters.getApplicationProtocols(), arrayContaining("h2"));
            assertThat(serverParameters.getEndpointIdentificationAlgorithm(), is(""));
        }
    }

    @Test
    public void testConfiguredSslParametersCannotMutateTls() {
        SSLParameters configured = new SSLParameters();
        configured.setApplicationProtocols(new String[] {"h2"});
        configured.setEndpointIdentificationAlgorithm(Tls.ENDPOINT_IDENTIFICATION_HTTPS);

        Tls tls = Tls.create(it -> it.sslParameters(configured));

        configured.setApplicationProtocols(new String[] {"http/1.1"});
        configured.setEndpointIdentificationAlgorithm("");

        SSLParameters freshParameters = tls.sslParameters();
        assertThat(freshParameters.getApplicationProtocols(), arrayContaining("h2"));
        assertThat(freshParameters.getEndpointIdentificationAlgorithm(), is(Tls.ENDPOINT_IDENTIFICATION_HTTPS));

        SSLParameters engineParameters = tls.newEngine().getSSLParameters();
        assertThat(engineParameters.getApplicationProtocols(), arrayContaining("h2"));
        assertThat(engineParameters.getEndpointIdentificationAlgorithm(), is(Tls.ENDPOINT_IDENTIFICATION_HTTPS));
    }
}
