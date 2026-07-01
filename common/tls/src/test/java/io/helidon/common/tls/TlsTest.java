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
import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    public void explicitSslContextUsesProvidedContext() {
        SSLContext sslContext = createSslContext();

        Tls tls = Tls.create(it -> it.sslContext(sslContext)
                .addApplicationProtocol("h3"));

        assertThat(tls.sslContext(), sameInstance(sslContext));
        assertThat(tls.prototype().manager(), instanceOf(ExplicitContextTlsManager.class));
        assertThat(tls.newEngine().getSSLParameters().getApplicationProtocols(), arrayContaining("h3"));
    }

    @Test
    public void explicitSslContextRejectsIncompatibleOptions() {
        SSLContext sslContext = createSslContext();

        IllegalArgumentException managerException = assertThrows(IllegalArgumentException.class,
                                                                  () -> Tls.create(it -> it.sslContext(sslContext)
                                                                          .manager(new FailingTlsManager())));
        assertThat(managerException.getMessage(), is("Explicit SSLContext cannot be combined with TLS options: manager"));

        IllegalArgumentException materialException = assertThrows(IllegalArgumentException.class,
                                                                   () -> Tls.create(it -> it.sslContext(sslContext)
                                                                           .trustAll(true)
                                                                           .sessionCacheSize(1)));
        assertThat(materialException.getMessage(),
                   is("Explicit SSLContext cannot be combined with TLS options: trust-all, session-cache-size"));
    }

    @Test
    public void explicitSslContextSupportsBuilderReuseAndPrototypeCopy() {
        SSLContext sslContext = createSslContext();
        TlsConfig.Builder builder = Tls.builder().sslContext(sslContext);

        TlsConfig prototype = builder.buildPrototype();
        TlsConfig repeatedBuild = builder.buildPrototype();
        assertThat(repeatedBuild.manager(), sameInstance(prototype.manager()));

        Tls first = Tls.create(prototype);
        Tls second = Tls.create(repeatedBuild);
        assertThat(first.sslContext(), sameInstance(sslContext));
        assertThat(second.sslContext(), sameInstance(sslContext));

        TlsConfig copy = TlsConfig.builder(prototype).buildPrototype();
        assertThat(copy.sslContext().orElseThrow(), sameInstance(sslContext));
        assertThat(copy.manager(), sameInstance(prototype.manager()));
        assertThat(copy, is(prototype));

        TlsConfig defaultPrototype = Tls.builder().buildPrototype();
        TlsConfig contextCopy = TlsConfig.builder(defaultPrototype)
                .sslContext(sslContext)
                .buildPrototype();
        assertThat(contextCopy.sslContext().orElseThrow(), sameInstance(sslContext));
        assertThat(contextCopy.manager(), instanceOf(ExplicitContextTlsManager.class));

        Tls withoutContext = builder.clearSslContext().build();
        assertThat(withoutContext.prototype().sslContext().isEmpty(), is(true));
        assertThat(withoutContext.prototype().manager(), instanceOf(ConfiguredTlsManager.class));
    }

    @Test
    public void explicitSslContextRejectsMaterialReload() {
        SSLContext sslContext = createSslContext();
        Tls tls = Tls.create(it -> it.sslContext(sslContext));
        TlsMaterial material = TlsMaterial.builder()
                .trustAll(true)
                .build();

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                                                               () -> tls.reload(material));

        assertThat(exception.getMessage(),
                   is("TLS cannot be reloaded when an explicit instance of SSL context was used to create it"));
    }

    private static SSLContext createSslContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }

    private static final class FailingTlsManager implements TlsManager {
        @Override
        public void init(TlsConfig tls) {
            throw new AssertionError("Explicit SSLContext should use ExplicitContextTlsManager");
        }

        @Override
        public SSLContext sslContext() {
            throw new AssertionError("Explicit SSLContext should use ExplicitContextTlsManager");
        }

        @Override
        public Optional<X509KeyManager> keyManager() {
            throw new AssertionError("Explicit SSLContext should use ExplicitContextTlsManager");
        }

        @Override
        public Optional<X509TrustManager> trustManager() {
            throw new AssertionError("Explicit SSLContext should use ExplicitContextTlsManager");
        }

        @Override
        public String name() {
            return "failing";
        }

        @Override
        public String type() {
            return "failing";
        }
    }
}
