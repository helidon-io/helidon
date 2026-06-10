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

import java.security.GeneralSecurityException;
import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class TlsManagerCompatibilityTest {
    @Test
    @SuppressWarnings("removal")
    void deprecatedReloadTlsDelegatesToMaterialReload() {
        MaterialReloadManager manager = new MaterialReloadManager();
        Tls tls = Tls.create(it -> it.trustAll(true));

        manager.reload(tls);

        assertThat(manager.reloadedMaterial, sameInstance(tls.prototype()));
    }

    @Test
    void materialReloadDelegatesToDeprecatedReload() {
        DeprecatedReloadManager manager = new DeprecatedReloadManager();
        TlsMaterial material = TlsMaterial.builder()
                .trustAll(true)
                .build();

        manager.reload(material);

        assertThat(manager.reloadedTls.prototype().trustAll(), is(true));
    }

    private abstract static class TestTlsManager implements TlsManager {
        @Override
        public void init(TlsConfig tls) {
        }

        @Override
        public SSLContext sslContext() {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, null, null);
                return sslContext;
            } catch (GeneralSecurityException e) {
                throw new AssertionError(e);
            }
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
            return "test";
        }

        @Override
        public String type() {
            return "test";
        }
    }

    private static final class MaterialReloadManager extends TestTlsManager {
        private TlsMaterial reloadedMaterial;

        @Override
        public void reload(TlsMaterial material) {
            this.reloadedMaterial = material;
        }
    }

    private static final class DeprecatedReloadManager extends TestTlsManager {
        private Tls reloadedTls;

        @Override
        @Deprecated(forRemoval = true, since = "27.0.0")
        @SuppressWarnings("removal")
        public void reload(Tls tls) {
            this.reloadedTls = tls;
        }
    }
}
