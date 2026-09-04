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

import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSessionBindingEvent;
import javax.net.ssl.SSLSessionBindingListener;
import javax.net.ssl.SSLSessionContext;

import io.helidon.common.buffers.BufferData;

final class QuicTlsCallbackSession extends ExtendedSSLSession {
    private static final int DEFAULT_APPLICATION_BUFFER_SIZE = 16 * 1024;
    private static final int DEFAULT_PACKET_BUFFER_SIZE = 16 * 1024;
    private static final String DEFAULT_PROTOCOL = "TLSv1.3";
    private static final String DEFAULT_CIPHER_SUITE = "TLS_AES_128_GCM_SHA256";

    private final long creationTime = System.currentTimeMillis();
    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private final String peerHost;
    private final int peerPort;
    private final String[] peerSupportedSignatureAlgorithms;

    private volatile SSLParameters sslParameters;
    private volatile String[] localSupportedSignatureAlgorithms;
    private volatile List<SNIServerName> requestedServerNames;
    private volatile boolean valid = true;
    private volatile Certificate[] peerCertificates;
    private volatile Certificate[] localCertificates;
    private volatile String protocol = DEFAULT_PROTOCOL;
    private volatile String cipherSuite = DEFAULT_CIPHER_SUITE;

    QuicTlsCallbackSession(SSLParameters sslParameters) {
        this(sslParameters, signatureSchemes(sslParameters), new String[0]);
    }

    QuicTlsCallbackSession(SSLParameters sslParameters,
                           String[] localSupportedSignatureSchemes,
                           String[] peerSupportedSignatureSchemes) {
        this.peerHost = null;
        this.peerPort = -1;
        this.localSupportedSignatureAlgorithms = supportedSignatureAlgorithms(localSupportedSignatureSchemes);
        this.peerSupportedSignatureAlgorithms = supportedSignatureAlgorithms(peerSupportedSignatureSchemes);
        this.sslParameters = QuicTlsParameters.copy(sslParameters);
        this.requestedServerNames = serverNames(this.sslParameters);
    }

    QuicTlsCallbackSession(String peerHost, int peerPort, SSLParameters sslParameters) {
        this(peerHost, peerPort, sslParameters, signatureSchemes(sslParameters), new String[0]);
    }

    QuicTlsCallbackSession(String peerHost,
                           int peerPort,
                           SSLParameters sslParameters,
                           String[] localSupportedSignatureSchemes,
                           String[] peerSupportedSignatureSchemes) {
        this.peerHost = Objects.requireNonNull(peerHost, "peerHost");
        this.peerPort = peerPort;
        this.localSupportedSignatureAlgorithms = supportedSignatureAlgorithms(localSupportedSignatureSchemes);
        this.peerSupportedSignatureAlgorithms = supportedSignatureAlgorithms(peerSupportedSignatureSchemes);
        this.sslParameters = QuicTlsParameters.copy(sslParameters);
        this.requestedServerNames = serverNames(this.sslParameters);
    }

    @Override
    public String[] getLocalSupportedSignatureAlgorithms() {
        String[] algorithms = localSupportedSignatureAlgorithms;
        return algorithms.clone();
    }

    @Override
    public String[] getPeerSupportedSignatureAlgorithms() {
        return peerSupportedSignatureAlgorithms.clone();
    }

    @Override
    public List<SNIServerName> getRequestedServerNames() {
        return requestedServerNames;
    }

    @Override
    public List<byte[]> getStatusResponses() {
        return List.of();
    }

    @Override
    public byte[] getId() {
        return BufferData.EMPTY_BYTES;
    }

