/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;

import javax.net.ssl.X509TrustManager;

class TlsReloadableX509TrustManager implements X509TrustManager, TlsReloadableComponent {
    private static final System.Logger LOGGER = System.getLogger(TlsReloadableX509TrustManager.class.getName());

    private volatile X509TrustManager trustManager;

    private TlsReloadableX509TrustManager(X509TrustManager trustManager) {
        this.trustManager = trustManager;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        trustManager.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        trustManager.checkServerTrusted(chain, authType);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return trustManager.getAcceptedIssuers();
    }

    @Override
    public void reload(Tls tls) {
        tls.trustManager().ifPresent(this::reload);
    }

    void reload(X509TrustManager trustManager) {
        Objects.requireNonNull(trustManager, "Cannot unset trust store");
        assertValid(trustManager);
        LOGGER.log(System.Logger.Level.DEBUG, "Reloading TLS X509TrustManager");
        this.trustManager = trustManager;
    }

    static TlsReloadableX509TrustManager create(X509TrustManager trustManager) {
        if (trustManager instanceof TlsReloadableX509TrustManager) {
            return (TlsReloadableX509TrustManager) trustManager;
        }
        assertValid(trustManager);
        return new TlsReloadableX509TrustManager(trustManager);
    }

    static void assertValid(X509TrustManager trustManager) {
        if (trustManager instanceof TlsReloadableX509TrustManager) {
            throw new IllegalArgumentException();
        }
    }

    static class NotReloadableTrustManager extends TlsReloadableX509TrustManager {
        NotReloadableTrustManager() {
            super(null);
        }

        @Override
        public void reload(Tls tls) {
            if (tls.trustManager().isPresent()) {
                throw new UnsupportedOperationException("Cannot set trust manager if one was not set during server start");
            }
        }

        @Override
        void reload(X509TrustManager trustManager) {
            throw new UnsupportedOperationException("Cannot set trust manager if one was not set during server start");
        }
    }
}
