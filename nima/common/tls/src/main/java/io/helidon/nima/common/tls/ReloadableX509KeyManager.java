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

package io.helidon.nima.common.tls;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Objects;

import javax.net.ssl.X509KeyManager;

class ReloadableX509KeyManager implements X509KeyManager, TlsReloadableComponent {

    private static final System.Logger LOGGER = System.getLogger(ReloadableX509KeyManager.class.getName());

    private volatile X509KeyManager keyManager;

    ReloadableX509KeyManager(X509KeyManager keyManager) {
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
        Objects.requireNonNull(tls.originalKeyManager(), "Cannot unset key manager");
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Reloading TLS X509KeyManager");
        }
        this.keyManager = tls.originalKeyManager();
    }

    static class NotReloadableKeyManager implements TlsReloadableComponent {
        @Override
        public void reload(Tls tls) {
            if (tls.originalKeyManager() != null) {
                throw new UnsupportedOperationException("Cannot reload key manager if one was not set during server start");
            }
        }
    }
}
