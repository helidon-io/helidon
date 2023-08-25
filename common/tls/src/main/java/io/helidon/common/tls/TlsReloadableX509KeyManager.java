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

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Objects;

import javax.net.ssl.X509KeyManager;

class TlsReloadableX509KeyManager implements X509KeyManager, TlsReloadableComponent {
    private static final System.Logger LOGGER = System.getLogger(TlsReloadableX509KeyManager.class.getName());

    private volatile X509KeyManager keyManager;

    private TlsReloadableX509KeyManager(X509KeyManager keyManager) {
        this.keyManager = keyManager;
    }

    @Override
    public String[] getClientAliases(String s, Principal[] principals) {
        return keyManager.getClientAliases(s, principals);
    }

    @Override
    public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
        return keyManager.chooseClientAlias(strings, principals, socket);
    }

    @Override
    public String[] getServerAliases(String s, Principal[] principals) {
        return keyManager.getServerAliases(s, principals);
    }

    @Override
    public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
        return keyManager.chooseServerAlias(s, principals, socket);
    }

    @Override
    public X509Certificate[] getCertificateChain(String s) {
        return keyManager.getCertificateChain(s);
    }

    @Override
    public PrivateKey getPrivateKey(String s) {
        return keyManager.getPrivateKey(s);
    }

    @Override
    public void reload(Tls tls) {
        tls.keyManager().ifPresent(this::reload);
    }

    void reload(X509KeyManager keyManager) {
        Objects.requireNonNull(keyManager, "Cannot unset key manager");
        assertValid(keyManager);
        LOGGER.log(System.Logger.Level.DEBUG, "Reloading TLS X509KeyManager");
        this.keyManager = keyManager;
    }

    static TlsReloadableX509KeyManager create(X509KeyManager keyManager) {
        if (keyManager instanceof TlsReloadableX509KeyManager) {
            return (TlsReloadableX509KeyManager) keyManager;
        }
        assertValid(keyManager);
        return new TlsReloadableX509KeyManager(keyManager);
    }

    static void assertValid(X509KeyManager keyManager) {
        if (keyManager instanceof TlsReloadableX509KeyManager) {
            throw new IllegalArgumentException();
        }
    }

    static class NotReloadableKeyManager extends TlsReloadableX509KeyManager {
        NotReloadableKeyManager() {
            super(null);
        }

        @Override
        public void reload(Tls tls) {
            if (tls.keyManager().isPresent()) {
                throw new UnsupportedOperationException("Cannot reload key manager if one was not set during server start");
            }
        }

        @Override
        void reload(X509KeyManager keyManager) {
            throw new UnsupportedOperationException("Cannot reload key manager if one was not set during server start");
        }
    }
}
