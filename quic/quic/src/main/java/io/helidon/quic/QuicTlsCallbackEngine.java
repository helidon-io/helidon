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

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.BiFunction;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

final class QuicTlsCallbackEngine extends SSLEngine {
    private static final String[] SUPPORTED_PROTOCOLS = {"TLSv1.3"};

    private final QuicTlsCallbackSession handshakeSession;

    private volatile SSLParameters sslParameters;
    private volatile boolean clientMode;
    private volatile boolean inboundDone;
    private volatile boolean outboundDone;
    private volatile boolean enableSessionCreation = true;
    private volatile String applicationProtocol;
    private volatile String handshakeApplicationProtocol;
    private volatile BiFunction<SSLEngine, List<String>, String> handshakeSelector;

    QuicTlsCallbackEngine(SSLParameters sslParameters,
                          String[] localSupportedSignatureSchemes,
                          String[] peerSupportedSignatureSchemes) {
        this.sslParameters = QuicTlsParameters.copy(sslParameters);
        this.handshakeSession = new QuicTlsCallbackSession(this.sslParameters,
                                                           localSupportedSignatureSchemes,
                                                           peerSupportedSignatureSchemes);
    }

    QuicTlsCallbackEngine(String peerHost,
                          int peerPort,
                          SSLParameters sslParameters,
                          String[] localSupportedSignatureSchemes,
                          String[] peerSupportedSignatureSchemes) {
        super(peerHost, peerPort);
        this.sslParameters = QuicTlsParameters.copy(sslParameters);
        this.handshakeSession = new QuicTlsCallbackSession(peerHost,
                                                           peerPort,
                                                           this.sslParameters,
                                                           localSupportedSignatureSchemes,
                                                           peerSupportedSignatureSchemes);
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer[] srcs, int offset, int length, ByteBuffer dst) throws SSLException {
        throw new UnsupportedOperationException("Callback SSLEngine does not perform wrap operations");
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts, int offset, int length) throws SSLException {
        throw new UnsupportedOperationException("Callback SSLEngine does not perform unwrap operations");
    }

    @Override
    public Runnable getDelegatedTask() {
        return null;
    }

    @Override
    public void closeInbound() {
        inboundDone = true;
    }

    @Override
    public boolean isInboundDone() {
        return inboundDone;
    }

    @Override
    public void closeOutbound() {
        outboundDone = true;
    }

    @Override
    public boolean isOutboundDone() {
        return outboundDone;
    }

    @Override
    public String[] getSupportedCipherSuites() {
        String[] cipherSuites = sslParameters.getCipherSuites();
        return cipherSuites == null ? new String[0] : cipherSuites.clone();
    }

    @Override
    public String[] getEnabledCipherSuites() {
        String[] cipherSuites = sslParameters.getCipherSuites();
        return cipherSuites == null ? new String[0] : cipherSuites.clone();
    }

    @Override
    public void setEnabledCipherSuites(String[] suites) {
        SSLParameters parameters = getSSLParameters();
        parameters.setCipherSuites(suites);
        setSSLParameters(parameters);
    }

    @Override
    public String[] getSupportedProtocols() {
        return SUPPORTED_PROTOCOLS.clone();
    }

    @Override
    public String[] getEnabledProtocols() {
        String[] protocols = sslParameters.getProtocols();
        return protocols == null ? new String[0] : protocols.clone();
    }

    @Override
    public void setEnabledProtocols(String[] protocols) {
        SSLParameters parameters = getSSLParameters();
        parameters.setProtocols(protocols);
        setSSLParameters(parameters);
    }

    @Override
    public SSLSession getSession() {
        return handshakeSession;
    }

    @Override
    public SSLSession getHandshakeSession() {
        return handshakeSession;
    }

    @Override
    public void beginHandshake() {
        // no-op; this engine only acts as callback context for key/trust managers
    }

    @Override
    public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        return SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
    }

    @Override
    public boolean getUseClientMode() {
        return clientMode;
    }

    @Override
    public void setUseClientMode(boolean mode) {
        this.clientMode = mode;
    }

    @Override
    public boolean getNeedClientAuth() {
        return sslParameters.getNeedClientAuth();
    }

    @Override
    public void setNeedClientAuth(boolean need) {
        SSLParameters parameters = getSSLParameters();
        parameters.setNeedClientAuth(need);
        setSSLParameters(parameters);
    }

    @Override
    public boolean getWantClientAuth() {
        return sslParameters.getWantClientAuth();
    }

    @Override
    public void setWantClientAuth(boolean want) {
        SSLParameters parameters = getSSLParameters();
        parameters.setWantClientAuth(want);
        setSSLParameters(parameters);
    }

    @Override
    public boolean getEnableSessionCreation() {
        return enableSessionCreation;
    }

    @Override
    public void setEnableSessionCreation(boolean flag) {
        this.enableSessionCreation = flag;
    }

    @Override
    public SSLParameters getSSLParameters() {
        return QuicTlsParameters.copy(sslParameters);
    }

    @Override
    public void setSSLParameters(SSLParameters sslParameters) {
        this.sslParameters = QuicTlsParameters.copy(sslParameters);
        this.handshakeSession.sslParameters(this.sslParameters);
    }

    @Override
    public String getApplicationProtocol() {
        return applicationProtocol;
    }

    @Override
    public String getHandshakeApplicationProtocol() {
        return handshakeApplicationProtocol;
    }

    @Override
    public BiFunction<SSLEngine, List<String>, String> getHandshakeApplicationProtocolSelector() {
        return handshakeSelector;
    }

    @Override
    public void setHandshakeApplicationProtocolSelector(BiFunction<SSLEngine, List<String>, String> selector) {
        this.handshakeSelector = selector;
    }

    void applicationProtocol(String applicationProtocol) {
        this.applicationProtocol = applicationProtocol;
    }

    void handshakeApplicationProtocol(String handshakeApplicationProtocol) {
        this.handshakeApplicationProtocol = handshakeApplicationProtocol;
    }

    QuicTlsCallbackSession callbackSession() {
        return handshakeSession;
    }
}
