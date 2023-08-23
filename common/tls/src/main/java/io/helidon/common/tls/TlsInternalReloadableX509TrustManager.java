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

class TlsInternalReloadableX509TrustManager extends TlsReloadableX509TrustManager {
    private static final System.Logger LOGGER = System.getLogger(TlsInternalReloadableX509TrustManager.class.getName());

    private volatile X509TrustManager trustManager;

    TlsInternalReloadableX509TrustManager(X509TrustManager trustManager) {
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
        X509TrustManager trustManager = tls.trustManager();
        Objects.requireNonNull(trustManager, "Cannot unset trust store");
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Reloading TLS X509TrustManager");
        }
        trustManager(trustManager);
    }

    @Override
    public void trustManager(X509TrustManager trustManager) {
        this.trustManager = Objects.requireNonNull(trustManager);
    }

    static class NotReloadableTrustManager implements TlsReloadableComponent {
        @Override
        public void reload(Tls tls) {
            if (tls.trustManager() != null) {
                throw new UnsupportedOperationException("Cannot set trust manager if one was not set during server start");
            }
        }
    }
}
