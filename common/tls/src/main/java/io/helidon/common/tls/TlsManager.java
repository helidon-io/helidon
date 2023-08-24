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

import java.util.Optional;

import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.config.NamedService;
import io.helidon.inject.api.Contract;

/**
 * Implementors of this contract are responsible for managing the {@link javax.net.ssl.SSLContext} instance lifecycle, as well
 * as the {@link TlsReloadableComponent} instances. When the context changes, it then has the responsible to notify all
 * registered {@link TlsReloadableComponent}s to accept the new {@link Tls} having the reloaded context.
 * <p>
 * How context changes are observed is based upon the implementation of the manager.
 */
@Contract
public interface TlsManager extends NamedService {

    /**
     * Always called before any other method on this type. Typically, the implementation will use {@link Tls#prototype()} to then
     * determine whether {@link TlsConfig#enabled()} is {@code true}. If the configuration indicates that Tls is disabled then
     * typically the manager was no responsibilities to manage the context. Note that the passed Tls instance is still in early
     * lifecycle creation at this point.

     * @param tls the tls instance - this instance must expose a {@link Tls#prototype()}
     */
    void init(Tls tls);

    /**
     * This method will multiplex the call to all {@link TlsReloadableComponent}s that are being managed by this manager.
     *
     * @param tls the tls instance that is being asked to be reloaded
     * @see Tls#reload(Tls)
     */
    void reload(Tls tls);

    /**
     * Provides the ability to decorate the configuration for the {@link TlsConfig} as it is being built.
     *
     * @param target the builder
     */
    void decorate(TlsConfig.BuilderBase<?, ?> target);

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
