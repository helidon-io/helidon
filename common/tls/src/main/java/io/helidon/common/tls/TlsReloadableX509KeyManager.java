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

import javax.net.ssl.X509KeyManager;

/**
 * A {@link javax.net.ssl.KeyManager} that is both a {@link javax.net.ssl.X509KeyManager} as well as a
 * {@link TlsReloadableComponent}.
 */
public abstract class TlsReloadableX509KeyManager implements X509KeyManager, TlsReloadableComponent {

    /**
     * Default constructor.
     */
    protected TlsReloadableX509KeyManager() {
    }

    /**
     * Creates a new reloadable key manager.
     *
     * @param keyManager the underlying key manager (which can initially be null)
     * @return a reloadable key manager
     */
    public static TlsReloadableX509KeyManager create(X509KeyManager keyManager) {
        return new TlsInternalReloadableX509KeyManager(keyManager);
    }

    /**
     * Creates a new reloadable key manager.
     *
     * @return a reloadable key manager
     */
    public static TlsReloadableX509KeyManager create() {
        return new TlsInternalReloadableX509KeyManager(null);
    }

    /**
     * Establishes a new key manager delegate.
     *
     * @param keyManager the new key manager
     */
    public abstract void keyManager(X509KeyManager keyManager);

}
