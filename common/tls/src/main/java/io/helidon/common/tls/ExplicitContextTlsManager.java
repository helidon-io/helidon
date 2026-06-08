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

import java.util.Objects;
import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

class ExplicitContextTlsManager implements TlsManager {
    private static final String TYPE = "explicit";
    private final SSLContext sslContext;

    ExplicitContextTlsManager(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    @Override
    public void init(TlsConfig tls) {
    }

    @Override
    public void reload(TlsMaterial material) {
        Objects.requireNonNull(material);
        throw new UnsupportedOperationException(
                "TLS cannot be reloaded when an explicit instance of SSL context was used to create it");
    }

    @Override
    public SSLContext sslContext() {
        return sslContext;
    }

    @Override
    public Optional<X509KeyManager> keyManager() {
        return Optional.empty();
    }

    @Override
    public Optional<X509TrustManager> trustManager() {
        return Optional.empty();
    }

    @Override
    public String name() {
        return TYPE;
    }

    @Override
    public String type() {
        return TYPE;
    }
}
