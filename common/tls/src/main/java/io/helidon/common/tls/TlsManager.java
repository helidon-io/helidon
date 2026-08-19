/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.Api;
import io.helidon.common.DeprecationSupport;
import io.helidon.config.NamedService;
import io.helidon.service.registry.Service;

/**
 * Implementors of this contract are responsible for initializing a single {@link javax.net.ssl.SSLContext} identity and
 * managing the {@link io.helidon.common.tls.TlsReloadableComponent} instances that provide its key and trust material.
 * Once the context is exposed through an {@link io.helidon.common.tls.Tls} instance, its identity must not change.
 * Post-initialization material changes are published through the reload methods.
 */
@Service.Contract
public interface TlsManager extends NamedService {

    /**
     * Initializes this manager before any other method is called. This method is only called when TLS is enabled.
     * In case TLS is disabled, none of the methods on this type can be called.
     * <p>
     * A manager can be shared, so this method may be called more than once. Later calls must not replace state already
     * exposed through an {@link io.helidon.common.tls.Tls} instance. Implementations may ignore later calls or reject
     * incompatible configuration.
     *
     * @param tls TLS configuration
     */
    void init(TlsConfig tls);

    /**
     * This method will multiplex the call to all {@link TlsReloadableComponent}s that are being managed by this manager.
     *
     * @param tls the new tls instance
     * @see Tls#reload(Tls)
     * @deprecated use {@link #reload(TlsMaterial)}, this method will be removed in the next major version of Helidon
     */
    @Deprecated(forRemoval = true, since = "27.0.0")
    default void reload(Tls tls) {
        Tls.validateReloadSource(tls);
        reload(tls.prototype());
    }

    /**
     * This method will multiplex the call to all {@link TlsReloadableComponent}s that are being managed by this manager.
     *
     * @param material the new TLS material
     * @see Tls#reload(TlsMaterial)
     */
    default void reload(TlsMaterial material) {
        // default to preserve backward compatibility - if this method is not implemented, the other one must be
        // require the deprecated variant to be implemented
        DeprecationSupport.requireOverride(this,
                                           TlsManager.class,
                                           "reload",
                                           Tls.class);

        // now call the original, deprecated method, as we know it exists, and we will not stack overflow
        reload(TlsMaterialSupport.toTls(material));
    }

    /**
     * Generation of TLS material changes reported by this manager.
     * <p>
     * The generation after the manager's first successful initialization is {@code 0}. An implementation that overrides
     * this method must advance the value whenever key or trust material changes, including when a reload fails after it may
     * have published a change. A concurrent read must not return while a material change is being published, and every
     * direct or provider-driven material publication path must participate. All post-initialization publication paths must
     * be serialized with the mechanism used by this method.
     * <p>
     * A caller can obtain a coherent snapshot by reading the generation, obtaining the manager state, and then reading the
     * generation again, accepting the snapshot only when both values are equal. An overriding implementation must ensure
     * equal values mean no material publication crossed that interval.
     * <p>
     * The compatibility default always returns {@code 0}. A manager that supports reload but does not override this method
     * does not report those reloads, so its generation may lag behind its current material.
     *
     * @return current TLS material generation
     */
    @Api.Internal
    default long generation() {
        return 0L;
    }

    /**
     * SSL context created by this manager.
     * This method is called only after
     * {@link io.helidon.common.tls.TlsManager#init(io.helidon.common.tls.TlsConfig)} and only if
     * {@link io.helidon.common.tls.TlsConfig#enabled()} is {@code true}.
     * The returned context identity must remain stable after the first successful initialization.
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
