/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.config.NamedService;
import io.helidon.service.registry.Service;

/**
 * Implementors of this contract are responsible for managing the {@link javax.net.ssl.SSLContext} instance lifecycle, as well
 * as the {@link TlsReloadableComponent} instances. When the context changes, it then has the responsible to notify all
 * registered {@link TlsReloadableComponent}s to accept the new {@link Tls} having the reloaded context.
 * <p>
 * How context changes are observed is based upon the implementation of the manager.
 */
@Service.Contract
public interface TlsManager extends NamedService {

    /**
     * Always called before any other method on this type. This method is only called when TLS is enabled.
     * In case the TLS is disabled, none of the methods on this type can be called.

     * @param tls TLS configuration
     */
    void init(TlsConfig tls);

    /**
     * This method will multiplex the call to all {@link TlsReloadableComponent}s that are being managed by this manager.
     *
     * @param tls the new tls instance
     * @see Tls#reload(Tls)
     */
    void reload(Tls tls);

    /**
     * SSL context created by this manager.
     * This method is called only after {@link #init(TlsConfig)} and only if {@link TlsConfig#enabled()} is {@code true}.
     *
     * @return the SSL context to use
     */
    SSLContext sslContext();

    /**
     * The key manager in use.
     *
     * @return key manager
     */
    Optional<X509KeyManager> keyManager();

    /**
     * The trust manager in use.
     *
     * @return trust manager
     */
    Optional<X509TrustManager> trustManager();

}
