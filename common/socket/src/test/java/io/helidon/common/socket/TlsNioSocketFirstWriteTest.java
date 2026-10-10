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

package io.helidon.common.socket;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import io.helidon.common.buffers.BufferData;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TlsNioSocketFirstWriteTest {
    private static final int TIMEOUT_SECONDS = 10;

    @TestFactory
    List<DynamicTest> firstApplicationWrite() throws Exception {
        SSLContext context = context();
        int applicationBufferSize = context.createSSLEngine().getSession().getApplicationBufferSize();
        List<DynamicTest> tests = new ArrayList<>();
        for (String protocol : new String[] {"TLSv1.2", "TLSv1.3"}) {
            for (boolean client : new boolean[] {false, true}) {
                for (int size : new int[] {37, applicationBufferSize * 2 + 37}) {
                    String name = protocol + " " + (client ? "client" : "server") + " first-write bytes=" + size;
                    tests.add(DynamicTest.dynamicTest(name, () -> firstWrite(protocol, client, size)));
                }
            }
        }
        return tests;
    }

    private static void firstWrite(String protocol, boolean client, int size) throws Exception {
        SSLContext context = context();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            if (client) {
                try (SSLServerSocket listener = (SSLServerSocket) context.getServerSocketFactory().createServerSocket()) {
                    listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                    listener.setSoTimeout(TIMEOUT_SECONDS * 1000);
                    listener.setEnabledProtocols(new String[] {protocol});
                    try (SocketChannel channel = SocketChannel.open(listener.getLocalSocketAddress());
                         SSLSocket peer = (SSLSocket) listener.accept()) {
                        SSLEngine engine = context.createSSLEngine("localhost", listener.getLocalPort());
                        SSLParameters parameters = engine.getSSLParameters();
                        parameters.setEndpointIdentificationAlgorithm("HTTPS");
                        engine.setSSLParameters(parameters);
                        engine.setEnabledProtocols(new String[] {protocol});
                        exchange(TlsNioSocket.client(channel, engine, "test-client"), peer, executor, protocol, size);
                    }
                }
            } else {
                try (ServerSocketChannel listener = ServerSocketChannel.open()) {
                    listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                    try (SSLSocket peer = (SSLSocket) context.getSocketFactory().createSocket()) {
                        SSLParameters parameters = peer.getSSLParameters();
                        parameters.setEndpointIdentificationAlgorithm("HTTPS");
                        peer.setSSLParameters(parameters);
                        peer.connect(listener.getLocalAddress(), TIMEOUT_SECONDS * 1000);
                        try (SocketChannel channel = listener.accept()) {
                            SSLEngine engine = context.createSSLEngine();
                            engine.setEnabledProtocols(new String[] {protocol});
                            exchange(TlsNioSocket.server(channel, engine, "test-listener", "test-server"),
                                     peer, executor, protocol, size);
                        }
                    }
                }
            }
        } finally {
            // Both endpoints are closed before waiting, so a failed handshake cannot leave a blocked writer.
            executor.shutdownNow();
            assertThat("TLS writer terminated", executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
        }
    }

    private static void exchange(TlsNioSocket socket,
                                 SSLSocket peer,
                                 ExecutorService executor,
                                 String protocol,
                                 int size) throws Exception {
        peer.setEnabledProtocols(new String[] {protocol});
        peer.setSoTimeout(TIMEOUT_SECONDS * 1000);
        byte[] first = new byte[size];
        for (int i = 0; i < first.length; i++) {
            first[i] = (byte) (i * 31 + 7);
        }
        byte[] subsequent = new byte[] {91, 92, 93, 94, 95};
        byte[] expected = Arrays.copyOf(first, first.length + subsequent.length);
        System.arraycopy(subsequent, 0, expected, first.length, subsequent.length);

        Future<?> writer = executor.submit(() -> {
            try {
                // Neither side starts the engine handshake separately: this application write initiates it.
                socket.write(BufferData.create(first));
                socket.write(BufferData.create(subsequent));
            } finally {
                socket.close();
            }
        });
        byte[] actual = peer.getInputStream().readAllBytes();
        writer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat("negotiated protocol", peer.getSession().getProtocol(), is(protocol));
        // Reading through close-notify detects lost bytes as an exact length/content failure, rather than a timeout.
        assertThat("all first and subsequent write bytes for payload size " + size, actual.length, is(expected.length));
        assertThat("first and subsequent write content for payload size " + size, actual, is(expected));
    }

    private static SSLContext context() throws Exception {
        char[] password = "password".toCharArray();
        KeyStore keys = KeyStore.getInstance("PKCS12");
        // Reuse the HTTP/2 test certificate, valid through 2299 with localhost and loopback address SANs.
        try (InputStream input = TlsNioSocketFirstWriteTest.class.getResourceAsStream("/first-write.p12")) {
            keys.load(input, password);
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keys, password);
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        trust.setCertificateEntry("server", keys.getCertificate(keys.aliases().nextElement()));
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trust);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);
        return context;
    }
}
