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

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.tls.spi.TlsManagerProvider;
import io.helidon.config.Config;

public class StaticReloadTlsManagerProvider implements TlsManagerProvider {
    static final String TYPE = "static-reload";
    private static final ConcurrentMap<String, StaticReloadTlsManager> MANAGERS = new ConcurrentHashMap<>();

    static void reset() {
        MANAGERS.clear();
    }

    static StaticReloadTlsManager manager(String name) {
        return Objects.requireNonNull(MANAGERS.get(name), "manager " + name);
    }

    static void reload(String name, String issuer) {
        manager(name).reload(issuer);
    }

    @Override
    public String configKey() {
        return TYPE;
    }

    @Override
    public TlsManager create(Config config, String name) {
        return MANAGERS.computeIfAbsent(name,
                                        it -> new StaticReloadTlsManager(name,
                                                                        config.get("initial-issuer")
                                                                                .asString()
                                                                                .orElse("initial")));
    }

    static final class StaticReloadTlsManager extends ConfiguredTlsManager {
        private final String initialIssuer;

        private StaticReloadTlsManager(String name, String initialIssuer) {
            super(name, TYPE);
            this.initialIssuer = initialIssuer;
        }

        @Override
        public void init(TlsConfig tls) {
            initSslContext(tls,
                           new SecureRandom(),
                           new KeyManager[0],
                           new TrustManager[] {new TrackingTrustManager(initialIssuer)});
        }

        private void reload(String issuer) {
            reload(Optional.empty(), Optional.of(new TrackingTrustManager(issuer)));
        }
    }

    static final class TrackingTrustManager implements X509TrustManager {
        private final String issuer;

        private TrackingTrustManager(String issuer) {
            this.issuer = issuer;
        }

        String issuer() {
            return issuer;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