    @Override
    public SSLSessionContext getSessionContext() {
        return null;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public long getLastAccessedTime() {
        return creationTime;
    }

    @Override
    public void invalidate() {
        valid = false;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public void putValue(String name, Object value) {
        Object previous = values.put(name, value);
        if (value instanceof SSLSessionBindingListener listener) {
            listener.valueBound(new SSLSessionBindingEvent(this, name));
        }
        if (previous instanceof SSLSessionBindingListener listener) {
            listener.valueUnbound(new SSLSessionBindingEvent(this, name));
        }
    }

    @Override
    public Object getValue(String name) {
        return values.get(name);
    }

    @Override
    public void removeValue(String name) {
        Object previous = values.remove(name);
        if (previous instanceof SSLSessionBindingListener listener) {
            listener.valueUnbound(new SSLSessionBindingEvent(this, name));
        }
    }

    @Override
    public String[] getValueNames() {
        return values.keySet().toArray(new String[0]);
    }

    @Override
    public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        if (peerCertificates == null) {
            throw new SSLPeerUnverifiedException("Peer not verified");
        }
        return peerCertificates.clone();
    }

    @Override
    public Certificate[] getLocalCertificates() {
        return localCertificates == null ? null : localCertificates.clone();
    }

    @Override
    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        return x509Principal(getPeerCertificates());
    }

    @Override
    public Principal getLocalPrincipal() {
        try {
            return x509Principal(getLocalCertificates());
        } catch (SSLPeerUnverifiedException e) {
            return null;
        }
    }

    @Override
    public String getCipherSuite() {
        return cipherSuite;
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public String getPeerHost() {
        return peerHost;
    }

    @Override
    public int getPeerPort() {
        return peerPort;
    }

    @Override
    public int getPacketBufferSize() {
        int maximumPacketSize = sslParameters.getMaximumPacketSize();
        return maximumPacketSize > 0 ? maximumPacketSize : DEFAULT_PACKET_BUFFER_SIZE;
    }

    @Override
    public int getApplicationBufferSize() {
        return DEFAULT_APPLICATION_BUFFER_SIZE;
    }

    void sslParameters(SSLParameters sslParameters) {
        this.sslParameters = QuicTlsParameters.copy(sslParameters);
        this.localSupportedSignatureAlgorithms = supportedSignatureAlgorithms(signatureSchemes(this.sslParameters));
        this.requestedServerNames = serverNames(this.sslParameters);
    }

    void requestedServerNames(List<SNIServerName> requestedServerNames) {
        this.requestedServerNames = List.copyOf(requestedServerNames);
    }

    void peerCertificates(Certificate[] peerCertificates) {
        this.peerCertificates = peerCertificates == null ? null : peerCertificates.clone();
    }

    void localCertificates(Certificate[] localCertificates) {
        this.localCertificates = localCertificates == null ? null : localCertificates.clone();
    }

    void protocol(String protocol) {
        this.protocol = protocol;
    }

    void cipherSuite(String cipherSuite) {
        this.cipherSuite = cipherSuite;
    }

    private static String signatureAlgorithm(String signatureScheme) {
        try {
            return QuicTlsSignatureScheme.forTlsName(signatureScheme).jcaSignatureAlgorithm();
        } catch (IllegalArgumentException e) {
            // SSLParameters allows provider-specific names too, so preserve unknown entries rather than rejecting them.
            return signatureScheme;
        }
    }

    private static List<SNIServerName> serverNames(SSLParameters sslParameters) {
        List<SNIServerName> serverNames = sslParameters.getServerNames();
        return serverNames == null ? List.of() : List.copyOf(serverNames);
    }

    private static Principal x509Principal(Certificate[] certificates) throws SSLPeerUnverifiedException {
        if (certificates == null || certificates.length == 0 || !(certificates[0] instanceof X509Certificate x509)) {
            throw new SSLPeerUnverifiedException("Peer not verified");
        }
        return x509.getSubjectX500Principal();
    }

    private static String[] supportedSignatureAlgorithms(String[] signatureSchemes) {
        Objects.requireNonNull(signatureSchemes, "signatureSchemes");
        String[] algorithms = new String[signatureSchemes.length];
        for (int i = 0; i < signatureSchemes.length; i++) {
            algorithms[i] = signatureAlgorithm(signatureSchemes[i]);
        }
        return algorithms;
    }

    private static String[] signatureSchemes(SSLParameters sslParameters) {
        String[] signatureSchemes = Objects.requireNonNull(sslParameters, "sslParameters").getSignatureSchemes();
        return signatureSchemes == null ? new String[0] : signatureSchemes;
    }
}
