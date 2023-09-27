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

package io.helidon.webserver;

import java.io.Serial;
import java.security.KeyStore;
import java.security.Provider;
import java.security.cert.X509Certificate;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;

import static java.lang.System.Logger.Level.TRACE;

/**
 * A trust manager factory that trusts all peers.
 */
class TrustAllManagerFactory extends TrustManagerFactory {
    private static final TrustAllManagerFactorySpi SPI = new TrustAllManagerFactorySpi();
    private static final Provider PROVIDER = new Provider("helidon",
                                                          "0.0",
                                                          "Helidon internal security provider") {
        @Serial private static final long serialVersionUID = -147888L;
    };

    /**
     * Create a new instance.
     */
    TrustAllManagerFactory() {
        super(SPI, PROVIDER, "insecure-trust-all");
    }

    private static final class TrustAllManagerFactorySpi extends TrustManagerFactorySpi {
        private final TrustManager[] managers = new TrustManager[] {new TrustAllManager()};

        private TrustAllManagerFactorySpi() {
        }

        @Override
        protected void engineInit(KeyStore keyStore) {
        }

        @Override
        protected void engineInit(ManagerFactoryParameters managerFactoryParameters) {
        }

        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return managers;
        }
    }

    private static class TrustAllManager implements X509TrustManager {
        private static final System.Logger LOGGER = System.getLogger(TrustAllManager.class.getName());
        private static final X509Certificate[] ACCEPTED_ISSUERS = new X509Certificate[0];

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "Accepting a client certificate: " + chain[0].getSubjectX500Principal().getName()
                        + ", type: " + authType);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "Accepting a server certificate: " + chain[0].getSubjectX500Principal().getName()
                        + ", type: " + authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return ACCEPTED_ISSUERS;
        }
    }
}
